/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.run.FlutterAppManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncUtils;
import io.flutter.vmService.VMServiceManager;
import org.dartlang.vm.service.VmService;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service that tracks the current inspector state independent of a specific
 * application run.
 * <p>
 * This class provides a {@link Listener} that notifies consumers when
 * <ul>
 * <li>The inspector is available.</li>
 * <li>The inspector selection when it changes</li>
 * <li>When the UI displayed in the app has changed</li>
 * </ul>
 */
public class InspectorStateService implements Disposable {

  private final Project project;
  private CompletableFuture<InspectorService> inspectorServiceFuture;
  private FlutterApp app;

  private FlutterApp.FlutterAppListener appListener;
  private DiagnosticsNode selection;
  private InspectorService inspectorService;

  boolean started = false;
  private InspectorObjectGroupManager selectionGroups;

  /**
   * List of listeners.
   */
  private final Set<Listener> listeners = new HashSet<>();

  @NotNull
  public static InspectorStateService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, InspectorStateService.class);
  }

  public InspectorStateService(Project project) {
    this.project = project;
    // XXX probably does not need to be on the ui thread.
    FlutterAppManager.getInstance(project).getActiveAppAsStream().listen(
      this::updateActiveApp, true);

  }

  public void addListener(@NotNull Listener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
    // Update the listener with the current active state if any.
    if (inspectorService != null) {
      listener.onInspectorAvailable(inspectorService);
    }
    if (selection != null) {
      listener.onSelectionChanged(selection);
    }
  }

  public void removeListener(@NotNull Listener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  private void loadSelection() {
    selectionGroups.cancelNext();

    final CompletableFuture<DiagnosticsNode> pendingSelectionFuture =
      selectionGroups.getNext().getSelection(null, InspectorService.FlutterTreeType.widget, true);
    selectionGroups.getNext().safeWhenComplete(pendingSelectionFuture, (selection, error) -> {
      if (error != null) {
        //                  FlutterUtils.warn(LOG, error);
        selectionGroups.cancelNext();
        return;
      }

      selectionGroups.promoteNext();
      invokeOnAllListeners((listener) -> {
        listener.onSelectionChanged(selection);
      });
    });
  }

  private void requestRepaint(boolean force) {
    invokeOnAllListeners((listener) -> {
      listener.requestRepaint(force);
    });
  }

  private void updateActiveApp(FlutterApp app) {
    if (app == this.app) {
      return;
    }
    if (this.app != null && appListener != null) {
      this.app.removeStateListener(appListener);
      this.app = null;
      appListener = null;
    }
    this.app = app;
    if (app == null) {
      return;
    }
    started = false;
    // start listening for frames, reload and restart events
    appListener = new FlutterApp.FlutterAppListener() {

      @Override
      public void stateChanged(FlutterApp.State newState) {
        if (!started && app.isStarted()) {
          started = true;
          requestRepaint(false); // XXX when is this called? Probably force=true
        }
      }

      @Override
      public void notifyAppReloaded() {
        requestRepaint(true);
      }

      @Override
      public void notifyAppRestarted() {
        requestRepaint(true);
      }

      public void notifyVmServiceAvailable(VmService vmService) {
        // XXX run this method on the main thread.
        //       setupConnection(vmService);
        inspectorServiceFuture = app.getFlutterDebugProcess().getInspectorService();

        AsyncUtils.whenCompleteUiThread(inspectorServiceFuture, (service, error) -> {
          invokeOnAllListeners((listener) -> {
            listener.onInspectorAvailable(service);
          });

          if (inspectorServiceFuture == null || inspectorServiceFuture.getNow(null) != service) return;
          inspectorService = service;
          selection = null;
          selectionGroups = new InspectorObjectGroupManager(inspectorService, "selection");
          loadSelection();

          if (app != InspectorStateService.this.app) return;

          service.addClient(new InspectorService.InspectorServiceClient() {
            @Override
            public void onInspectorSelectionChanged(boolean uiAlreadyUpdated, boolean textEditorUpdated) {
              loadSelection();
            }

            @Override
            public void onFlutterFrame() {
              invokeOnAllListeners((listener) -> {
                listener.onFlutterFrame();
              });
            }

            @Override
            public CompletableFuture<?> onForceRefresh() {
              requestRepaint(true); // XXX don't need a full repaint.
              return null;
            }
          });
        });

      }
    };

    app.addStateListener(appListener);
    if (app.getFlutterDebugProcess() != null) {
      appListener.notifyVmServiceAvailable(null);
    }
  }

  private void invokeOnAllListeners(InvokeListener callback) {
    AsyncUtils.invokeLater(() -> {
      ArrayList<Listener> cachedListeners;
      synchronized (listeners) {
        cachedListeners =  Lists.newArrayList(listeners);
      }
      ArrayList<Listener> toRemove = new ArrayList<>();
      for (Listener listener : cachedListeners) {
        if (listener.isValid()) {
          callback.run(listener);
        } else {
          synchronized (listeners) {
            listeners.remove(listener);
          }
        }
      }
    });
  }

  @Override
  public void dispose() {
    synchronized (listeners) {
      listeners.clear();
    }
    if (app != null) {
      this.app.removeStateListener(appListener);
    }
    this.app = null;
    appListener = null;

  }

  private interface InvokeListener {
    void run(Listener listener);
  }

  /**
   * Listener for changes to the active editors or open outlines.
   */
  public interface Listener {
    /**
     * Whether the listener is still valid.
     */
    boolean isValid();
    /**
     * Called when the inspector selection has changed.
     */
    void onSelectionChanged(DiagnosticsNode selection);


    void onInspectorAvailable(InspectorService service);

      /**
       * Called when something has changed and UI should be repainted.
       *
       * @param force indicates that the old UI is likely completely obsolete.
       */
     void requestRepaint(boolean force);

    /**
     * Called whenever Flutter renders a frame.
     */
    void onFlutterFrame();
  }
}
