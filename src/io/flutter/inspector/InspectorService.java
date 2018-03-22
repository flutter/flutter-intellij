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
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceValue;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Manages all communication between inspector code running on the DartVM and
 * inspector code running in the IDE.
 */
public class InspectorService implements Disposable {
  private static int nextGroupId = 0;

  /**
   * Group name to to manage keeping alive nodes in the tree referenced by the inspector.
   */
  private final String groupName;
  @NotNull private final FlutterDebugProcess debugProcess;
  @NotNull private final VmService vmService;
  @NotNull private final Set<InspectorServiceClient> clients;
  private EvalOnDartLibrary inspectorLibrary;
  @NotNull private final Set<String> supportedServiceMethods;

  // TODO(jacobr): remove this field as soon as
  // `ext.flutter.debugCallWidgetInspectorService` has been in two revs of the
  // Flutter Beta channel. The feature is expected to have landed in the
  // Flutter dev chanel on March 22, 2018.
  private final boolean isDaemonApiSupported;

  public static CompletableFuture<InspectorService> create(@NotNull FlutterDebugProcess debugProcess, @NotNull VmService vmService) {
    final EvalOnDartLibrary inspectorLibrary = new EvalOnDartLibrary(
      "package:flutter/src/widgets/widget_inspector.dart",
      debugProcess,
      vmService
    );
    final CompletableFuture<Library> libraryFuture = inspectorLibrary.libraryRef.thenComposeAsync(inspectorLibrary::getLibrary);
    return libraryFuture.thenComposeAsync((Library library) -> {
      for (ClassRef classRef : library.getClasses()) {
        if ("WidgetInspectorService".equals(classRef.getName())) {
          return inspectorLibrary.getClass(classRef).thenApplyAsync((ClassObj classObj) -> {
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
      (supportedServiceMethods) -> new InspectorService(debugProcess, vmService, inspectorLibrary, supportedServiceMethods));
  }

  private InspectorService(@NotNull FlutterDebugProcess debugProcess,
                           @NotNull VmService vmService,
                           EvalOnDartLibrary inspectorLibrary,
                           Set<String> supportedServiceMethods) {
    this.vmService = vmService;
    this.debugProcess = debugProcess;
    this.inspectorLibrary = inspectorLibrary;
    this.supportedServiceMethods = supportedServiceMethods;

    // TODO(jacobr): remove this field as soon as
    // `ext.flutter.debugCallWidgetInspectorService` has been in two revs of the
    // Flutter Beta channel. The feature is expected to have landed in the
    // Flutter dev chanel on March 22, 2018.
    this.isDaemonApiSupported = hasServiceMethod("initServiceExtensions");

    clients = new HashSet<>();
    groupName = "intellij_inspector_" + nextGroupId;
    nextGroupId++;

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

    vmService.streamListen("Extension", VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
  }

  public FlutterDebugProcess getDebugProcess() {
    return debugProcess;
  }

  public FlutterApp getApp() {
    return debugProcess.getApp();
  }

  public CompletableFuture<XSourcePosition> getPropertyLocation(InstanceRef instanceRef, String name) {
    return getInstance(instanceRef).thenComposeAsync((Instance instance) -> getPropertyLocationHelper(instance.getClassRef(), name));
  }

  public CompletableFuture<XSourcePosition> getPropertyLocationHelper(ClassRef classRef, String name) {
    return inspectorLibrary.getClass(classRef).thenComposeAsync((ClassObj clazz) -> {
      for (FuncRef f : clazz.getFunctions()) {
        // TODO(pq): check for private properties that match name.
        if (f.getName().equals(name)) {
          return inspectorLibrary.getFunc(f).thenComposeAsync((Func func) -> {
            final SourceLocation location = func.getLocation();
            return inspectorLibrary.getSourcePosition(debugProcess, location.getScript(), location.getTokenPos());
          });
        }
      }
      final ClassRef superClass = clazz.getSuperClass();
      return superClass == null ? CompletableFuture.completedFuture(null) : getPropertyLocationHelper(superClass, name);
    });
  }

  public CompletableFuture<DiagnosticsNode> getRoot(FlutterTreeType type) {
    switch (type) {
      case widget:
        return getRootWidget();
      case renderObject:
        return getRootRenderObject();
    }
    throw new RuntimeException("Unexpected FlutterTreeType");
  }

  private EvalOnDartLibrary getInspectorLibrary() {
    if (inspectorLibrary == null) {
      inspectorLibrary = new EvalOnDartLibrary(
        "package:flutter/src/widgets/widget_inspector.dart",
        debugProcess,
        vmService
      );
    }
    return inspectorLibrary;
  }

  public void addClient(InspectorServiceClient client) {
    clients.add(client);
  }

  /**
   * Invokes a static method on the WidgetInspectorService class passing in the specified
   * arguments.
   * <p>
   * Intent is we could refactor how the API is invoked by only changing this call.
   */
  // TODO(jacobr): remove this method as soon as
  // `ext.flutter.debugCallWidgetInspectorService` has been in two revs of the
  // Flutter Beta channel. The feature is expected to have landed in the
  // Flutter dev chanel on March 22, 2018.
  CompletableFuture<InstanceRef> invokeServiceMethodObservatory(String methodName) {
    return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(\"" + groupName + "\")", null);
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

  CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, List<String> args) {
    final Map<String, Object> params = new HashMap<>();
    for (int i = 0; i < args.size(); ++i) {
      params.put("arg" + i, args.get(i));
    }
    return invokeServiceMethodDaemon(methodName, params);
  }

  CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, Map<String, Object> params) {
    return getApp().callServiceExtension("ext.flutter.inspector." + methodName, params).thenApply((JsonObject json) -> {
      if (json.has("errorMessage")) {
        String message = json.get("errorMessage").getAsString();
        throw new RuntimeException(methodName + " -- " + message);
      }
      return json.get("result");
    });
  }

  CompletableFuture<JsonElement> invokeServiceMethodDaemon(String methodName, InspectorInstanceRef arg) {
    if (arg == null || arg.getId() == null) {
      return invokeServiceMethodDaemon(methodName, null, groupName);
    }
    return invokeServiceMethodDaemon(methodName, arg.getId(), groupName);
  }

  // TODO(jacobr): remove this method as soon as
  // `ext.flutter.debugCallWidgetInspectorService` has been in two revs of the
  // Flutter Beta channel. The feature is expected to have landed in the
  // Flutter dev chanel on March 22, 2018.
  CompletableFuture<InstanceRef> invokeServiceMethodObservatory(String methodName, InspectorInstanceRef arg) {
    if (arg == null || arg.getId() == null) {
      return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(null, \"" + groupName + "\")", null);
    }
    return getInspectorLibrary()
      .eval("WidgetInspectorService.instance." + methodName + "(\"" + arg.getId() + "\", \"" + groupName + "\")", null);
  }

  /**
   * Call a service method passing in an observatory instance reference.
   *
   * This call is useful when receiving an "inspect" event from the
   * observatory and future use cases such as inspecting a Widget from the
   * IntelliJ watch window.
   *
   * This method will always need to use the observatory service as the input
   * parameter is an Observatory InstanceRef..
   */
  CompletableFuture<InstanceRef> invokeServiceMethodOnRefObservatory(String methodName, InstanceRef arg) {
    final HashMap<String, String> scope = new HashMap<>();
    if (arg == null) {
      return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(null, \"" + groupName + "\")", scope);
    }
    scope.put("arg1", arg.getId());
    return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(arg1, \"" + groupName + "\")", scope);
  }

  public CompletableFuture<Void> setPubRootDirectories(List<String> rootDirectories) {
    // TODO(jacobr): remove call to hasServiceMethod("setPubRootDirectories") after
    // the `setPubRootDirectories` method has been in two revs of the Flutter Alpha
    // channel. The feature is expected to have landed in the Flutter dev
    // chanel on March 2, 2018.
    if (!hasServiceMethod("setPubRootDirectories")) {
      return CompletableFuture.completedFuture(null);
    }

    if (isDaemonApiSupported) {
      return invokeServiceMethodDaemon("setPubRootDirectories", rootDirectories).thenApplyAsync((ignored) -> null);
    }
    else {
      // TODO(jacobr): remove this call as soon as
      // `ext.flutter.debugCallWidgetInspectorService` has been in two revs of the
      // Flutter Beta channel. The feature is expected to have landed in the
      // Flutter dev chanel on March 22, 2018.
      final JsonArray jsonArray = new JsonArray();
      for (String rootDirectory : rootDirectories) {
        jsonArray.add(rootDirectory);
      }
      return getInspectorLibrary().eval(
        "WidgetInspectorService.instance.setPubRootDirectories(" + new Gson().toJson(jsonArray) + ")", null)
        .thenApplyAsync((instance) -> null);
    }
  }

  // TODO(jacobr): remove this method as soon as
  // `ext.flutter.debugCallWidgetInspectorService` has been in two revs of the
  // Flutter Beta channel. The feature is expected to have landed in the
  // Flutter dev chanel on March 22, 2018.
  CompletableFuture<DiagnosticsNode> parseDiagnosticsNodeObservatory(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::parseDiagnosticsNodeObservatory);
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
    return toObservatoryInstanceRef(inspectorInstanceRef).thenComposeAsync((InstanceRef instanceRef) -> {
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
      return getInstance(inspectorLibrary.eval(sb.toString(), scope)).thenApplyAsync(
        (Instance instance) -> {
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
        });
    });
  }

  public CompletableFuture<InstanceRef> toObservatoryInstanceRef(InspectorInstanceRef inspectorInstanceRef) {
    return invokeServiceMethodObservatory("toObject", inspectorInstanceRef);
  }

  private CompletableFuture<Instance> getInstance(InstanceRef instanceRef) {
    return getInspectorLibrary().getInstance(instanceRef);
  }

  CompletableFuture<Instance> getInstance(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::getInstance);
  }

  CompletableFuture<DiagnosticsNode> parseDiagnosticsNodeObservatory(InstanceRef instanceRef) {
    return instanceRefToJson(instanceRef).thenApplyAsync(this::parseDiagnosticsNodeHelper);
  }

  CompletableFuture<DiagnosticsNode> parseDiagnosticsNodeDaemon(CompletableFuture<JsonElement> json) {
    return json.thenApplyAsync(this::parseDiagnosticsNodeHelper);
  }

  DiagnosticsNode parseDiagnosticsNodeHelper(JsonElement jsonElement) {
    return new DiagnosticsNode(jsonElement.getAsJsonObject(), this);
  }

  /**
   * Requires that the InstanceRef is really referring to a String that is valid JSON.
   */
  CompletableFuture<JsonElement> instanceRefToJson(InstanceRef instanceRef) {
    return getInspectorLibrary().getInstance(instanceRef).thenApplyAsync((Instance instance) -> {
      final String json = instance.getValueAsString();
      return new JsonParser().parse(json);
    });
  }

  CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodesObservatory(InstanceRef instanceRef) {
    return instanceRefToJson(instanceRef).thenApplyAsync((JsonElement jsonElement) -> {
      final JsonArray jsonArray = jsonElement.getAsJsonArray();
      return parseDiagnosticsNodesHelper(jsonArray);
    });
  }

  ArrayList<DiagnosticsNode> parseDiagnosticsNodesHelper(JsonArray jsonArray) {
    final ArrayList<DiagnosticsNode> nodes = new ArrayList<>();
    for (JsonElement element : jsonArray) {
      nodes.add(new DiagnosticsNode(element.getAsJsonObject(), this));
    }
    return nodes;
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
      (InstanceRef instanceRef) -> {
        //noinspection CodeBlock2Expr
        return new DartVmServiceValue(debugProcess, inspectorLibrary.getIsolateId(), "inspectedObject", instanceRef, null, null, false);
      });
  }

  CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodesObservatory(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::parseDiagnosticsNodesObservatory);
  }

  CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodesDaemon(CompletableFuture<JsonElement> jsonFuture) {
    return jsonFuture.thenApplyAsync((json) -> parseDiagnosticsNodesHelper(json.getAsJsonArray()));
  }

  CompletableFuture<ArrayList<DiagnosticsNode>> getChildren(InspectorInstanceRef instanceRef) {
    return getListHelper(instanceRef, "getChildren");
  }

  CompletableFuture<ArrayList<DiagnosticsNode>> getProperties(InspectorInstanceRef instanceRef) {
    return getListHelper(instanceRef, "getProperties");
  }

  /**
   * If the widget tree is not ready, the application should wait for the next
   * Flutter.Frame event before attempting to display the widget tree. If the
   * application is ready, the next Flutter.Frame event may never come as no
   * new frames will be triggered to draw unless something changes in the UI.
   */
  public CompletableFuture<Boolean> isWidgetTreeReady() {
    if (isDaemonApiSupported) {
      return invokeServiceMethodDaemon("isWidgetTreeReady").thenApplyAsync((JsonElement element) -> element.getAsBoolean() == true);
    }
    else {
      return invokeServiceMethodObservatory("isWidgetTreeReady").thenApplyAsync((InstanceRef ref) -> "true".equals(ref.getValueAsString()));
    }
  }

  /**
   * Use this method to write code that is backwards compatible with versions
   * of Flutter that are too old to contain specific service methods.
   */
  private boolean hasServiceMethod(String methodName) {
    return supportedServiceMethods.contains(methodName);
  }

  private CompletableFuture<ArrayList<DiagnosticsNode>> getListHelper(
    InspectorInstanceRef instanceRef, String methodName) {
    if (isDaemonApiSupported) {
      return parseDiagnosticsNodesDaemon(invokeServiceMethodDaemon(methodName, instanceRef));
    }
    else {
      return parseDiagnosticsNodesObservatory(invokeServiceMethodObservatory(methodName, instanceRef));
    }
  }

  public CompletableFuture<DiagnosticsNode> invokeServiceMethodReturningNode(String methodName) {
    if (isDaemonApiSupported) {
      return parseDiagnosticsNodeDaemon(invokeServiceMethodDaemon(methodName));
    }
    else {
      return parseDiagnosticsNodeObservatory(invokeServiceMethodObservatory(methodName));
    }
  }

  public CompletableFuture<DiagnosticsNode> invokeServiceMethodReturningNode(String methodName, InspectorInstanceRef ref) {
    if (isDaemonApiSupported) {
      return parseDiagnosticsNodeDaemon(invokeServiceMethodDaemon(methodName, ref));
    }
    else {
      return parseDiagnosticsNodeObservatory(invokeServiceMethodObservatory(methodName, ref));
    }
  }

  public CompletableFuture<Void> invokeVoidServiceMethod(String methodName, InspectorInstanceRef ref) {
    if (isDaemonApiSupported) {
      return invokeServiceMethodDaemon(methodName, ref).thenApply((ignored) -> null);
    }
    else {
      return invokeServiceMethodObservatory(methodName, ref).thenApply((ignored) -> null);
    }
  }

  public CompletableFuture<DiagnosticsNode> getRootWidget() {
    return invokeServiceMethodReturningNode("getRootWidget");
  }

  public CompletableFuture<DiagnosticsNode> getRootRenderObject() {
    return invokeServiceMethodReturningNode("getRootRenderObject");
  }

  public CompletableFuture<ArrayList<DiagnosticsPathNode>> getParentChain(DiagnosticsNode target) {
    if (isDaemonApiSupported) {
      return parseDiagnosticsPathDaeomon(invokeServiceMethodDaemon("getParentChain", target.getValueRef()));
    }
    else {
      return parseDiagnosticsPathObservatory(invokeServiceMethodObservatory("getParentChain", target.getValueRef()));
    }
  }

  CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPathObservatory(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::parseDiagnosticsPathObservatory);
  }

  private CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPathObservatory(InstanceRef pathRef) {
    return instanceRefToJson(pathRef).thenApplyAsync(this::parseDiagnosticsPathHelper);
  }

  CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPathDaeomon(CompletableFuture<JsonElement> jsonFuture) {
    return jsonFuture.thenApplyAsync(this::parseDiagnosticsPathHelper);
  }

  private ArrayList<DiagnosticsPathNode> parseDiagnosticsPathHelper(JsonElement jsonElement) {
    final JsonArray jsonArray = jsonElement.getAsJsonArray();
    final ArrayList<DiagnosticsPathNode> pathNodes = new ArrayList<>();
    for (JsonElement element : jsonArray) {
      pathNodes.add(new DiagnosticsPathNode(element.getAsJsonObject(), this));
    }
    return pathNodes;
  }

  public CompletableFuture<DiagnosticsNode> getSelection(DiagnosticsNode previousSelection, FlutterTreeType treeType) {
    CompletableFuture<DiagnosticsNode> result = null;
    final InspectorInstanceRef previousSelectionRef = previousSelection != null ? previousSelection.getDartDiagnosticRef() : null;

    switch (treeType) {
      case widget:
        result = invokeServiceMethodReturningNode("getSelectedWidget", previousSelectionRef);
        break;
      case renderObject:
        result = invokeServiceMethodReturningNode("getSelectedRenderObject", previousSelectionRef);
        break;
    }
    return result.thenApplyAsync((DiagnosticsNode newSelection) -> {
      if (newSelection.getDartDiagnosticRef().equals(previousSelectionRef)) {
        return previousSelection;
      }
      else {
        return newSelection;
      }
    });
  }

  @Override
  public void dispose() {
    vmService.streamCancel("Extension", VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
    // TODO(jacobr): dispose everything that needs to be disposed of.
  }

  private void maybeDisposeInspectorLibrary() {
    if (inspectorLibrary != null) {
      inspectorLibrary.dispose();
      inspectorLibrary = null;
    }
  }

  private void onVmServiceReceived(String streamId, Event event) {
    switch (streamId) {
      case VmService.ISOLATE_STREAM_ID:
        if (event.getKind() == EventKind.IsolateStart) {
          maybeDisposeInspectorLibrary();
        }
        else if (event.getKind() == EventKind.IsolateExit) {
          maybeDisposeInspectorLibrary();
          ApplicationManager.getApplication().invokeLater(() -> {
            for (InspectorServiceClient client : clients) {
              client.onIsolateStopped();
            }
          });
        }
        break;

      case VmService.DEBUG_STREAM_ID: {
        if (event.getKind() == EventKind.Inspect) {
          // Make sure the WidgetInspector on the device switches to show the inspected object
          // if the inspected object is a Widget or RenderObject.
          setSelection(event.getInspectee(), true);
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

  public void setSelection(InspectorInstanceRef selection, boolean uiAlreadyUpdated) {
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
    // This call requires the observatory protocol as an observatory InstanceRef is specified.
    handleSetSelectionObservatory(invokeServiceMethodOnRefObservatory("setSelection", selection), uiAlreadyUpdated);
  }

  private void handleSetSelectionObservatory(CompletableFuture<InstanceRef> setSelectionResult, boolean uiAlreadyUpdated) {
    // TODO(jacobr): we need to cancel if another inspect request comes in while we are trying this one.
    setSelectionResult.thenAcceptAsync((InstanceRef instanceRef) -> {
      handleSetSelectionHelper("true".equals(instanceRef.getValueAsString()), uiAlreadyUpdated);
    });
  }

  private void handleSetSelectionHelper(boolean selectionChanged, boolean uiAlreadyUpdated) {
    if (selectionChanged && !uiAlreadyUpdated) {
      notifySelectionChanged();
    }
  }

  private void handleSetSelectionDaemon(CompletableFuture<JsonElement> setSelectionResult, boolean uiAlreadyUpdated) {
    // TODO(jacobr): we need to cancel if another inspect request comes in while we are trying this one.
    setSelectionResult.thenAcceptAsync((JsonElement json) -> {
      handleSetSelectionHelper(json.getAsBoolean(), uiAlreadyUpdated);
    });
  }

  private void notifySelectionChanged() {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (InspectorServiceClient client : clients) {
        client.onInspectorSelectionChanged();
      }
    });
  }

  public CompletableFuture<Map<String, InstanceRef>> getEnumPropertyValues(InspectorInstanceRef ref) {
    if (ref == null || ref.getId() == null) {
      final CompletableFuture<Map<String, InstanceRef>> ret = new CompletableFuture<>();
      ret.complete(new HashMap<>());
      return ret;
    }
    return getInstance(toObservatoryInstanceRef(ref))
      .thenComposeAsync((Instance instance) -> getInspectorLibrary().getClass(instance.getClassRef()).thenApplyAsync((ClassObj clazz) -> {
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
      }));
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

    void onIsolateStopped();
  }
}
