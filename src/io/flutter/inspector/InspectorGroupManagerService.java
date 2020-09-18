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
import com.intellij.openapi.util.Disposer;
import io.flutter.run.FlutterAppManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncUtils;
import org.dartlang.vm.service.VmService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service that provides the current inspector state independent of a specific
 * application run.
 * <p>
 * This class provides a {@link Listener} that notifies consumers when state
 * has changed.
 * <ul>
 * <li>The inspector is available.</li>
 * <li>The inspector selection when it changes</li>
 * <li>When the UI displayed in the app has changed</li>
 * </ul>
 */
public class InspectorGroupManagerService implements Disposable {

  /**
   * Convenience implementation of Listener that tracks the active
   * InspectorService and manages the creation of an
   * InspectorObjectGroupManager for easy inspector lifecycle management.
   */
  public static class Client implements Listener {
    private InspectorService service;
    private InspectorObjectGroupManager groupManager;

    public Client(Disposable parent) {
      Disposer.register(parent, () -> {
        if (groupManager != null) {
          groupManager.clear(false);
        }
      });
    }

    public InspectorObjectGroupManager getGroupManager() {
      if (groupManager == null && service != null) {
        groupManager = new InspectorObjectGroupManager(service, "client");
      }
      return groupManager;
    }

    public FlutterApp getApp() {
      if (service == null) return null;
      return service.getApp();
    }

    public InspectorService getInspectorService() {
      return service;
    }

    @Override
    public void onInspectorAvailable(InspectorService service) {
      if (this.service == service) return;
      if (groupManager != null) {
        groupManager.clear(service == null);
      }
      this.service = service;
      groupManager = null;
      onInspectorAvailabilityChanged();
    }

    /**
     * Clients should override this method to be notified when the inspector
     * availabilty changed.
     */
    public void onInspectorAvailabilityChanged() {
    }

    public InspectorService.ObjectGroup getCurrentObjectGroup() {
      groupManager = getGroupManager();
      if (groupManager == null) return null;
      return groupManager.getCurrent();
    }
  }

  private interface InvokeListener {
    void run(Listener listener);
  }

  public interface Listener {
    void onInspectorAvailable(InspectorService service);

    /**
     * Called when something has changed and UI dependent on inspector state
     * should re-render.
     *
     * @param force indicates that the old UI is likely completely obsolete.
     */
    default void requestRepaint(boolean force) {
    }

    /**
     * Called whenever Flutter renders a frame.
     */
    default void onFlutterFrame() {
    }

    default void notifyAppReloaded() {
    }

    default void notifyAppRestarted() {
    }

    /**
     * Called when the inspector selection has changed.
     */
    default void onSelectionChanged(DiagnosticsNode selection) {
    }
  }

  private final Set<Listener> listeners = new HashSet<>();
  boolean started = false;
  private CompletableFuture<InspectorService> inspectorServiceFuture;
  private FlutterApp app;
  private FlutterApp.FlutterAppListener appListener;
  private DiagnosticsNode selection;
  private InspectorService inspectorService;
  private InspectorObjectGroupManager selectionGroups;

  public InspectorGroupManagerService(Project project) {
    FlutterAppManager.getInstance(project).getActiveAppAsStream().listen(
      this::updateActiveApp, true);
    Disposer.register(project, this);
  }

  @NotNull
  public static InspectorGroupManagerService getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, InspectorGroupManagerService.class);
  }

  public InspectorService getInspectorService() {
    return inspectorService;
  }

  public void addListener(@NotNull Listener listener, Disposable disposable) {
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
    Disposer.register(disposable, () -> removeListener(listener));
  }

  private void removeListener(@NotNull Listener listener) {
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
        // TODO(jacobr): should we report an error here?
        selectionGroups.cancelNext();
        return;
      }

      selectionGroups.promoteNext();
      invokeOnAllListeners((listener) -> listener.onSelectionChanged(selection));
    });
  }

  private void requestRepaint(boolean force) {
    invokeOnAllListeners((listener) -> listener.requestRepaint(force));
  }

  private void updateActiveApp(FlutterApp app) {
    if (app == this.app) {
      return;
    }
    selection = null;

    if (this.app != null && appListener != null) {
      this.app.removeStateListener(appListener);
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
          requestRepaint(false);
        }
        if (newState == FlutterApp.State.TERMINATING) {
          inspectorService = null;

          invokeOnAllListeners((listener) -> listener.onInspectorAvailable(inspectorService));
        }
      }

      @Override
      public void notifyAppReloaded() {
        invokeOnAllListeners(Listener::notifyAppReloaded);

        requestRepaint(true);
      }

      @Override
      public void notifyAppRestarted() {
        invokeOnAllListeners(Listener::notifyAppRestarted);

        requestRepaint(true);
      }

      public void notifyVmServiceAvailable(VmService vmService) {
        assert (app.getFlutterDebugProcess() != null);
        inspectorServiceFuture = app.getFlutterDebugProcess().getInspectorService();

        AsyncUtils.whenCompleteUiThread(inspectorServiceFuture, (service, error) -> {
          invokeOnAllListeners((listener) -> listener.onInspectorAvailable(service));

          if (inspectorServiceFuture == null || inspectorServiceFuture.getNow(null) != service) return;
          inspectorService = service;
          selection = null;
          selectionGroups = new InspectorObjectGroupManager(inspectorService, "selection");
          loadSelection();

          if (app != InspectorGroupManagerService.this.app) return;

          service.addClient(new InspectorService.InspectorServiceClient() {
            @Override
            public void onInspectorSelectionChanged(boolean uiAlreadyUpdated, boolean textEditorUpdated) {
              loadSelection();
            }

            @Override
            public void onFlutterFrame() {
              invokeOnAllListeners(Listener::onFlutterFrame);
            }

            @Override
            public CompletableFuture<?> onForceRefresh() {
              requestRepaint(true);
              // Return null instead of a CompletableFuture as the
              // InspectorService should not wait for our client to be ready.
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
      final ArrayList<Listener> cachedListeners;
      synchronized (listeners) {
        cachedListeners = Lists.newArrayList(listeners);
      }
      for (Listener listener : cachedListeners) {
        callback.run(listener);
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
}
