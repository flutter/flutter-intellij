/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

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

public class AndroidModuleLibraryType extends LibraryType<AndroidModuleLibraryProperties> {
  public static final String LIBRARY_NAME = "Android Libraries";

  public static final PersistentLibraryKind<AndroidModuleLibraryProperties> LIBRARY_KIND =
    new PersistentLibraryKind<AndroidModuleLibraryProperties>("AndroidModuleLibraryType") {
      @Override
      @NotNull
      public AndroidModuleLibraryProperties createDefaultProperties() {
        return new AndroidModuleLibraryProperties();
      }
    };

  protected AndroidModuleLibraryType() {
    super(LIBRARY_KIND);
  }

  @Nullable
  @Override
  public String getCreateActionName() {
    return null;
  }

  @Nullable
  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent,
                                                  @Nullable VirtualFile contextDirectory,
                                                  @NotNull Project project) {
    return null;
  }

  @Nullable
  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<AndroidModuleLibraryProperties> editorComponent) {
    return null;
  }

  @Override
  @Nullable
  public Icon getIcon(@Nullable AndroidModuleLibraryProperties properties) {
    return FlutterIcons.Flutter;
  }
}
