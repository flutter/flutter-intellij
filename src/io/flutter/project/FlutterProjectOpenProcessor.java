/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.execution.ExecutionException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.ProjectOpenActivity;
import io.flutter.actions.FlutterPackagesGetAction;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class FlutterProjectOpenProcessor extends ProjectOpenProcessor {

  private static final Logger LOG = Logger.getInstance(FlutterProjectOpenProcessor.class.getName());

  private static void handleError(@NotNull Exception e) {
    FlutterMessages.showError("Error opening", e.getMessage());
  }

  @Override
  public String getName() {
    return FlutterBundle.message("flutter.module.name");
  }

  @Override
  public Icon getIcon() {
    return FlutterIcons.Flutter;
  }

  @Override
  public boolean canOpenProject(@Nullable VirtualFile file) {
    if (file == null) return false;
    final PubRoot root = PubRoot.forDirectoryWithRefresh(file);
    return root != null && root.declaresFlutter();
  }

  /**
   * Runs when a project is opened by selecting the project directly, possibly for import.
   * <p>
   * Doesn't run when a project is opened via recent projects menu (and so on). Actions that
   * should run every time a project is opened should be in
   * {@link ProjectOpenActivity} or {@link io.flutter.FlutterInitializer}.
   */
  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile file, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {

    // Delegate opening to the platform open processor.
    final ProjectOpenProcessor importProvider = getDelegateImportProvider(file);
    if (importProvider == null) {
      return null;
    }

    final Project project = importProvider.doOpenProject(file, projectToClose, forceOpenInNewFrame);

    // Once open, process.
    doPostOpenProcessing(project);

    return project;
  }

  private void doPostOpenProcessing(@Nullable Project project) {
    if (project == null) {
      return;
    }

    if (!FlutterModuleUtils.hasFlutterModule(project)) {
      if (FlutterModuleUtils.usesFlutter(project)) {
        final List<Module> modules = FlutterModuleUtils.findModulesWithFlutterContents(project);
        if (modules.isEmpty()) {
          LOG.warn(MessageFormat.format("No module found for {0}", project.getName()));
          return;
        }
        else if (modules.size() > 1) {
          LOG.warn(MessageFormat.format("{0} contains {1} modules.", project.getName(), modules.size()));
        }

        final Module module = modules.get(0);
        final PubRoot root = PubRoot.forModuleWithRefresh(module);
        final VirtualFile main = root == null ? null : root.getLibMain();
        if (main != null) {
          FlutterModuleUtils.createRunConfig(project, main);
        }

        FlutterModuleUtils.setFlutterModuleAndReload(module, project);
        return;
      }
    }

    try {
      final PubRoot root = PubRoot.forProjectWithRefresh(project);
      if (root != null && root.getPackages() == null) {
        final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
        if (sdk != null) {
          sdk.startPackagesGet(root, project);
        }
      }
      else {
        if (root != null && !root.hasUpToDatePackages()) {
          Notifications.Bus.notify(new PackagesOutOfDateNotification(project));
        }
      }
    }
    catch (ExecutionException e) {
      handleError(e);
    }
  }

  @Nullable
  private ProjectOpenProcessor getDelegateImportProvider(@Nullable VirtualFile file) {
    return Arrays.stream(Extensions.getExtensions(EXTENSION_POINT_NAME)).filter(
      processor -> processor.canOpenProject(file) && !Objects.equals(processor.getName(), getName())
    ).findFirst().orElse(null);
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return true;
  }

  private static class PackagesOutOfDateNotification extends Notification {

    @NotNull
    private final Project myProject;

    public PackagesOutOfDateNotification(@NotNull Project project) {
      super("Flutter Packages", FlutterIcons.Flutter, "Flutter packages get.",
            null, "The pubspec.yaml file has been modified since " +
                  "the last time 'flutter packages get' was run.",
            NotificationType.INFORMATION, null);

      myProject = project;

      addAction(new AnAction("Run 'flutter packages get'") {
        @Override
        public void actionPerformed(AnActionEvent event) {
          expire();

          final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
          if (sdk == null) {
            return;
          }

          final PubRoot root = PubRoot.forProjectWithRefresh(project);
          if (root == null) {
            return;
          }

          try {
            // TODO(skybrian) analytics?
            sdk.startPackagesGet(root, project);
          }
          catch (ExecutionException e) {
            handleError(e);
          }
        }
      });
    }
  }
}
