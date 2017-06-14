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

  private static abstract class InstallAction {
    abstract void perform();

    abstract String getLinkText();
  }

  private static class ViewDocsAction extends InstallAction {
    @Override
    void perform() {
      BrowserUtil.browse("https://flutter.io/setup/");
    }

    @Override
    String getLinkText() {
      return "View setup docs…";
    }
  }

  private final InstallAction myInstallAction;

  public InstallSdkAction() {
    myInstallAction = createInstallAction();
  }

  private static InstallAction createInstallAction() {
    //TODO(pq): update to start a git workflow where possible.
    return new ViewDocsAction();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myInstallAction.perform();
  }

  public String getLinkText() {
    return myInstallAction.getLinkText();
  }
}
