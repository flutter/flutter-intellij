/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.vfs.VirtualFile;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterPluginLibraryType extends LibraryType<FlutterPluginLibraryProperties> {
  public static final String FLUTTER_PLUGINS_LIBRARY_NAME = "Flutter Plugins";

  public static final PersistentLibraryKind<FlutterPluginLibraryProperties> LIBRARY_KIND =
    new PersistentLibraryKind<FlutterPluginLibraryProperties>("FlutterPluginsLibraryType") {
      @Override
      @NotNull
      public FlutterPluginLibraryProperties createDefaultProperties() {
        return new FlutterPluginLibraryProperties();
      }
    };

  protected FlutterPluginLibraryType() {
    super(LIBRARY_KIND);
  }

  @Nullable
  @Override
  public String getCreateActionName() {
    return null;
  }

  @Nullable
  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent component, @Nullable VirtualFile file, @NotNull Project project) {
    return null;
  }

  @Nullable
  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<FlutterPluginLibraryProperties> component) {
    return null;
  }

  @Nullable
  public Icon getIcon(@Nullable FlutterPluginLibraryProperties properties) {
    return FlutterIcons.Flutter;
  }
}
