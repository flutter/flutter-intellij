/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class ProgressHelper {
  final Project myProject;
  final List<String> myTasks = new ArrayList<>();

  private Task.Backgroundable myTask;

  ProgressHelper(@NotNull Project project) {
    this.myProject = project;
  }

  /**
   * Start a progress task.
   *
   * @param log the title of the progress task
   */
  public void start(String log) {
    synchronized (myTasks) {
      myTasks.add(log);
      myTasks.notifyAll();

      if (myTask == null) {
        myTask = new Task.Backgroundable(myProject, log, false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setText(log);

            synchronized (myTasks) {
              while (!myTasks.isEmpty()) {
                indicator.setText(myTasks.get(myTasks.size() - 1));

                try {
                  myTasks.wait();
                }
                catch (InterruptedException e) {
                  // ignore

                }
                myTask = null;
              }
            }
          }
        };

        ApplicationManager.getApplication().invokeLater(() -> {
          synchronized (myTasks) {
            if (myTask != null) {
              ProgressManager.getInstance().run(myTask);
            }
          }
        });
      }
    }
  }

  /**
   * Notify that a progress task has finished.
   */
  public void done() {
    synchronized (myTasks) {
      if (!myTasks.isEmpty()) {
        myTasks.remove(myTasks.size() - 1);
        myTasks.notifyAll();
      }
    }
  }

  /**
   * Finish any outstanding progress tasks.
   */
  public void cancel() {
    synchronized (myTasks) {
      myTasks.clear();
      myTasks.notifyAll();
    }
  }
}
