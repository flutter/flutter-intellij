/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.HyperlinkLabel;
import icons.FlutterIcons;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterPubspecNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.pubspec");

  @NotNull private final Project project;

  public FlutterPubspecNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor editor) {
    if (!file.isInLocalFileSystem()) {
      return null;
    }

    // The Bazel workspace condition is handled by this check, pubspecs are not used.
    // TODO(jwren) Add additional check to exit if this is a Flutter Bazel workspace (in the event that some pubspec.yaml file is opened).
    if (!PubRoot.isPubspec(file)) {
      return null;
    }

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null || !FlutterModuleUtils.isFlutterModule(module)) {
      return null;
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    return new FlutterPubspecActionsPanel(file);
  }

  static class FlutterPubspecActionsPanel extends EditorNotificationPanel {
    @NotNull final VirtualFile myFile;

    FlutterPubspecActionsPanel(@NotNull VirtualFile file) {
      super(EditorColors.GUTTER_BACKGROUND);

      myFile = file;

      icon(FlutterIcons.Flutter);
      text("Flutter commands");

      // "flutter.packages.get"
      HyperlinkLabel label = createActionLabel("Packages get", () -> runPackagesGet(false));
      label.setToolTipText("Install referenced packages");

      // "flutter.packages.upgrade"
      label = createActionLabel("Packages upgrade", () -> runPackagesGet(true));
      label.setToolTipText("Upgrade referenced packages to the latest versions");

      myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      label = createActionLabel("Flutter upgrade", "flutter.upgrade");
      label.setToolTipText("Upgrade the Flutter framework to the latest version");

      myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      label = createActionLabel("Flutter doctor", "flutter.doctor");
      label.setToolTipText("Validate installed tools and their versions");
    }

    private void runPackagesGet(boolean upgrade) {
      // We can assert below since we know we're on a pubspec.yaml file in a project, with a valid Flutter SDK.
      final Project project = ProjectLocator.getInstance().guessProjectForFile(myFile);
      assert (project != null);
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      assert (sdk != null);

      final PubRoot root = PubRoot.forDirectory(myFile.getParent());
      if (root != null) {
        if (!upgrade) {
          sdk.startPackagesGet(root, project);
        }
        else {
          sdk.startPackagesUpgrade(root, project);
        }
      }
    }
  }
}
