/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.run.daemon.ConnectedDevice;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkManager;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DeviceSelectorAction extends ComboBoxAction implements DumbAware {
  final private List<AnAction> actions = new ArrayList<>();
  private boolean isListening = false;

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(actions);
    return group;
  }

  @Override
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @Override
  public void update(final AnActionEvent e) {
    // Suppress device actions in all but the toolbars.
    final String place = e.getPlace();
    if (!Objects.equals(place, ActionPlaces.NAVIGATION_BAR_TOOLBAR) && !Objects.equals(place, ActionPlaces.MAIN_TOOLBAR)) {
      e.getPresentation().setVisible(false);
      return;
    }

    // And only present in the context of a flutter project.
    final Project project = e.getProject();
    if (project == null || !FlutterSdkUtil.hasFlutterModule(project)) {
      e.getPresentation().setVisible(false);
      return;
    }

    super.update(e);

    final FlutterDaemonService service = FlutterDaemonService.getInstance(project);

    if (!isListening) {
      isListening = true;

      // Setup initial actions.
      updateActions(e.getPresentation(), project);

      updateVisibility(project, e.getPresentation());
      FlutterSdkManager.getInstance().addListener(new FlutterSdkManager.Listener() {
        @Override
        public void flutterSdkAdded() {
          updateVisibility(project, e.getPresentation());
        }

        @Override
        public void flutterSdkRemoved() {
          updateVisibility(project, e.getPresentation());
        }
      });

      service.addDeviceListener(new FlutterDaemonService.DeviceListener() {
        @Override
        public void deviceAdded(ConnectedDevice device) {
          updateActions(e.getPresentation(), project);
        }

        @Override
        public void selectedDeviceChanged(ConnectedDevice device) {
          updateActions(e.getPresentation(), project);
        }

        @Override
        public void deviceRemoved(ConnectedDevice device) {
          updateActions(e.getPresentation(), project);
        }
      });
    }
  }

  private void updateVisibility(final Project project, final Presentation presentation) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final boolean visible = FlutterSdk.getFlutterSdk(project) != null;
      presentation.setVisible(visible);

      final JComponent button = (JComponent)presentation.getClientProperty("customComponent");
      if (button != null) {
        button.setVisible(visible);
        if (button.getParent() != null) {
          button.getParent().doLayout();
        }
      }
    });
  }

  private void updateActions(Presentation presentation, @NotNull Project project) {
    actions.clear();

    final FlutterDaemonService service = FlutterDaemonService.getInstance(project);
    final Collection<ConnectedDevice> devices = service.getConnectedDevices();
    actions.addAll(devices.stream().map(SelectDeviceAction::new).collect(Collectors.toList()));

    if (actions.isEmpty()) {
      actions.add(new NoDevicesAction());
    }

    if (SystemInfo.isMac) {
      boolean simulatorOpen = false;
      for (AnAction action : actions) {
        if (action instanceof SelectDeviceAction) {
          final SelectDeviceAction deviceAction = (SelectDeviceAction)action;
          final ConnectedDevice device = deviceAction.device;
          if (StringUtil.equals(device.platform(), "ios") && device.emulator()) {
            simulatorOpen = true;
          }
        }
      }

      actions.add(new Separator());
      actions.add(new OpenSimulatorAction(!simulatorOpen));

      ApplicationManager.getApplication().invokeAndWait(() -> {
        final ConnectedDevice selectedDevice = service.getSelectedDevice();

        for (AnAction action : actions) {
          if (action instanceof SelectDeviceAction) {
            final SelectDeviceAction deviceAction = (SelectDeviceAction)action;

            if (Objects.equals(deviceAction.device, selectedDevice)) {
              final Presentation template = action.getTemplatePresentation();
              presentation.setIcon(template.getIcon());
              presentation.setText(template.getText());
              presentation.setEnabled(true);
              return;
            }
          }
        }

        presentation.setText(null);
      });
    }
  }

  private static class NoDevicesAction extends AnAction implements TransparentUpdate {
    NoDevicesAction() {
      super("No devices", null, null);
      getTemplatePresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      // No-op
    }
  }

  private static class OpenSimulatorAction extends AnAction {
    final boolean enabled;

    OpenSimulatorAction(boolean enabled) {
      super("Open iOS Simulator");

      this.enabled = enabled;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
      try {
        final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", "Simulator.app");
        final OSProcessHandler handler = new OSProcessHandler(cmd);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(final ProcessEvent event) {
            if (event.getExitCode() != 0) {
              FlutterErrors.showError(
                "Error Opening Simulator",
                event.getText());
            }
          }
        });
        handler.startNotify();
      }
      catch (ExecutionException e) {
        FlutterErrors.showError(
          "Error Opening Simulator",
          FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      }
    }
  }

  private static class SelectDeviceAction extends AnAction {
    @NotNull
    private final ConnectedDevice device;

    SelectDeviceAction(@NotNull ConnectedDevice device) {
      super(device.deviceName(), null, FlutterIcons.Phone);
      this.device = device;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final Project project = e.getProject();
      final FlutterDaemonService service = project != null ? FlutterDaemonService.getInstance(project) : null;
      if (service != null) {
        service.setSelectedDevice(device);
      }
    }
  }
}
