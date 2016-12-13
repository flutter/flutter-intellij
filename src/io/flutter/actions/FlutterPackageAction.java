/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Flutter Package actions have smart enablement to allow for appropriate contribution to the Editor popup
 * for `flutter.yaml` files.
 */
public abstract class FlutterPackageAction extends FlutterSdkAction {

  private static boolean isFlutterYamlPopup(@NotNull AnActionEvent e) {
    if (ActionPlaces.EDITOR_POPUP.equals(e.getPlace())) {
      final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (file != null) {
        return FlutterConstants.FLUTTER_YAML.equalsIgnoreCase(file.getName());
      }
    }
    return false;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {

    final Presentation presentation = e.getPresentation();

    // Enabled in main menu and action search.
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
      return;
    }

    // Hidden elsewhere.
    presentation.setEnabled(false);
    presentation.setVisible(false);
  }
}
