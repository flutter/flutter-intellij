/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ModalityUiUtil;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterDevice;
import io.flutter.run.daemon.DeviceService;
import io.flutter.sdk.AndroidEmulatorManager;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class DeviceSelectorAction extends ComboBoxAction implements DumbAware {
  private final List<AnAction> actions = new ArrayList<>();
  private final List<Project> knownProjects = Collections.synchronizedList(new ArrayList<>());

  private SelectDeviceAction selectedDeviceAction;

  DeviceSelectorAction() {
    setSmallVariant(true);
  }

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
    final Presentation presentation = e.getPresentation();
    final Application application = ApplicationManager.getApplication();
    if (application == null) return;

    super.update(e);

    application.invokeLater(() -> {
      if (!isSelectorVisible(project)) {
        presentation.setVisible(false);
        return;
      }

      if (!knownProjects.contains(project)) {
        knownProjects.add(project);
        application.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
          @Override
          public void projectClosed(@NotNull Project closedProject) {
            knownProjects.remove(closedProject);
          }
        });
        Runnable deviceListener = () -> queueUpdate(project, presentation);
        DeviceService.getInstance(project).addListener(deviceListener);

        // Listen for android device changes, and rebuild the menu if necessary.
        Runnable emulatorListener = () -> queueUpdate(project, presentation);
        AndroidEmulatorManager.getInstance(project).addListener(emulatorListener);
        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
          public void projectClosing(@NotNull Project project) {
            DeviceService.getInstance(project).removeListener(deviceListener);
            AndroidEmulatorManager.getInstance(project).removeListener(emulatorListener);
          }
        });
        update(project, presentation);
      }

      final DeviceService deviceService = DeviceService.getInstance(project);

      final FlutterDevice selectedDevice = deviceService.getSelectedDevice();
      final Collection<FlutterDevice> devices = deviceService.getConnectedDevices();

      if (devices.isEmpty()) {
        final boolean isLoading = deviceService.getStatus() == DeviceService.State.LOADING;
        if (isLoading) {
          presentation.setText(FlutterBundle.message("devicelist.loading"));
        }
        else {
          presentation.setText("<no devices>");
        }
      }
      else if (selectedDevice == null) {
        presentation.setText("<no device selected>");
      }
      else if (selectedDeviceAction != null) {
        final Presentation template = selectedDeviceAction.getTemplatePresentation();
        presentation.setIcon(template.getIcon());
        presentation.setText(selectedDevice.presentationName());
        presentation.setEnabled(true);
      }
    });
  }

  private void queueUpdate(@NotNull Project project, @NotNull Presentation presentation) {
    ModalityUiUtil.invokeLaterIfNeeded(
      ModalityState.defaultModalityState(),
      () -> update(project, presentation));
  }

  private void update(@NotNull Project project, @NotNull Presentation presentation) {
    if (project.isDisposed()) {
      return; // This check is probably unnecessary, but safe.
    }
    FlutterUtils.invokeAndWait(() -> {
      updateActions(project, presentation);
      updateVisibility(project, presentation);
    });
  }

  private static void updateVisibility(final Project project, final Presentation presentation) {
    final boolean visible = isSelectorVisible(project);

    final JComponent component = (JComponent)presentation.getClientProperty("customComponent");
    if (component != null) {
      component.setVisible(visible);
      if (component.getParent() != null) {
        component.getParent().doLayout();
        component.getParent().repaint();
      }
    }
  }

  private static boolean isSelectorVisible(@Nullable Project project) {
    if (project == null || !FlutterModuleUtils.hasFlutterModule(project)) {
      return false;
    }
    final DeviceService deviceService = DeviceService.getInstance(project);
    return deviceService.isRefreshInProgress() || deviceService.getStatus() != DeviceService.State.INACTIVE;
  }

  private void updateActions(@NotNull Project project, Presentation presentation) {
    actions.clear();

    final DeviceService deviceService = DeviceService.getInstance(project);

    final FlutterDevice selectedDevice = deviceService.getSelectedDevice();
    final Collection<FlutterDevice> devices = deviceService.getConnectedDevices();

    selectedDeviceAction = null;

    for (FlutterDevice device : devices) {
      final SelectDeviceAction deviceAction = new SelectDeviceAction(device, devices);
      actions.add(deviceAction);

      if (Objects.equals(device, selectedDevice)) {
        selectedDeviceAction = deviceAction;

        final Presentation template = deviceAction.getTemplatePresentation();
        presentation.setIcon(template.getIcon());
        //presentation.setText(deviceAction.presentationName());
        presentation.setEnabled(true);
      }
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

    // Add Open Android emulators actions.
    final List<OpenEmulatorAction> emulatorActions = OpenEmulatorAction.getEmulatorActions(project);
    if (!emulatorActions.isEmpty()) {
      actions.add(new Separator());
      actions.addAll(emulatorActions);
    }
    if (!FlutterModuleUtils.hasInternalDartSdkPath(project)) {
      actions.add(new Separator());
      actions.add(new RestartFlutterDaemonAction());
    }
    ActivityTracker.getInstance().inc();
  }

  // Show the current device as selected when the combo box menu opens.
  @Override
  protected Condition<AnAction> getPreselectCondition() {
    return action -> action == selectedDeviceAction;
  }

  private static class SelectDeviceAction extends AnAction {
    @NotNull
    private final FlutterDevice device;

    SelectDeviceAction(@NotNull FlutterDevice device, @NotNull Collection<FlutterDevice> devices) {
      super(device.getUniqueName(devices), null, FlutterIcons.Phone);
      this.device = device;
    }

    public String presentationName() {
      return device.presentationName();
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
