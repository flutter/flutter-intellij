/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.common.base.Joiner;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceValue;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.*;

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
  private final FlutterDebugProcess debugProcess;
  private final VmService vmService;
  private final Set<InspectorServiceClient> clients;
  private EvalOnDartLibrary inspectorLibrary;
  private CompletableFuture<Set<String>> supportedServiceMethods;

  public InspectorService(FlutterDebugProcess debugProcess, VmService vmService) {
    clients = new HashSet<>();
    groupName = "intellij_inspector_" + nextGroupId;
    nextGroupId++;
    this.vmService = vmService;
    this.debugProcess = debugProcess;

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
  CompletableFuture<InstanceRef> invokeServiceMethod(String methodName) {
    return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(\"" + groupName + "\")", null);
  }

  CompletableFuture<InstanceRef> invokeServiceMethod(String methodName, InspectorInstanceRef arg) {
    if (arg == null || arg.getId() == null) {
      return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(null, \"" + groupName + "\")", null);
    }
    return getInspectorLibrary()
      .eval("WidgetInspectorService.instance." + methodName + "(\"" + arg.getId() + "\", \"" + groupName + "\")", null);
  }

  CompletableFuture<InstanceRef> invokeServiceMethodOnRef(String methodName, InstanceRef arg) {
    final HashMap<String, String> scope = new HashMap<>();
    if (arg == null) {
      return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(null, \"" + groupName + "\")", scope);
    }
    scope.put("arg1", arg.getId());
    return getInspectorLibrary().eval("WidgetInspectorService.instance." + methodName + "(arg1, \"" + groupName + "\")", scope);
  }

  CompletableFuture<DiagnosticsNode> parseDiagnosticsNode(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::parseDiagnosticsNode);
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
   * to display visually we care about properties that are not neccesarily
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
      sb.append("<Object>[");
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
    return invokeServiceMethod("toObject", inspectorInstanceRef);
  }

  private CompletableFuture<Instance> getInstance(InstanceRef instanceRef) {
    return getInspectorLibrary().getInstance(instanceRef);
  }

  CompletableFuture<Instance> getInstance(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::getInstance);
  }

  CompletableFuture<DiagnosticsNode> parseDiagnosticsNode(InstanceRef instanceRef) {
    return instanceRefToJson(instanceRef).thenApplyAsync((JsonElement jsonElement) -> {
      //noinspection CodeBlock2Expr
      return new DiagnosticsNode(jsonElement.getAsJsonObject(), this);
    });
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

  CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodes(InstanceRef instanceRef) {
    return instanceRefToJson(instanceRef).thenApplyAsync((JsonElement jsonElement) -> {
      final JsonArray jsonArray = jsonElement.getAsJsonArray();
      final ArrayList<DiagnosticsNode> nodes = new ArrayList<>();
      for (JsonElement element : jsonArray) {
        nodes.add(new DiagnosticsNode(element.getAsJsonObject(), this));
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
    return invokeServiceMethod("toObjectForSourceLocation", inspectorInstanceRef).thenApplyAsync(
      (InstanceRef instanceRef) -> {
        //noinspection CodeBlock2Expr
        return new DartVmServiceValue(debugProcess, inspectorLibrary.getIsolateId(), "inspectedObject", instanceRef, null, null, false);
      });
  }

  CompletableFuture<ArrayList<DiagnosticsNode>> parseDiagnosticsNodes(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::parseDiagnosticsNodes);
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
    // TODO(jacobr): remove call to hasServiceMethod("isWidgetTreeReady") after
    // the `isWidgetTreeReady` method has been in two revs of the Flutter Alpha
    // channel. The feature is expected to have landed in the Flutter dev
    // chanel on January 18, 2018.
    return hasServiceMethod("isWidgetTreeReady").<Boolean>thenComposeAsync((Boolean hasMethod) -> {
      if (!hasMethod) {
        // Fallback if the InspectorService doesn't provide the
        // isWidgetTreeReady method. In this case, we will fail gracefully
        // risking not displaying the Widget tree but ensuring we do not throw
        // exceptions due to accessing the widget tree before it is safe to.
        CompletableFuture<Boolean> value = new CompletableFuture<>();
        value.complete(false);
        return value;
      }
      return invokeServiceMethod("isWidgetTreeReady").thenApplyAsync((InstanceRef ref) -> {
        return "true".equals(ref.getValueAsString());
      });
    });
  }

  /**
   * Use this method to write code that is backwards compatible with versions
   * of Flutter that are too old to contain specific service methods.
   */
  private CompletableFuture<Boolean> hasServiceMethod(String methodName) {
    if (supportedServiceMethods == null) {
      EvalOnDartLibrary inspectorLibrary = getInspectorLibrary();
      final CompletableFuture<Library> libraryFuture = inspectorLibrary.libraryRef.thenComposeAsync(inspectorLibrary::getLibrary);
      supportedServiceMethods = libraryFuture.thenComposeAsync((Library library) -> {
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
      });
    }

    return supportedServiceMethods.thenApplyAsync(methodNames -> methodNames.contains(methodName));
  }

  private CompletableFuture<ArrayList<DiagnosticsNode>> getListHelper(
    InspectorInstanceRef instanceRef, String methodName) {
    return parseDiagnosticsNodes(invokeServiceMethod(methodName, instanceRef));
  }

  public CompletableFuture<DiagnosticsNode> getRootWidget() {
    return parseDiagnosticsNode(invokeServiceMethod("getRootWidget"));
  }

  public CompletableFuture<DiagnosticsNode> getRootRenderObject() {
    return parseDiagnosticsNode(invokeServiceMethod("getRootRenderObject"));
  }

  public CompletableFuture<ArrayList<DiagnosticsPathNode>> getParentChain(DiagnosticsNode target) {
    return parseDiagnosticsPath(invokeServiceMethod("getParentChain", target.getValueRef()));
  }

  CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPath(CompletableFuture<InstanceRef> instanceRefFuture) {
    return instanceRefFuture.thenComposeAsync(this::parseDiagnosticsPath);
  }

  private CompletableFuture<ArrayList<DiagnosticsPathNode>> parseDiagnosticsPath(InstanceRef pathRef) {
    return instanceRefToJson(pathRef).thenApplyAsync((JsonElement jsonElement) -> {
      final JsonArray jsonArray = jsonElement.getAsJsonArray();
      final ArrayList<DiagnosticsPathNode> pathNodes = new ArrayList<>();
      for (JsonElement element : jsonArray) {
        pathNodes.add(new DiagnosticsPathNode(element.getAsJsonObject(), this));
      }
      return pathNodes;
    });
  }

  public CompletableFuture<DiagnosticsNode> getSelection(DiagnosticsNode previousSelection, FlutterTreeType treeType) {
    CompletableFuture<InstanceRef> result = null;
    final InspectorInstanceRef previousSelectionRef = previousSelection != null ? previousSelection.getDartDiagnosticRef() : null;

    switch (treeType) {
      case widget:
        result = invokeServiceMethod("getSelectedWidget", previousSelectionRef);
        break;
      case renderObject:
        result = invokeServiceMethod("getSelectedRenderObject", previousSelectionRef);
        break;
    }
    assert (result != null);
    return parseDiagnosticsNode(result).thenApplyAsync((DiagnosticsNode newSelection) -> {
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
    handleSetSelection(invokeServiceMethod("setSelectionById", selection), uiAlreadyUpdated);
  }

  /**
   * Helper when we need to set selection given an observatory InstanceRef
   * instead of an InspectorInstanceRef.
   */
  public void setSelection(InstanceRef selection, boolean uiAlreadyUpdated) {
    handleSetSelection(invokeServiceMethodOnRef("setSelection", selection), uiAlreadyUpdated);
  }

  private void handleSetSelection(CompletableFuture<InstanceRef> setSelectionResult, boolean uiAlreadyUpdated) {
    // TODO(jacobr): we need to cancel if another inspect request comes in while we are trying this one.
    setSelectionResult.thenAcceptAsync((InstanceRef instanceRef) -> {
      if ("true".equals(instanceRef.getValueAsString())) {
        if (!uiAlreadyUpdated) {
          notifySelectionChanged();
        }
      }
    });
  }

  private void notifySelectionChanged() {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (InspectorServiceClient client : clients) {
        client.onInspectorSelectionChanged();
      }
    });
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
