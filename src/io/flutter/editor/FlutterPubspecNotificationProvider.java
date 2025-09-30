/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
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
import java.util.function.Function;

public final class FlutterPubspecNotificationProvider implements EditorNotificationProvider {
  @Nullable
  @Override
  public Function<? super FileEditor, ? extends JComponent> collectNotificationData(@NotNull Project project,
                                                                                                       @NotNull VirtualFile file) {
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

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      return null;
    }

    return fileEditor -> new FlutterPubspecActionsPanel(fileEditor, project, flutterSdk);
  }

  static class FlutterPubspecActionsPanel extends EditorNotificationPanel {
    @NotNull final VirtualFile myFile;
    @NotNull final Project myProject;
    @NotNull final FlutterSdk myFlutterSdk;

    FlutterPubspecActionsPanel(@NotNull FileEditor fileEditor, @NotNull Project project, @NotNull FlutterSdk flutterSdk) {
      super(UIUtils.getEditorNotificationBackgroundColor());

      myFile = fileEditor.getFile();
      myProject = project;
      myFlutterSdk = flutterSdk;

      icon(FlutterIcons.Flutter);
      text("Flutter commands");

      // "flutter.pub.get"
      HyperlinkLabel label = createActionLabel("Pub get", () -> runPubGet(false));
      label.setToolTipText("Install referenced packages");

      // "flutter.pub.upgrade"
      label = createActionLabel("Pub upgrade", () -> runPubGet(true));
      label.setToolTipText("Upgrade referenced packages to the latest versions");

     // "flutter.pub.outdated"
      label = createActionLabel("Pub outdated", this::runPubOutdated);
      label.setToolTipText("Analyze packages to determine which ones can be upgraded");

      if (myLinksPanel != null) {
        myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      }
      label = createActionLabel("Flutter doctor", "flutter.doctor");
      label.setToolTipText("Validate installed tools and their versions");
    }

    private void runPubGet(boolean upgrade) {
      final PubRoot root = PubRoot.forDirectory(myFile.getParent());
      if (root != null) {
        if (!upgrade) {
          myFlutterSdk.startPubGet(root, myProject);
        }
        else {
          myFlutterSdk.startPubUpgrade(root, myProject);
        }
      }
    }

    private void runPubOutdated() {
      final PubRoot root = PubRoot.forDirectory(myFile.getParent());
      if (root != null) {
        myFlutterSdk.startPubOutdated(root, myProject);
      }
    }
  }
}
