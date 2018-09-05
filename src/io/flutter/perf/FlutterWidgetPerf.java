/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EdtInvocationManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class FlutterWidgetPerf implements Disposable {
  @NotNull final FlutterApp app;
  @NotNull final FlutterApp.FlutterAppListener appListener;
  private StreamSubscription<IsolateRef> isolateRefStreamSubscription;

  private boolean isStarted;
  private boolean isDirty = true;
  private boolean isDisposed = false;
  private boolean requestInProgress = false;

  private ScriptManager scriptManager;
  @SuppressWarnings("FieldCanBeLocal")
  private VmServiceListener vmServiceListener;

  private final Map<FileEditor, EditorPerfDecorations> editorDecorations = new HashMap<>();

  @Nullable VirtualFile currentFile;
  @Nullable FileEditor currentEditor;

  FlutterWidgetPerf(@NotNull FlutterApp app) {
    this.app = app;

    isStarted = app.isStarted();

    // start listening for frames, reload and restart events
    appListener = new FlutterApp.FlutterAppListener() {
      @Override
      public void stateChanged(FlutterApp.State newState) {
        if (!isStarted && app.isStarted()) {
          isStarted = true;
          requestRepaint(When.now);
        }
      }

      @Override
      public void notifyAppReloaded() {
        requestRepaint(When.now);
      }

      @Override
      public void notifyAppRestarted() {
        requestRepaint(When.now);
      }

      @Override
      public void notifyFrameRendered() {
        requestRepaint(When.soon);
      }

      public void notifyVmServiceAvailable(VmService vmService) {
        setupConnection(vmService);
      }
    };

    app.addStateListener(appListener);

    if (app.getVmService() != null) {
      setupConnection(app.getVmService());
    }
  }

  @NotNull
  public FlutterApp getApp() {
    return app;
  }

  private boolean isConnected() {
    return scriptManager != null;
  }

  private IsolateRef getCurrentIsolateRef() {
    assert app.getPerfService() != null;
    return app.getPerfService().getCurrentFlutterIsolateRaw();
  }

  private void setupConnection(@NotNull VmService vmService) {
    if (isDisposed) {
      return;
    }

    if (scriptManager != null) {
      return;
    }

    scriptManager = new ScriptManager(vmService);

    assert app.getPerfService() != null;
    isolateRefStreamSubscription = app.getPerfService().getCurrentFlutterIsolate(
      (isolateRef) -> requestRepaint(When.soon), false);

    vmServiceListener = new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        if (isDisposed) {
          return;
        }

        final EventKind kind = event.getKind();

        if (kind == EventKind.PauseBreakpoint || kind == EventKind.PauseException ||
            kind == EventKind.PauseInterrupted) {
          requestRepaint(When.soon);
        }
      }
    };
    vmService.addVmServiceListener(vmServiceListener);

    requestRepaint(When.soon);
  }

  /**
   * Schedule a repaint of the coverage information.
   * <p>
   * When.now schedules a repaint immediately.
   * <p>
   * When.soon will schedule a repaint shortly; that can get delayed by another request, with a maximum delay.
   */
  private void requestRepaint(When when) {
    isDirty = true;

    if (requestInProgress) {
      return;
    }

    if (!isConnected() || this.currentFile == null || this.currentEditor == null) {
      return;
    }

    requestInProgress = true;

    final FileEditor editor = this.currentEditor;
    final VirtualFile file = this.currentFile;

    JobScheduler.getScheduler().schedule(() -> performRequest(editor, file), 0, TimeUnit.SECONDS);
  }

  private void performRequest(FileEditor fileEditor, VirtualFile file) {
    assert !EdtInvocationManager.getInstance().isEventDispatchThread();

    assert app.getPerfService() != null;
    final IsolateRef isolateRef = app.getPerfService().getCurrentFlutterIsolateRaw();

    final VmService vmService = app.getVmService();
    assert vmService != null;

    if (isolateRef == null) {
      requestInProgress = false;
      return;
    }

    this.isDirty = false;

    scriptManager.setCurrentIsolate(isolateRef);

    @Nullable final ScriptRef scriptRef = scriptManager.getScriptRefFor(file);
    if (scriptRef == null) {
      performRequestFinish();
      return;
    }

    final JsonObject params = new JsonObject();
    final JsonArray arr = new JsonArray();
    arr.add("Coverage");

    params.add("reports", arr);

    // Pass in just the current scriptId.
    params.addProperty("scriptId", scriptRef.getId());

    // Make sure we get good 'not covered' info.
    // TODO(devoncarew): Does this work under the CFE?
    params.addProperty("forceCompile", true);

    // TODO(devoncarew): When the latest verison of the library is available, use the getSourceReport() call directly.
    vmService.callServiceExtension(isolateRef.getId(), "getSourceReport", params, new ServiceExtensionConsumer() {
      @Override
      public void received(JsonObject object) {
        JobScheduler.getScheduler().schedule(() -> {
          final SourceReport report = new SourceReport(object);
          final EditorPerfDecorations editorDecoration = editorDecorations.get(fileEditor);

          editorDecoration.updateFromSourceReport(scriptManager, file, report);

          performRequestFinish();
        }, 0, TimeUnit.MILLISECONDS);
      }

      @Override
      public void onError(RPCError error) {
        performRequestFinish();
      }
    });
  }

  private void performRequestFinish() {
    requestInProgress = false;

    if (isDirty) {
      requestRepaint(When.soon);
    }
  }

  public void showFor(@Nullable VirtualFile file, @Nullable FileEditor fileEditor) {
    this.currentFile = file;
    this.currentEditor = fileEditor;

    // Harvest old editors.
    harvestInvalidEditors();

    if (fileEditor != null) {
      // Create a new EditorPerfDecorations if necessary.
      if (!editorDecorations.containsKey(fileEditor)) {
        editorDecorations.put(fileEditor, new EditorPerfDecorations(fileEditor));
      }

      requestRepaint(When.now);
    }
  }

  private void harvestInvalidEditors() {
    final Iterator<FileEditor> editors = editorDecorations.keySet().iterator();

    while (editors.hasNext()) {
      final FileEditor editor = editors.next();
      if (!editor.isValid()) {
        final EditorPerfDecorations editorPerfDecorations = editorDecorations.get(editor);
        editors.remove();
        editorPerfDecorations.dispose();
      }
    }
  }

  @Override
  public void dispose() {
    if (isDisposed) {
      return;
    }

    this.isDisposed = true;

    app.removeStateListener(appListener);

    scriptManager = null;

    // TODO(devoncarew): This method will be available in a future version of the service protocol library.
    //if (vmServiceListener != null) {
    //  app.getVmService().removeEventListener(vmServiceListener);
    //}

    for (EditorPerfDecorations decorations : editorDecorations.values()) {
      decorations.dispose();
    }

    if (isolateRefStreamSubscription != null) {
      isolateRefStreamSubscription.dispose();
    }
  }

  private enum When {
    now,
    soon
  }
}
