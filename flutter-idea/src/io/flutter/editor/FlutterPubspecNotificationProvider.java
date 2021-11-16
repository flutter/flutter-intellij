/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.HyperlinkLabel;
import icons.FlutterIcons;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.UIUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterPubspecNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.pubspec");

  public FlutterPubspecNotificationProvider(@NotNull Project project) {
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    // We only show this notification inside of local pubspec files.
    if (!PubRoot.isPubspec(file) || !file.isInLocalFileSystem()) {
      return null;
    }

    // If the user has opted out of using pub in a project with both bazel rules and pub rules,
    // then we will default to bazel instead of pub.
    if (WorkspaceCache.getInstance(project).isBazel()) {
      return null;
    }

    // Check that this pubspec file declares flutter
    if (!FlutterUtils.declaresFlutter(file)) {
      return null;
    }

    if (FlutterSdk.getFlutterSdk(project) == null) {
      return null;
    }

    return new FlutterPubspecActionsPanel(project, file);
  }

  static class FlutterPubspecActionsPanel extends EditorNotificationPanel {
    @NotNull final Project project;
    @NotNull final VirtualFile myFile;

    FlutterPubspecActionsPanel(@NotNull Project project, @NotNull VirtualFile file) {
      super(UIUtils.getEditorNotificationBackgroundColor());

      this.project = project;
      myFile = file;

      icon(FlutterIcons.Flutter);
      text("Flutter commands");

      // "flutter.pub.get"
      HyperlinkLabel label = createActionLabel("Pub get", () -> runPubGet(false));
      label.setToolTipText("Install referenced packages");

      // "flutter.pub.upgrade"
      label = createActionLabel("Pub upgrade", () -> runPubGet(true));
      label.setToolTipText("Upgrade referenced packages to the latest versions");

      // If the SDK is the right version, add a 'flutter pub outdated' command.
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk != null && sdk.getVersion().isPubOutdatedSupported()) {
        // "flutter.pub.outdated"
        label = createActionLabel("Pub outdated", this::runPubOutdated);
        label.setToolTipText("Analyze packages to determine which ones can be upgraded");
      }

      myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      label = createActionLabel("Flutter doctor", "flutter.doctor");
      label.setToolTipText("Validate installed tools and their versions");
    }

    private void runPubGet(boolean upgrade) {
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        return;
      }

      final PubRoot root = PubRoot.forDirectory(myFile.getParent());
      if (root != null) {
        if (!upgrade) {
          sdk.startPubGet(root, project);
        }
        else {
          sdk.startPubUpgrade(root, project);
        }
      }
    }

    private void runPubOutdated() {
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        return;
      }

      final PubRoot root = PubRoot.forDirectory(myFile.getParent());
      if (root != null) {
        sdk.startPubOutdated(root, project);
      }
    }
  }
}
