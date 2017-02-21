/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import icons.FlutterIcons;
import io.flutter.FlutterUtils;
import io.flutter.run.daemon.FlutterDaemonService;
import io.flutter.run.daemon.FlutterDevice;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

public class DeviceSelectorAction extends ComboBoxAction implements DumbAware {
  final private List<AnAction> actions = new ArrayList<>();
  private final List<Project> knownProjects = Collections.synchronizedList(new ArrayList<>());

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

    // Only show device menu when the device daemon process is running.
    final Project project = e.getProject();
    final FlutterDaemonService service = project == null ? null : FlutterDaemonService.getInstance(project);
    if (service == null || !service.isActive()) {
      e.getPresentation().setVisible(false);
      return;
    }

    super.update(e);

    if (!knownProjects.contains(project)) {
      knownProjects.add(project);
      Disposer.register(project, () -> knownProjects.remove(project));

      // Setup initial actions.
      updateActions(e.getPresentation(), project);

      updateVisibility(project, e.getPresentation());
      service.addDeviceDaemonListener(() -> updateVisibility(project, e.getPresentation()));

      service.addDeviceListener(new FlutterDaemonService.DeviceListener() {
        @Override
        public void deviceAdded(FlutterDevice device) {
          updateActions(e.getPresentation(), project);
        }

        @Override
        public void selectedDeviceChanged(FlutterDevice device) {
          updateActions(e.getPresentation(), project);
        }

        @Override
        public void deviceRemoved(FlutterDevice device) {
          updateActions(e.getPresentation(), project);
        }
      });
    }
  }

  private void updateVisibility(final Project project, final Presentation presentation) {
    FlutterUtils.invokeAndWait(() -> {
      final boolean visible = FlutterDaemonService.getInstance(project).isActive();
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
    final Collection<FlutterDevice> devices = service.getConnectedDevices();
    actions.addAll(devices.stream().map(SelectDeviceAction::new).collect(Collectors.toList()));

    if (actions.isEmpty()) {
      actions.add(new NoDevicesAction());
    }

    // Show the 'Open iOS Simulator' action.
    if (SystemInfo.isMac) {
      boolean simulatorOpen = false;
      for (AnAction action : actions) {
        if (action instanceof SelectDeviceAction) {
          final SelectDeviceAction deviceAction = (SelectDeviceAction)action;
          final FlutterDevice device = deviceAction.device;
          if (device.isIOS() && device.emulator()) {
            simulatorOpen = true;
          }
        }
      }

      actions.add(new Separator());
      actions.add(new OpenSimulatorAction(!simulatorOpen));
    }

    FlutterUtils.invokeAndWait(() -> {
      final FlutterDevice selectedDevice = service.getSelectedDevice();
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

  private static class SelectDeviceAction extends AnAction {
    @NotNull
    private final FlutterDevice device;

    SelectDeviceAction(@NotNull FlutterDevice device) {
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
