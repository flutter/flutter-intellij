/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.ModuleTemplateGalleryEntry;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import icons.FlutterIcons;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;

public class FlutterDescriptionProvider implements ModuleDescriptionProvider {
  private static final Logger LOG = Logger.getInstance(FlutterDescriptionProvider.class.getName());
  private static final String BUNDLED_TEMPLATE_PATH = "/flutter-studio/templates";
  private static final String[] DEVELOPMENT_TEMPLATE_PATHS = {"/out/production/flutter-studio/templates"};
  private static final String GRADLE_PROJECTS_PATH = "/gradle-projects";

  private static File[] getFlutterModuleTemplateFiles() {
    File root = getTemplateRootFolder();
    if (root == null) {
      return new File[0];
    }
    return root.listFiles();
  }

  /**
   * Adapted from com.android.tools.idea.templates.TemplateManager.
   *
   * @return the root folder containing templates
   */
  @Nullable
  private static File getTemplateRootFolder() {
    String homePath = toSystemIndependentName(PathManager.getHomePath());
    // Release build?
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(homePath + BUNDLED_TEMPLATE_PATH));
    if (root == null) {
      // Development build?
      for (String path : DEVELOPMENT_TEMPLATE_PATHS) {
        root = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(homePath + path));

        if (root != null) {
          break;
        }
      }
    }
    if (root != null) {
      File rootFile = VfsUtilCore.virtualToIoFile(root);
      if (TemplateManager.templateRootIsValid(rootFile)) {
        return new File(rootFile, GRADLE_PROJECTS_PATH);
      }
    }
    return null;
  }

  @Override
  public Collection<? extends ModuleGalleryEntry> getDescriptions() {
    ArrayList<ModuleTemplateGalleryEntry> res = new ArrayList<>();

    TemplateManager manager = TemplateManager.getInstance();
    File[] applicationTemplates = getFlutterModuleTemplateFiles();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }

      int minSdk = metadata.getMinSdk();
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor.equals(FormFactor.MOBILE)) {
        res.add(new FlutterModuleTemplateGalleryEntry(templateFile, formFactor, minSdk, false,
                                                      FlutterIcons.Flutter_13_2x,
                                                      "Flutter",
                                                      metadata.getTitle()));
      }
    }
    return res;
  }
}
