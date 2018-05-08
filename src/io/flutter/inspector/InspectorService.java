/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.common.base.Joiner;
import com.google.gson.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceValue;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Manages all communication between inspector code running on the DartVM and
 * inspector code running in the IDE.
 */
public class InspectorService implements Disposable {
  private static int nextGroupId = 0;

  @NotNull private final FlutterApp app;
  @NotNull private final FlutterDebugProcess debugProcess;
  @NotNull private final VmService vmService;
  @NotNull private final Set<InspectorServiceClient> clients;
  @NotNull private final EvalOnDartLibrary inspectorLibrary;
  @NotNull private final Set<String> supportedServiceMethods;

  // TODO(jacobr): remove this field as soon as
  // `ext.flutter.inspector.*` has been in two revs of the Flutter Beta
  // channel. The feature landed in the Flutter dev chanel on
  // April 16, 2018.
  private final boolean isDaemonApiSupported;
  private final StreamSubscription<Boolean> setPubRootDirectoriesSubscription;

  public static CompletableFuture<InspectorService> create(@NotNull FlutterApp app,
                                                           @NotNull FlutterDebugProcess debugProcess,
                                                           @NotNull VmService vmService) {
    assert app.getPerfService() != null;
    final EvalOnDartLibrary inspectorLibrary = new EvalOnDartLibrary(
      "package:flutter/src/widgets/widget_inspector.dart",
      vmService,
      app.getPerfService()
    );
    final CompletableFuture<Library> libraryFuture =
      inspectorLibrary.libraryRef.thenComposeAsync((library) -> inspectorLibrary.getLibrary(library, null));
    return libraryFuture.thenComposeAsync((Library library) -> {
      for (ClassRef classRef : library.getClasses()) {
        if ("WidgetInspectorService".equals(classRef.getName())) {
          return inspectorLibrary.getClass(classRef, null).thenApplyAsync((ClassObj classObj) -> {
            final Set<String> functionNames = new HashSet<>();
            for (FuncRef funcRef : classObj.getFunctions()) {
              functionNames.add(funcRef.getName());
            }
            return functionNames;
          });
        }
      }
      throw new RuntimeException("WidgetInspectorService class not found");
    }).thenApplyAsync(
      (supportedServiceMethods) -> new InspectorService(
        app, debugProcess, vmService, inspectorLibrary, supportedServiceMethods));
  }

  private InspectorService(@NotNull FlutterApp app,
                           @NotNull FlutterDebugProcess debugProcess,
                           @NotNull VmService vmService,
                           EvalOnDartLibrary inspectorLibrary,
                           @NotNull Set<String> supportedServiceMethods) {
    this.vmService = vmService;
    this.app = app;
    this.debugProcess = debugProcess;
    this.inspectorLibrary = inspectorLibrary;
    this.supportedServiceMethods = supportedServiceMethods;

    // TODO(jacobr): remove this field as soon as
    // `ext.flutter.inspector.*` has been in two revs of the Flutter Beta
    // channel. The feature landed in the Flutter dev chanel on
    // April 16, 2018.
    this.isDaemonApiSupported = hasServiceMethod("initServiceExtensions");

    clients = new HashSet<>();

    vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        onVmServiceReceived(streamId, event);
      }

      @Override
      public void connectionClosed() {
        // TODO(jacobr): dispose?
      }
    });

    vmService.streamListen(VmService.EXTENSION_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);

    assert (app.getPerfService() != null);
    setPubRootDirectoriesSubscription =
      app.getPerfService().hasServiceExtension("ext.flutter.inspector.setPubRootDirectories", (Boolean available) -> {
        if (!available) {
          return;
        }
        final ArrayList<String> rootDirectories = new ArrayList<>();
        for (PubRoot root : app.getPubRoots()) {
          String path = root.getRoot().getCanonicalPath();
          if (SystemInfo.isWindows) {
            // TODO(jacobr): remove after https://github.com/flutter/flutter-intellij/issues/2217.
            // The problem is setPubRootDirectories is currently expecting
            // valid URIs as opposed to windows paths.
            path = "file:///" + path;
          }
          rootDirectories.add(path);
        }
        setPubRootDirectories(rootDirectories);
      });
  }

  public boolean isDetailsSummaryViewSupported() {
    return hasServiceMethod("getSelectedSummaryWidget");
  }

  /**
   * Use this method to write code that is backwards compatible with versions
   * of Flutter that are too old to contain specific service methods.
   */
  private boolean hasServiceMethod(String methodName) {
    return supportedServiceMethods.contains(methodName);
  }

  @NotNull
  public FlutterDebugProcess getDebugProcess() {
    return debugProcess;
  }

  public FlutterApp getApp() {
    return debugProcess.getApp();
  }

  public ObjectGroup createObjectGroup(String debugName) {
    return new ObjectGroup(debugName);
  }

  private EvalOnDartLibrary getInspectorLibrary() {
    return inspectorLibrary;
  }

  @Override
  public void dispose() {
    inspectorLibrary.dispose();
    setPubRootDirectoriesSubscription.dispose();
  }

  public CompletableFuture<?> forceRefresh() {
    final List<CompletableFuture<?>> futures = new ArrayList<>();

    for (InspectorServiceClient client : clients) {
      final CompletableFuture<?> future = client.onForceRefresh();
      futures.add(future);
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private void notifySelectionChanged() {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (InspectorServiceClient client : clients) {
        client.onInspectorSelectionChanged();
      }
    });
  }

  public void addClient(InspectorServiceClient client) {
    clients.add(client);
  }

  private void onVmServiceReceived(String streamId, Event event) {
    switch (streamId) {
      case VmService.DEBUG_STREAM_ID: {
        if (event.getKind() == EventKind.Inspect) {
          // Make sure the WidgetInspector on the device switches to show the inspected object
          // if the inspected object is a Widget or RenderObject.

          // We create a dummy object group as this particular operation
          // doesn't actually require an object group.
          createObjectGroup("dummy").setSelection(event.getInspectee(), true);
          // Update the UI in IntelliJ.
          notifySelectionChanged();
        }
        break;
      }
      case VmService.EXTENSION_STREAM_ID: {
        if ("Flutter.Frame".equals(event.getExtensionKind())) {
          ApplicationManager.getApplication().invokeLater(() -> {
            for (InspectorServiceClient client : clients) {
              client.onFlutterFrame();
            }
          });
        }
        break;
      }
      default:
    }
  }

  /**
   * If the widget tree is not ready, the application should wait for the next
   * Flutter.Frame event before attempting to display the widget tree. If the
   * application is ready, the next Flutter.Frame event may never come as no
   * new frames will be triggered to draw unless something changes in the UI.
   */
  public CompletableFuture<Boolean> isWidgetTreeReady() {
    if (isDaemonApiSupported) {
      return invokeServiceMethodDaemonNoGroup("isWidgetTreeReady", new HashMap<>())
        .thenApplyAsync((JsonElement element) -> element.getAsBoolean() == true);
    }
    else {
      return invokeServiceMethodObservatoryNoGroup("isWidgetTreeReady")
        .thenApplyAsync((InstanceRef ref) -> "true".equals(ref.getValueAsString()));
    }
  }

  CompletableFuture<JsonElement> invokeServiceMethodDaemonNoGroup(String methodName, List<String> args) {
    final Map<String, Object> params = new HashMap<>();
    for (int i = 0; i < args.size(); ++i) {
      params.put("arg" + i, args.get(i));
    }
    return invokeServiceMethodDaemonNoGroup(methodName, params);
  }

  private CompletableFuture<Void> setPubRootDirectories(List<String> rootDirectories) {
    // TODO(jacobr): remove call to hasServiceMethod("setPubRootDirectories") after
    // the `setPubRootDirectories` method has been in two revs of the Flutter Alpha
    // channel. The feature is expected to have landed in the Flutter dev
    // chanel on March 2, 2018.
    if (!hasServiceMethod("setPubRootDirectories")) {
      return CompletableFuture.completedFuture(null);
    }

    if (isDaemonApiSupported) {
      return invokeServiceMethodDaemonNoGroup("setPubRootDirectories", rootDirectories).thenApplyAsync((ignored) -> null);
    }
    else {
      // TODO(jacobr): remove this call as soon as
      // `ext.flutter.inspector.*` has been in two revs of the Flutter Beta
      // channel. The feature landed in the Flutter dev chanel on
      // April 16, 2018.
      final JsonArray jsonArray = new JsonArray();
      for (String rootDirectory : rootDirectories) {
        jsonArray.add(rootDirectory);
      }
      return getInspectorLibrary().eval(
        "WidgetInspectorService.instance.setPubRootDirectories(" + new Gson().toJson(jsonArray) + ")", null, null)
        .thenApplyAsync((instance) -> null);
    }
  }

  CompletableFuture<InstanceRef> invokeServiceMethodObservatoryNoGroup(String methodName) {
    return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "()", null, null);
  }

  CompletableFuture<JsonElement> invokeServiceMethodDaemonNoGroup(String methodName, Map<String, Object> params) {
    return getApp().callServiceExtension("ext.flutter.inspector." + methodName, params).thenApply((JsonObject json) -> {
      if (json.has("errorMessage")) {
        String message = json.get("errorMessage").getAsString();
        throw new RuntimeException(methodName + " -- " + message);
      }
      return json.get("result");
    });
  }

  /**
   * Class managing a group of inspector objects that can be freed by
   * a single call to dispose().
   * After dispose is called, all pending requests made with the ObjectGroup
   * will be skipped. This means that clients should not have to write any
   * special logic to handle orphaned requests.
   * <p>
   * safeWhenComplete is the recommended way to await futures returned by the
   * ObjectGroup as with that method the callback will be skipped if the
   * ObjectGroup is disposed making it easy to get the correct behavior of
   * skipping orphaned requests. Otherwise, code needs to handle getting back
   * futures that return null values for requests from disposed ObjectGroup
   * objects.
   */
  public class ObjectGroup implements Disposable {
    /**
     * Object group all objects in this arena are allocated with.
     */
    final String groupName;

    volatile boolean disposed;
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private ObjectGroup(String debugName) {
      this.groupName = debugName + "_" + nextGroupId;
      nextGroupId++;
    }

    /**
     * Once an ObjectGroup has been disposed, all methods returning
     * DiagnosticsNode objects will return a placeholder dummy node and all methods
     * returning lists or maps will return empty lists and all other methods will
     * return null. Generally code should never call methods on a disposed object
     * group but sometimes due to chained futures that can be difficult to avoid
     * and it is simpler return an empty result that will be ignored anyway than to
     * attempt carefully cancel futures.
     */
    @Override
    public void dispose() {
      lock.writeLock().lock();
      invokeVoidServiceMethod("disposeGroup", groupName);
      disposed = true;
      lock.writeLock().unlock();
    }

    private <T> CompletableFuture<T> nullIfDisposed(Supplier<CompletableFuture<T>> supplier) {
      lock.readLock().lock();
      if (disposed) {
        lock.readLock().unlock();
        return CompletableFuture.completedFuture(null);
      }

      try {
        return supplier.get();
      }
      finally {
        lock.readLock().unlock();
      }
    }

    private <T> T nullValueIfDisposed(Supplier<T> supplier) {
      lock.readLock().lock();
      if (disposed) {
        lock.readLock().unlock();
        return null;
      }

      try {
        return supplier.get();
      }
      finally {
        lock.readLock().unlock();
      }
    }

    private void skipIfDisposed(Runnable runnable) {
      lock.readLock().lock();
      if (disposed) {
        return;
      }

      try {
        runnable.run();
      }
      finally {
        lock.readLock().unlock();
      }
    }

    public CompletableFuture<XSourcePosition> getPropertyLocation(InstanceRef instanceRef, String name) {
      return nullIfDisposed(() -> getInstance(instanceRef)
        .thenComposeAsync((Instance instance) -> nullValueIfDisposed(() -> getPropertyLocationHelper(instance.getClassRef(), name))));
    }

    public CompletableFuture<XSourcePosition> getPropertyLocationHelper(ClassRef classRef, String name) {
      return nullIfDisposed(() -> inspectorLibrary.getClass(classRef, this).thenComposeAsync((ClassObj clazz) -> {
        return nullIfDisposed(() -> {
          for (FuncRef f : clazz.getFunctions()) {
            // TODO(pq): check for private properties that match name.
            if (f.getName().equals(name)) {
              return inspectorLibrary.getFunc(f, this).thenComposeAsync((Func func) -> nullIfDisposed(() -> {
                final SourceLocation location = func.getLocation();
                return inspectorLibrary.getSourcePosition(debugProcess, location.getScript(), location.getTokenPos(), this);
              }));
            }
          }
          final ClassRef superClass = clazz.getSuperClass();
          return superClass == null ? CompletableFuture.completedFuture(null) : getPropertyLocationHelper(superClass, name);
        });
      }));
    }

    public CompletableFuture<DiagnosticsNode> getRoot(FlutterTreeType type) {
      // There is no excuse to call this method on a disposed group.
      assert (!disposed);
      switch (type) {
        case widget:
          return getRootWidget();
        case renderObject:
          return getRootRenderObject();
      }
      throw new RuntimeException("Unexpected FlutterTreeType");
    }

    /**
     * Invokes a static method on the WidgetInspectorService class passing in the specified
     * arguments.
     * <p>
     * Intent is we could refactor how the API is invoked by only changing this call.
     */
    CompletableFuture<InstanceRef> invokeServiceMethodObservatory(String methodName) {
      return nullIfDisposed(() -> invokeServiceMethodObservatory(methodName, groupName));
    }

    CompletableFuture<InstanceRef> invokeServiceMethodObservatory(String methodName, String arg1) {
      return nullIfDisposed(
        () -> getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(\"" + arg1 + "\")", null, this));
    }

    CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName) {
      return invokeServiceMethodDaemon(methodName, groupName);
    }

    CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, String objectGroup) {
      final Map<String, Object> params = new HashMap<>();
      params.put("objectGroup", objectGroup);
      return invokeServiceMethodDaemon(methodName, params);
    }

    CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, String arg, String objectGroup) {
      final Map<String, Object> params = new HashMap<>();
      params.put("arg", arg);
      params.put("objectGroup", objectGroup);
      return invokeServiceMethodDaemon(methodName, params);
    }

    // All calls to invokeServiceMethodDaemon bottom out to this call.
    CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, Map<String, Object> params) {
      return getInspectorLibrary().addRequest(this, () -> getApp().callServiceExtension("ext.flutter.inspector." + methodName, params)
        .thenApply((JsonObject json) -> nullValueIfDisposed(() -> {
          if (json.has("errorMessage")) {
            String message = json.get("errorMessage").getAsString();
            throw new RuntimeException(methodName + " -- " + message);
          }
          return json.get("result");
        })));
    }

    CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, InspectorInstanceRef arg) {
      if (arg == null || arg.getId() == null) {
        return invokeServiceMethodDaemon(methodName, null, groupName);
      }
      return invokeServiceMethodDaemon(methodName, arg.getId(), groupName);
    }

    CompletableFuture<InstanceRef> invokeServiceMethodObservatory(String methodName, InspectorInstanceRef arg) {
      return nullIfDisposed(() -> {
        if (arg == null || arg.getId() == null) {
          return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(null, \"" + groupName + "\")", null, this);
        }
        return getInspectorLibrary()
          .eval("WidgetInspectorService.instance." + methodName + "(\"" + arg.getId() + "\", \"" + groupName + "\")", null, this);
      });
    }

    /**
     * Call a service method passing in an observatory instance reference.
     * <p>
     * This call is useful when receiving an "inspect" event from the
     * observatory and future use cases such as inspecting a Widget from the
     * IntelliJ watch window.
     * <p>
     * This method will always need to use the observatory service as the input
     * parameter is an Observatory InstanceRef..
     */
    CompletableFuture<InstanceRef> invokeServiceMethodOnRefObservatory(String methodName, InstanceRef arg) {
      return nullIfDisposed(() -> {
        final HashMap<String, String> scope = new HashMap<>();
        if (arg == null) {
          return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(null, \"" + groupName + "\")", scope, this);
        }
        scope.put("arg1", arg.getId());
        return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(arg1, \"" + groupName + "\")", scope, this);
      });
    }

    CompletableFuture<DiagnosticsNode> parseDiagnosticsNodeObservatory(CompletableFuture<InstanceRef> instanceRefFuture) {
      return nullIfDisposed(() -> instanceRefFuture.thenComposeAsync(this::parseDiagnosticsNodeObservatory));
    }

    /**
     * Returns a CompletableFuture with a Map of property names to Observatory
     * InstanceRef objects. This method is shorthand for individually evaluating
     * each of the getters specified by property names.
     * <p>
     * It would be nice if the Observatory protocol provided a built in method
     * to get InstanceRef objects for a list of properties but this is
     * sufficient although slightly less efficient. The Observatory protocol
     * does provide fast access to all fields as part of an Instance object
     * but that is inadequate as for many Flutter data objects that we want
     * to display visually we care about properties that are not necessarily
     * fields.
     * <p>
     * The future will immediately complete to null if the inspectorInstanceRef is null.
     */
    public CompletableFuture<Map<String, InstanceRef>> getDartObjectProperties(
      InspectorInstanceRef inspectorInstanceRef, final String[] propertyNames) {
      return nullIfDisposed(
        () -> toObservatoryInstanceRef(inspectorInstanceRef).thenComposeAsync((InstanceRef instanceRef) -> nullIfDisposed(() -> {
          final StringBuilder sb = new StringBuilder();
          final List<String> propertyAccessors = new ArrayList<>();
          final String objectName = "that";
          for (String propertyName : propertyNames) {
            propertyAccessors.add(objectName + "." + propertyName);
          }
          sb.append("[");
          sb.append(Joiner.on(',').join(propertyAccessors));
          sb.append("]");
          final Map<String, String> scope = new HashMap<>();
          scope.put(objectName, instanceRef.getId());
          return getInstance(inspectorLibrary.eval(sb.toString(), scope, this)).thenApplyAsync(
            (Instance instance) -> nullValueIfDisposed(() -> {
              // We now have an instance object that is a Dart array of all the
              // property values. Convert it back to a map from property name to
              // property values.

              final Map<String, InstanceRef> properties = new HashMap<>();
              final ElementList<InstanceRef> values = instance.getElements();
              assert (values.size() == propertyNames.length);
              for (int i = 0; i < propertyNames.length; ++i) {
                properties.put(propertyNames[i], values.get(i));
              }
              return properties;
            }));
        })));
    }

    public CompletableFuture<InstanceRef> toObservatoryInstanceRef(InspectorInstanceRef inspectorInstanceRef) {
      return nullIfDisposed(() -> invokeServiceMethodObservatory("toObject", inspectorInstanceRef));
    }

    private CompletableFuture<Instance> getInstance(InstanceRef instanceRef) {
      return nullIfDisposed(() -> getInspectorLibrary().getInstance(instanceRef, this));
    }

    CompletableFuture<Instance> getInstance(CompletableFuture<InstanceRef> instanceRefFuture) {
      return nullIfDisposed(() -> instanceRefFuture.thenComposeAsync(this::getInstance));
    }

    CompletableFuture<DiagnosticsNode> parseDiagnosticsNodeObservatory(InstanceRef instanceRef) {
      return nullIfDisposed(() -> instanceRefToJson(instanceRef).thenApplyAsync(this::parseDiagnosticsNodeHelper));
    }

    CompletableFuture<DiagnosticsNode> parseDiagnosticsNodeDaemon(CompletableFuture<JsonElement> json) {
      return nullIfDisposed(() -> json.thenApplyAsync(this::parseDiagnosticsNodeHelper));
    }

    DiagnosticsNode parseDiagnosticsNodeHelper(JsonElement jsonElement) {
      return nullValueIfDisposed(() -> {
        if (jsonElement == null || jsonElement.isJsonNull()) {
          return null;
        }
        return new DiagnosticsNode(jsonElement.getAsJsonObject(), this, false);
      });
    }

    /**
     * Requires that the InstanceRef is really referring to a String that is valid JSON.
     */
    CompletableFuture<JsonElement> instanceRefToJson(InstanceRef instanceRef) {

      return nullIfDisposed(() -> getInspectorLibrary().getInstance(instanceRef, this).thenApplyAsync((Instance instance) -> {
        return nullValueIfDisposed(() -> {
          final String json = instance.getValueAsString();
          return new JsonParser().parse(json);
        });
      }));
    }

    CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodesObservatory(InstanceRef instanceRef) {
      return nullIfDisposed(() -> instanceRefToJson(instanceRef).thenApplyAsync((JsonElement jsonElement) -> {
        return nullValueIfDisposed(() -> {
          final JsonArray jsonArray = jsonElement != null ? jsonElement.getAsJsonArray() : null;
          return parseDiagnosticsNodesHelper(jsonArray);
        });
      }));
    }

    ArrayList<DiagnosticsNode> parseDiagnosticsNodesHelper(JsonElement jsonObject) {
      return parseDiagnosticsNodesHelper(jsonObject != null ? jsonObject.getAsJsonArray() : null);
    }

    ArrayList<DiagnosticsNode> parseDiagnosticsNodesHelper(JsonArray jsonArray) {
      return nullValueIfDisposed(() -> {
        if (jsonArray == null) {
          return null;
        }
        final ArrayList<DiagnosticsNode> nodes = new ArrayList<>();
        for (JsonElement element : jsonArray) {
          nodes.add(new DiagnosticsNode(element.getAsJsonObject(), this, false));
        }
        return nodes;
      });
    }

    /**
     * Converts an inspector ref to value suitable for use by generic intellij
     * debugging tools.
     * <p>
     * Warning: DartVmServiceValue references do not make any lifetime guarantees
     * so code keeping them around for a long period of time must be prepared to
     * handle reference expiration gracefully.
     */
    public CompletableFuture<DartVmServiceValue> toDartVmServiceValueForSourceLocation(InspectorInstanceRef inspectorInstanceRef) {
      return invokeServiceMethodObservatory("toObjectForSourceLocation", inspectorInstanceRef).thenApplyAsync(
        (InstanceRef instanceRef) -> nullValueIfDisposed(() -> {
          //noinspection CodeBlock2Expr
          return new DartVmServiceValue(debugProcess, inspectorLibrary.getIsolateId(), "inspectedObject", instanceRef, null, null, false);
        }));
    }

    CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodesObservatory(CompletableFuture<InstanceRef> instanceRefFuture) {
      return nullIfDisposed(() -> instanceRefFuture.thenComposeAsync(this::parseDiagnosticsNodesObservatory));
    }

    CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodesDaemon(CompletableFuture<JsonElement> jsonFuture) {
      return nullIfDisposed(() -> jsonFuture.thenApplyAsync(this::parseDiagnosticsNodesHelper));
    }

    CompletableFuture<ArrayList<DiagnosticsNode>> getChildren(InspectorInstanceRef instanceRef, boolean summaryTree) {
      if (isDetailsSummaryViewSupported()) {
        return getListHelper(instanceRef, summaryTree ? "getChildrenSummaryTree" : "getChildrenDetailsSubtree");
      }
      else {
        return getListHelper(instanceRef, "getChildren");
      }
    }

    CompletableFuture<ArrayList<DiagnosticsNode>> getProperties(InspectorInstanceRef instanceRef) {
      return getListHelper(instanceRef, "getProperties");
    }

    private CompletableFuture<ArrayList<DiagnosticsNode>> getListHelper(
      InspectorInstanceRef instanceRef, String methodName) {
      return nullIfDisposed(() -> {
        if (isDaemonApiSupported) {
          return parseDiagnosticsNodesDaemon(invokeServiceMethodDaemon(methodName, instanceRef));
        }
        else {
          return parseDiagnosticsNodesObservatory(invokeServiceMethodObservatory(methodName, instanceRef));
        }
      });
    }

    public CompletableFuture<DiagnosticsNode> invokeServiceMethodReturningNode(String methodName) {
      return nullIfDisposed(() -> {
        if (isDaemonApiSupported) {
          return parseDiagnosticsNodeDaemon(invokeServiceMethodDaemon(methodName));
        }
        else {
          return parseDiagnosticsNodeObservatory(invokeServiceMethodObservatory(methodName));
        }
      });
    }

    public CompletableFuture<DiagnosticsNode> invokeServiceMethodReturningNode(String methodName, InspectorInstanceRef ref) {
      return nullIfDisposed(() -> {
        if (isDaemonApiSupported) {
          return parseDiagnosticsNodeDaemon(invokeServiceMethodDaemon(methodName, ref));
        }
        else {
          return parseDiagnosticsNodeObservatory(invokeServiceMethodObservatory(methodName, ref));
        }
      });
    }

    public CompletableFuture<Void> invokeVoidServiceMethod(String methodName, String arg1) {
      return nullIfDisposed(() -> {
        if (isDaemonApiSupported) {
          return invokeServiceMethodDaemon(methodName, arg1).thenApply((ignored) -> null);
        }
        else {
          return invokeServiceMethodObservatory(methodName, arg1).thenApply((ignored) -> null);
        }
      });
    }

    public CompletableFuture<Void> invokeVoidServiceMethod(String methodName, InspectorInstanceRef ref) {
      return nullIfDisposed(() -> {
        if (isDaemonApiSupported) {
          return invokeServiceMethodDaemon(methodName, ref).thenApply((ignored) -> null);
        }
        else {
          return invokeServiceMethodObservatory(methodName, ref).thenApply((ignored) -> null);
        }
      });
    }

    public CompletableFuture<DiagnosticsNode> getRootWidget() {
      return invokeServiceMethodReturningNode(isDetailsSummaryViewSupported() ? "getRootWidgetSummaryTree" : "getRootWidget");
    }

    public CompletableFuture<DiagnosticsNode> getRootRenderObject() {
      assert (!disposed);
      return invokeServiceMethodReturningNode("getRootRenderObject");
    }

    public CompletableFuture<ArrayList<DiagnosticsPathNode>> getParentChain(DiagnosticsNode target) {
      return nullIfDisposed(() -> {
        if (isDaemonApiSupported) {
          return parseDiagnosticsPathDaemon(invokeServiceMethodDaemon("getParentChain", target.getValueRef()));
        }
        else {
          return parseDiagnosticsPathObservatory(invokeServiceMethodObservatory("getParentChain", target.getValueRef()));
        }
      });
    }

    CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPathObservatory(CompletableFuture<InstanceRef> instanceRefFuture) {
      return nullIfDisposed(() -> instanceRefFuture.thenComposeAsync(this::parseDiagnosticsPathObservatory));
    }

    private CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPathObservatory(InstanceRef pathRef) {
      return nullIfDisposed(() -> instanceRefToJson(pathRef).thenApplyAsync(this::parseDiagnosticsPathHelper));
    }

    CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPathDaemon(CompletableFuture<JsonElement> jsonFuture) {
      return nullIfDisposed(() -> jsonFuture.thenApplyAsync(this::parseDiagnosticsPathHelper));
    }

    private ArrayList<DiagnosticsPathNode> parseDiagnosticsPathHelper(JsonElement jsonElement) {
      return nullValueIfDisposed(() -> {
        final JsonArray jsonArray = jsonElement.getAsJsonArray();
        final ArrayList<DiagnosticsPathNode> pathNodes = new ArrayList<>();
        for (JsonElement element : jsonArray) {
          pathNodes.add(new DiagnosticsPathNode(element.getAsJsonObject(), this));
        }
        return pathNodes;
      });
    }

    public CompletableFuture<DiagnosticsNode> getSelection(DiagnosticsNode previousSelection, FlutterTreeType treeType, boolean localOnly) {
      // There is no reason to allow calling this method on a disposed group.
      assert (!disposed);
      return nullIfDisposed(() -> {
        CompletableFuture<DiagnosticsNode> result = null;
        final InspectorInstanceRef previousSelectionRef = previousSelection != null ? previousSelection.getDartDiagnosticRef() : null;

        switch (treeType) {
          case widget:
            result = invokeServiceMethodReturningNode(localOnly ? "getSelectedSummaryWidget" : "getSelectedWidget", previousSelectionRef);
            break;
          case renderObject:
            result = invokeServiceMethodReturningNode("getSelectedRenderObject", previousSelectionRef);
            break;
        }
        return result.thenApplyAsync((DiagnosticsNode newSelection) -> nullValueIfDisposed(() -> {
          if (newSelection != null && newSelection.getDartDiagnosticRef().equals(previousSelectionRef)) {
            return previousSelection;
          }
          else {
            return newSelection;
          }
        }));
      });
    }

    public void setSelection(InspectorInstanceRef selection, boolean uiAlreadyUpdated) {
      if (disposed) {
        return;
      }
      if (isDaemonApiSupported) {
        handleSetSelectionDaemon(invokeServiceMethodDaemon("setSelectionById", selection), uiAlreadyUpdated);
      }
      else {
        handleSetSelectionObservatory(invokeServiceMethodObservatory("setSelectionById", selection), uiAlreadyUpdated);
      }
    }

    /**
     * Helper when we need to set selection given an observatory InstanceRef
     * instead of an InspectorInstanceRef.
     */
    public void setSelection(InstanceRef selection, boolean uiAlreadyUpdated) {
      // There is no excuse for calling setSelection using a disposed ObjectGroup.
      assert (!disposed);
      // This call requires the observatory protocol as an observatory InstanceRef is specified.
      handleSetSelectionObservatory(invokeServiceMethodOnRefObservatory("setSelection", selection), uiAlreadyUpdated);
    }

    private void handleSetSelectionObservatory(CompletableFuture<InstanceRef> setSelectionResult, boolean uiAlreadyUpdated) {
      // TODO(jacobr): we need to cancel if another inspect request comes in while we are trying this one.
      skipIfDisposed(() -> setSelectionResult.thenAcceptAsync((InstanceRef instanceRef) -> skipIfDisposed(() -> {
        handleSetSelectionHelper("true".equals(instanceRef.getValueAsString()), uiAlreadyUpdated);
      })));
    }

    private void handleSetSelectionHelper(boolean selectionChanged, boolean uiAlreadyUpdated) {
      if (selectionChanged && !uiAlreadyUpdated) {
        notifySelectionChanged();
      }
    }

    private void handleSetSelectionDaemon(CompletableFuture<JsonElement> setSelectionResult, boolean uiAlreadyUpdated) {
      skipIfDisposed(() ->
                       // TODO(jacobr): we need to cancel if another inspect request comes in while we are trying this one.
                       setSelectionResult.thenAcceptAsync(
                         (JsonElement json) -> skipIfDisposed(() -> handleSetSelectionHelper(json.getAsBoolean(), uiAlreadyUpdated)))
      );
    }

    public CompletableFuture<Map<String, InstanceRef>> getEnumPropertyValues(InspectorInstanceRef ref) {
      return nullIfDisposed(() -> {
        if (ref == null || ref.getId() == null) {
          return CompletableFuture.completedFuture(null);
        }
        return getInstance(toObservatoryInstanceRef(ref))
          .thenComposeAsync(
            (Instance instance) -> nullIfDisposed(() -> getInspectorLibrary().getClass(instance.getClassRef(), this).thenApplyAsync(
              (ClassObj clazz) -> nullValueIfDisposed(() -> {
                final Map<String, InstanceRef> properties = new LinkedHashMap<>();
                for (FieldRef field : clazz.getFields()) {
                  final String name = field.getName();
                  if (name.startsWith("_")) {
                    // Needed to filter out _deleted_enum_sentinel synthetic property.
                    // If showing private enum values is useful we could special case
                    // just the _deleted_enum_sentinel property name.
                    continue;
                  }
                  if (name.equals("values")) {
                    // Need to filter out the synthetic "values" member.
                    // TODO(jacobr): detect that this properties return type is
                    // different and filter that way.
                    continue;
                  }
                  if (field.isConst() && field.isStatic()) {
                    properties.put(field.getName(), field.getDeclaredType());
                  }
                }
                return properties;
              })
            )));
      });
    }

    public CompletableFuture<DiagnosticsNode> getDetailsSubtree(DiagnosticsNode node) {
      if (node == null) {
        return CompletableFuture.completedFuture(null);
      }
      return nullIfDisposed(() -> invokeServiceMethodReturningNode("getDetailsSubtree", node.getDartDiagnosticRef()));
    }

    FlutterApp getApp() {
      return InspectorService.this.getApp();
    }

    /**
     * Await a Future invoking the callback on completion on the UI thread only if the
     * rhis ObjectGroup is still alive when the Future completes.
     */
    public <T> void safeWhenComplete(CompletableFuture<T> future, BiConsumer<? super T, ? super Throwable> action) {
      if (future == null) {
        return;
      }
      future.whenCompleteAsync(
        (T value, Throwable throwable) -> skipIfDisposed(() -> {
          ApplicationManager.getApplication().invokeLater(() -> {
            action.accept(value, throwable);
          });
        })
      );
    }

    public boolean isDisposed() {
      return disposed;
    }
  }

  public enum FlutterTreeType {
    widget("Widget"),
    renderObject("Render");
    // TODO(jacobr): add semantics, and layer trees.

    public final String displayName;

    FlutterTreeType(String displayName) {
      this.displayName = displayName;
    }
  }

  public interface InspectorServiceClient {
    void onInspectorSelectionChanged();

    void onFlutterFrame();

    CompletableFuture<?> onForceRefresh();
  }
}
