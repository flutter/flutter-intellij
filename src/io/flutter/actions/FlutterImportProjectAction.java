/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.ArrayUtil;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterImportProjectAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(FlutterImportProjectAction.class);

  @NotNull
  private final AnAction delegateAction;
  @Nullable
  private final Icon icon;

  public FlutterImportProjectAction(@NotNull AnAction delegateAction) {
    this(delegateAction, null);
  }

  public FlutterImportProjectAction(@NotNull AnAction delegateAction, @Nullable Icon icon) {
    this.delegateAction = delegateAction;
    this.icon = icon;
  }

  @Override
  public void update(AnActionEvent e) {
    // The action icon is lazily loaded and forcing a load in the Welcome Page does not work so if specified we
    // set it ourselves.
    if (icon != null) {
      e.getPresentation().setIcon(icon);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(false);
    final Project project = e.getData(CommonDataKeys.PROJECT);

    FileChooser
      .chooseFiles(descriptor, project, null, files -> {
        final VirtualFile selection = files.get(0);
        if (FlutterUtils.isFlutterProjectRoot(selection)) {
          PlatformProjectOpenProcessor.getInstance().doOpenProject(selection, project, false);
        }
        else {
          if (delegateAction instanceof ImportModuleAction) {
            try {
              final AddModuleWizard wizard = ImportModuleAction.createImportWizard(project, null, selection, ArrayUtil
                .toObjectArray(ImportModuleAction.getProviders(project), ProjectImportProvider.class));
              if (wizard != null && (wizard.getStepCount() <= 0 || wizard.showAndGet())) {
                ImportModuleAction.createFromWizard(project, wizard);
                return;
              }
            }
            catch (Throwable th) {
              // Be extra defensive in the off chance that something goes sideways in our delegate to the ImportModuleAction.
              LOG.error(th);
            }
            // Fall back on delegate action.
            delegateAction.actionPerformed(e);
          }
        }
      });
  }
}
