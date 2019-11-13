/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.vmService.ServiceExtensionDescription;
import io.flutter.vmService.ServiceExtensionState;
import io.flutter.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class TogglePlatformAction extends ToolbarComboBoxAction {
  private static final Logger LOG = Logger.getInstance(TogglePlatformAction.class);

  private static final ServiceExtensionDescription extensionDescription = ServiceExtensions.togglePlatformMode;

  private final @NotNull FlutterApp app;
  private final @NotNull AppState appState;
  private final DefaultActionGroup myActionGroup;
  private final FlutterViewAction fuchsiaAction;

  private PlatformTarget selectedPlatform;

  public TogglePlatformAction(@NotNull AppState appState, @NotNull FlutterApp app) {
    super();
    this.app = app;
    this.appState = appState;
    setSmallVariant(false);
    myActionGroup = createPopupActionGroup(appState, app);
    fuchsiaAction = new PlatformTargetAction(app, PlatformTarget.fuchsia);
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return myActionGroup;
  }

  @Override
  public final void update(AnActionEvent e) {
    app.getVMServiceManager().getServiceExtensionState(extensionDescription.getExtension()).listen((state) -> {
      selectedPlatform = PlatformTarget.parseValue((String)state.getValue());
    }, true);

    String selectorText = "Platform:";
    if (selectedPlatform != null && selectedPlatform != PlatformTarget.unknown) {
      if (selectedPlatform == PlatformTarget.fuchsia && !appState.flutterViewActions.contains(fuchsiaAction)) {
        myActionGroup.add(appState.registerAction(fuchsiaAction));
      }

      final int platformIndex = extensionDescription.getValues().indexOf(selectedPlatform.name());
      if (platformIndex == -1) {
        selectorText = "Platform: Unknown";
        LOG.info("Unknown platform: " + selectedPlatform.name());
      }
      else {
        selectorText = (String)extensionDescription.getTooltips().get(platformIndex);
      }
    }

    e.getPresentation().setText(selectorText);
    e.getPresentation().setDescription(extensionDescription.getDescription());
    e.getPresentation().setEnabled(app.isSessionActive());
  }

  private static DefaultActionGroup createPopupActionGroup(AppState appState, FlutterApp app) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(appState.registerAction(new PlatformTargetAction(app, PlatformTarget.android)));
    group.add(appState.registerAction(new PlatformTargetAction(app, PlatformTarget.iOS)));
    return group;
  }
}

class PlatformTargetAction extends FlutterViewAction implements Toggleable, Disposable {
  private static final ServiceExtensionDescription extensionDescription = ServiceExtensions.togglePlatformMode;
  private final PlatformTarget platformTarget;
  private StreamSubscription<ServiceExtensionState> currentValueSubscription;
  private boolean selected = false;

  PlatformTargetAction(@NotNull FlutterApp app, PlatformTarget platformTarget) {
    super(app,
          platformTarget.toString(),
          extensionDescription.getDescription(),
          null);
    this.platformTarget = platformTarget;
  }

  @Override
  @SuppressWarnings("Duplicates")
  public void update(@NotNull AnActionEvent e) {
    if (!app.isSessionActive()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    app.hasServiceExtension(extensionDescription.getExtension(), (enabled) -> {
      e.getPresentation().setEnabled(app.isSessionActive() && enabled);
    });

    e.getPresentation().putClientProperty(SELECTED_PROPERTY, selected);

    if (currentValueSubscription == null) {
      currentValueSubscription =
        app.getVMServiceManager().getServiceExtensionState(extensionDescription.getExtension()).listen((state) -> {
          this.setSelected(e, state);
        }, true);
    }
  }

  @Override
  public void dispose() {
    if (currentValueSubscription != null) {
      currentValueSubscription.dispose();
      currentValueSubscription = null;
    }
  }

  @Override
  public void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      app.togglePlatform(platformTarget.name());

      app.getVMServiceManager().setServiceExtensionState(
        extensionDescription.getExtension(),
        true,
        platformTarget.name());
    }
  }

  public void setSelected(@NotNull AnActionEvent event, ServiceExtensionState state) {
    @Nullable final Object value = state.getValue();

    final boolean selected = value != null && value.equals(platformTarget.name());
    this.selected = selected;
    event.getPresentation().putClientProperty(SELECTED_PROPERTY, selected);
  }
}

enum PlatformTarget {
  iOS,
  android {
    public String toString() {
      return "Android";
    }
  },
  fuchsia {
    public String toString() {
      return "Fuchsia";
    }
  },
  unknown;

  public static PlatformTarget parseValue(@Nullable String value) {
    if (value == null) {
      return unknown;
    }

    try {
      return valueOf(value);
    }
    catch (NullPointerException | IllegalArgumentException e) {
      // Default to {@link unknown} in the event that {@link value} is null or if
      // there is not a {@link PlatformTarget} value with the name {@link value}.
      return unknown;
    }
  }
}
