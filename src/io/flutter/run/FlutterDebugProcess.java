/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.FlutterUtils;
import io.flutter.actions.ReloadAllFlutterApps;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.actions.RestartAllFlutterApps;
import io.flutter.actions.RestartFlutterApp;
import io.flutter.inspector.InspectorService;
import io.flutter.run.common.RunMode;
import io.flutter.inspector.EvalOnDartLibrary;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.view.FlutterViewMessages;
import io.flutter.vmService.DartVmServiceDebugProcess;
import io.flutter.vmService.frame.DartVmServiceEvaluator;
import io.flutter.vmService.frame.DartVmServiceStackFrame;
import io.flutter.vmService.frame.DartVmServiceValue;
import org.dartlang.vm.service.VmService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * A debug process that handles hot reloads for Flutter.
 * <p>
 * It's used for both the 'Run' and 'Debug' modes. (We apparently need a debug process even
 * when not debugging in order to support hot reload.)
 */
public class FlutterDebugProcess extends DartVmServiceDebugProcess implements Disposable {
  private static final Logger LOG = Logger.getInstance(FlutterDebugProcess.class);

  private final @NotNull FlutterApp app;

  public FlutterDebugProcess(@NotNull FlutterApp app,
                             @NotNull ExecutionEnvironment executionEnvironment,
                             @NotNull XDebugSession session,
                             @NotNull ExecutionResult executionResult,
                             @NotNull DartUrlResolver dartUrlResolver,
                             @NotNull PositionMapper mapper) {
    super(executionEnvironment, session, executionResult, dartUrlResolver, app.getConnector(), mapper);
    this.app = app;
  }

  @NotNull
  public FlutterApp getApp() {
    return app;
  }

  String currentIsolateId;

  CompletableFuture<InspectorService> inspectorService;

  public String getCurrentIsolateId() {
    return currentIsolateId;
  }

  public CompletableFuture<InspectorService> getInspectorService() {
    if (inspectorService == null) {
      inspectorService = InspectorService.create(app, app.getFlutterDebugProcess(), app.getVmService());
    }
    return inspectorService;
  }

  @Override
  public void dispose() {
    // XXX really dispose.
    setCurrentIsolateId(null);
  }

  public class DebuggerScopeCache {
    private final String prefix;

    private int MAX_VALUES = 3;
    final ArrayList<DartVmServiceValue> values = new ArrayList<>();

    DebuggerScopeCache(String prefix) {
      this.prefix = prefix;
    }

    public void add(DartVmServiceValue value) {
      if (values.size() >= MAX_VALUES) {
        values.remove(values.size() - 1);
      }
      values.add(0, value);
    }

    public void addToMap(Map<String, String> map) {
      for (int i = 0; i < values.size(); ++i) {
        // TODO(jacobr): due to Observatory lifetime issues, this value may
        // have expired so we may need to recompute form a backing store.
        final DartVmServiceValue value = values.get(i);

        map.put(prefix + i, value.getInstanceRef().getId());
      }
    }

    public void clear() {
      values.clear();
    }
  }

  public class ConsoleDebuggerScope {
    private final DebuggerScopeCache widgets;
    private final DebuggerScopeCache elements;
    private final DebuggerScopeCache renderObjects;

    ConsoleDebuggerScope() {
      widgets = new DebuggerScopeCache("w");
      elements = new DebuggerScopeCache("e");
      renderObjects = new DebuggerScopeCache("r");
    }

    void clear() {
      widgets.clear();
      elements.clear();
      renderObjects.clear();
    }

    public void addWidget(DartVmServiceValue widget) {
      widgets.add(widget);
    }

    public void addElement(DartVmServiceValue element) {
      elements.add(element);
    }

    public void addRenderObject(DartVmServiceValue renderObject) {
      renderObjects.add(renderObject);
    }

    Map<String, String> toMap() {
     final Map<String, String> map = new HashMap<>();
     widgets.addToMap(map);
     elements.addToMap(map);
     renderObjects.addToMap(map);
     return map;
    }
  }

  final Map<String, ConsoleDebuggerScope> debuggerScopes = new HashMap<>();

  public void setCurrentIsolateId(String isolateId) {
    currentIsolateId = isolateId;
    if (isolateId == null ) {
      debuggerScopes.clear();
    } else {
      debuggerScopes.putIfAbsent(isolateId, new ConsoleDebuggerScope());
    }
  }


  public ConsoleDebuggerScope getDebuggerScope(String isolateId) {
    return debuggerScopes.get(isolateId);
  }

  public ConsoleDebuggerScope getDebuggerScope() {
    final XStackFrame frame = getSession().getCurrentStackFrame();
    if (frame instanceof DartVmServiceStackFrame) {
      final DartVmServiceStackFrame dartStackFrame = (DartVmServiceStackFrame)frame;
      setCurrentIsolateId(dartStackFrame.getIsolateId());
    }
    return currentIsolateId != null ? debuggerScopes.get(currentIsolateId) : null;
  }

  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    XStackFrame frame = getSession().getCurrentStackFrame();
    if (frame== null) {
      return new DartVmServiceEvaluator(this);
    }
    return frame == null ? null : frame.getEvaluator();
  }

  @Override
  protected void onVmConnected(@NotNull VmService vmService) {
    app.setFlutterDebugProcess(this);
    debuggerScopes.clear();
    FlutterViewMessages.sendDebugActive(getSession().getProject(), app, vmService);
  }

  @Override
  public void registerAdditionalActions(@NotNull final DefaultActionGroup leftToolbar,
                                        @NotNull final DefaultActionGroup topToolbar,
                                        @NotNull final DefaultActionGroup settings) {

    if (app.getMode() != RunMode.DEBUG) {
      // Remove the debug-specific actions that aren't needed when we're not debugging.

      // Remove all but specified actions.
      final AnAction[] leftActions = leftToolbar.getChildActionsOrStubs();
      // Not all on the classpath so we resort to Strings.
      final List<String> actionClassNames = Arrays
        .asList("com.intellij.execution.actions.StopAction", "com.intellij.ui.content.tabs.PinToolwindowTabAction",
                "com.intellij.execution.ui.actions.CloseAction", "com.intellij.ide.actions.ContextHelpAction");
      for (AnAction a : leftActions) {
        if (!actionClassNames.contains(a.getClass().getName())) {
          leftToolbar.remove(a);
        }
      }

      // Remove all top actions.
      final AnAction[] topActions = topToolbar.getChildActionsOrStubs();
      for (AnAction action : topActions) {
        topToolbar.remove(action);
      }

      // Remove all settings actions.
      final AnAction[] settingsActions = settings.getChildActionsOrStubs();
      for (AnAction a : settingsActions) {
        settings.remove(a);
      }
    }

    // Add actions common to the run and debug windows.
    final Computable<Boolean> isSessionActive = () -> app.isStarted() && getVmConnected() && !getSession().isStopped();
    final Computable<Boolean> canReload = () -> app.getLaunchMode().supportsReload() && isSessionActive.compute() && !app.isReloading();
    final Computable<Boolean> debugUrlAvailable = () -> isSessionActive.compute() && app.getConnector().getBrowserUrl() != null;

    if (app.getMode() == RunMode.DEBUG) {
      topToolbar.addSeparator();
      topToolbar.addAction(new FlutterPopFrameAction());
    }

    topToolbar.addSeparator();
    topToolbar.addAction(new ReloadFlutterApp(app, canReload));
    topToolbar.addAction(new RestartFlutterApp(app, canReload));
    topToolbar.addSeparator();
    topToolbar.addAction(new OpenDevToolsAction(app.getConnector(), debugUrlAvailable));

    settings.addAction(new ReloadAllFlutterApps(app, canReload));
    settings.addAction(new RestartAllFlutterApps(app, canReload));
    // Don't call super since we have our own observatory action.
  }

  @Override
  public void sessionInitialized() {
    if (app.getMode() != RunMode.DEBUG) {
      suppressDebugViews(getSession().getUI());
    }
  }

  /**
   * Turn off debug-only views (variables and frames).
   */
  private static void suppressDebugViews(@Nullable RunnerLayoutUi ui) {
    if (ui == null) {
      return;
    }

    for (Content c : ui.getContents()) {
      if (!Objects.equals(c.getTabName(), "Console")) {
        try {
          GuiUtils.runOrInvokeAndWait(() -> ui.removeContent(c, false /* dispose? */));
        }
        catch (InvocationTargetException | InterruptedException e) {
          FlutterUtils.warn(LOG, e);
        }
      }
    }
  }
}
