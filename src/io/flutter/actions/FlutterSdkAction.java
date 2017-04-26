/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for Flutter commands.
 * <p>
 * In general actions are executed via {@link #actionPerformed(AnActionEvent)} and
 * send analytics. In case an action is performed and analytics should not be
 * collected, prefer {@link #perform(FlutterSdk, Project, AnActionEvent, boolean)}.
 */
public abstract class FlutterSdkAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(FlutterSdkAction.class);

  @Nullable
  public static Pair<Module, VirtualFile> getModuleAndPubspecYamlFile(@NotNull final Project project, @Nullable final AnActionEvent e)
    throws ExecutionException {

    Module module = e == null ? null : LangDataKeys.MODULE.getData(e.getDataContext());
    final PsiFile psiFile = e == null ? null : CommonDataKeys.PSI_FILE.getData(e.getDataContext());

    PubRoot root = psiFile == null ? null : PubRoot.forPsiFile(psiFile);
    if (root == null) root = PubRoot.forProjectWithRefresh(project);
    if (root == null && module != null) root = PubRoot.forModuleWithRefresh(module);

    if (module == null && root != null) {
      module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(root.getPubspec());
    }

    return root == null ? null : Pair.create(module, root.getPubspec());
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = DumbAwareAction.getEventProject(event);
    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;
    if (sdk != null) {
      try {
        perform(sdk, project, event, true);
      }
      catch (ExecutionException e) {
        FlutterMessages.showError(
          FlutterBundle.message("flutter.command.exception.title"),
          FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
        LOG.warn(e);
      }
    }
    else {
      final int response = FlutterMessages.showDialog(project, FlutterBundle.message("flutter.sdk.notAvailable.message"),
                                                      FlutterBundle.message("flutter.sdk.notAvailable.title"),
                                                      new String[]{"Yes, configure", "No, thanks"}, -1);
      if (response == 0) {
        FlutterUtils.openFlutterSettings(project);
      }
    }
  }

  public final void perform(@NotNull FlutterSdk sdk,
                            @NotNull Project project,
                            @Nullable AnActionEvent event,
                            @SuppressWarnings("SameParameterValue") boolean sendAnalytics)
    throws ExecutionException {
    if (sendAnalytics) {
      FlutterInitializer.sendAnalyticsAction(this);
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    perform(sdk, project, event);
  }

  public abstract void perform(@NotNull FlutterSdk sdk, @NotNull Project project, AnActionEvent event) throws ExecutionException;
}
