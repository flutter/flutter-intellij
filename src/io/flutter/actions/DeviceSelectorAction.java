/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterDevice;
import io.flutter.run.daemon.DeviceService;
import io.flutter.sdk.AndroidEmulatorManager;
import io.flutter.utils.FlutterModuleUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceSelectorAction extends ComboBoxAction implements DumbAware {
  private final List<AnAction> actions = new ArrayList<>();
  private final List<Project> knownProjects = Collections.synchronizedList(new ArrayList<>());

  private SelectDeviceAction selectedDeviceAction;

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

      // Listen for android device changes, and rebuild the menu if necessary.
      AndroidEmulatorManager.getInstance(project).addListener(() -> update(project, e.getPresentation()));

      update(project, e.getPresentation());
    }
  }

  private void update(Project project, Presentation presentation) {
    FlutterUtils.invokeAndWait(() -> {
      updateActions(project, presentation);
      updateVisibility(project, presentation);
    });
  }

  private static void updateVisibility(final Project project, final Presentation presentation) {
    final boolean visible = isSelectorVisible(project);
    presentation.setVisible(visible);

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
    return project != null &&
           DeviceService.getInstance(project).getStatus() != DeviceService.State.INACTIVE &&
           FlutterModuleUtils.hasFlutterModule(project);
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
        presentation.setText(deviceAction.presentationName());
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

    if (devices.isEmpty()) {
      final boolean isLoading = deviceService.getStatus() == DeviceService.State.LOADING;
      if (isLoading) {
        presentation.setText(FlutterBundle.message("devicelist.loading"));
      }
      else {
        //noinspection DialogTitleCapitalization
        presentation.setText("<no devices>");
      }
    }
    else if (selectedDevice == null) {
      //noinspection DialogTitleCapitalization
      presentation.setText("<no device selected>");
    }
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
