/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import icons.FlutterIcons;
import io.flutter.run.daemon.ConnectedDevice;
import io.flutter.run.daemon.FlutterDaemonService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class DeviceSelectorAction extends ComboBoxAction implements DumbAware {

  final private List<SelectDeviceAction> actions = new ArrayList<>();

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    final DefaultActionGroup group = new DefaultActionGroup();
    FlutterDaemonService service = FlutterDaemonService.getInstance();
    if (service != null) {
      final Collection<ConnectedDevice> devices = service.getConnectedDevices();
      actions.clear();
      actions.addAll(devices.stream().map(SelectDeviceAction::new).collect(Collectors.toList()));
      group.addAll(actions);
    }
    return group;
  }


  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();

    FlutterDaemonService service = FlutterDaemonService.getInstance();
    if (service == null) {
      return;
    }

    ConnectedDevice device = service.getSelectedDevice();
    if (device == null) {
      return;
    }

    for (SelectDeviceAction action : actions) {
      if (Objects.equals(action.device, device)) {
        Presentation templatePresentation = action.getTemplatePresentation();
        presentation.setIcon(templatePresentation.getIcon());
        presentation.setText(templatePresentation.getText());
        presentation.setEnabled(true);
        return;
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
      FlutterDaemonService service = FlutterDaemonService.getInstance();
      if (service != null) {
        service.setSelectedDevice(device);
      }
    }
  }
}
