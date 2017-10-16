/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.annotations.concurrency.GuardedBy;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class FlutterCreateState {
  private static final Logger LOG = Logger.getInstance(FlutterCreateState.class);

  @NotNull private final Project myProject;
  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  private boolean myCreateInProgress;

  public FlutterCreateState(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static FlutterCreateState getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterCreateState.class);
  }

  public boolean createStarted() {
    synchronized (myLock) {
      if (myCreateInProgress) {
        LOG.info(String.format("flutter create already in progress for project '%1$s'.", myProject.getName()));
        return false;
      }
      myCreateInProgress = true;
      return true;
    }
  }

  public void createEnded() {
    stopCreateInProgress();
  }

  public void createFailed(String message) {
    String msg = "flutter create failed";
    if (isNotEmpty(message)) {
      msg += String.format(": %1$s", message);
    }
    LOG.info(msg);
    stopCreateInProgress();
  }

  public boolean isCreateInProgress() {
    synchronized (myLock) {
      return myCreateInProgress;
    }
  }

  private void stopCreateInProgress() {
    synchronized (myLock) {
      myCreateInProgress = false;
    }
  }
}
