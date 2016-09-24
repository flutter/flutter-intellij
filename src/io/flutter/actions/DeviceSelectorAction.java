/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class DeviceSelectorAction extends ComboBoxAction implements DumbAware {

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        //TODO: fill in from DaemonManager.
        return group;
    }

    @Override
    public void update(AnActionEvent e) {
        //TODO: fill in.
    }


    private static class SelectDeviceAction extends AnAction {

        SelectDeviceAction(String deviceName) {
            super(deviceName, null, FlutterIcons.Flutter_16);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            //TODO: fill in.
        }
    }
}
