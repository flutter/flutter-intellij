/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;


import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

@SuppressWarnings("ComponentNotRegistered")
public class InstallSdkAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    //TODO(pq): update to start a git workflow where possible.
    BrowserUtil.browse("https://flutter.io/setup/");
  }
}
