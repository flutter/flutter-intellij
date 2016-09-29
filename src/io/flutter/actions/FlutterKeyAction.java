/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcessZ;
import org.jetbrains.annotations.Nullable;

public abstract class FlutterKeyAction extends DumbAwareAction {
  public static final String RELOAD_DISPLAY_ID = "Flutter Commands";

  /**
   * Find an active Dart VM debug process and get it's observatory connector.
   * <p>
   * TODO: make this more robust in the face of multiple concurrent debug sessions.
   */
  static
  @Nullable
  ObservatoryConnector findConnector() {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final XDebuggerManager manager = XDebuggerManager.getInstance(project);
      final XDebugSession session = manager.getCurrentSession();
      if (session != null) {
        final XDebugProcess debugProcess = session.getDebugProcess();
        if (debugProcess instanceof DartVmServiceDebugProcessZ) {
          return ((DartVmServiceDebugProcessZ)debugProcess).getConnector();
        }
      }
    }
    return null;
  }

}
