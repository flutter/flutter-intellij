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
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.run.daemon.DeviceService;
import io.flutter.run.daemon.FlutterDevice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

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
    if (!isSelectorVisible(project)) {
      e.getPresentation().setVisible(false);
      return;
    }

    super.update(e);

    if (!knownProjects.contains(project)) {
      knownProjects.add(project);
      Disposer.register(project, () -> knownProjects.remove(project));

      DeviceService.getInstance(project).addListener(() -> update(project, e.getPresentation()));
      update(project, e.getPresentation());
    }
  }

  private void update(Project project, Presentation presentation) {
    FlutterUtils.invokeAndWait(() -> {
      updateVisibility(project, presentation);
      updateActions(project, presentation);
    });
  }

  private void updateVisibility(final Project project, final Presentation presentation) {
    final boolean visible = isSelectorVisible(project);
    presentation.setVisible(visible);

    final JComponent button = (JComponent)presentation.getClientProperty("customComponent");
    if (button != null) {
      button.setVisible(visible);
      if (button.getParent() != null) {
        button.getParent().doLayout();
      }
    }
  }

  private boolean isSelectorVisible(@Nullable Project project) {
    return project != null && DeviceService.getInstance(project).getStatus() != DeviceService.State.INACTIVE;
  }

  private void updateActions(@NotNull Project project, Presentation presentation) {
    actions.clear();

    final DeviceService service = DeviceService.getInstance(project);

    for (FlutterDevice item : service.getConnectedDevices()) {
      actions.add(new SelectDeviceAction(item));
    }

    if (actions.isEmpty()) {
      final boolean isLoading = service.getStatus() == DeviceService.State.LOADING;
      final String message = isLoading ? FlutterBundle.message("devicelist.loading") : FlutterBundle.message("devicelist.empty");
      actions.add(new NoDevicesAction(message));
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

    presentation.setText("<no devices>");
  }

  // It's not clear if we need TransparentUpdate, but apparently it will make the UI refresh
  // the display more often?
  // See: https://intellij-support.jetbrains.com/hc/en-us/community/posts/206772825-How-to-enable-disable-action-in-runtime
  private static class NoDevicesAction extends AnAction implements TransparentUpdate {
    NoDevicesAction(String message) {
      super(message, null, null);
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
      final DeviceService service = project == null ? null : DeviceService.getInstance(project);
      if (service != null) {
        service.setSelectedDevice(device);
      }
    }
  }
}
