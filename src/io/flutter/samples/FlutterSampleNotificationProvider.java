/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlutterSampleNotificationProvider extends EditorNotifications.Provider<JPanel> implements DumbAware {
  private static final Key<JPanel> KEY = Key.create("flutter.sample");

  @NotNull private final Project project;

  public FlutterSampleNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  @Override
  public Key<JPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public JPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor editor) {
    final List<FlutterSample> samples = getSamplesForFile(file);
    return !samples.isEmpty() ? new FlutterSampleActionsPanel(samples, project) : null;
  }

  private FlutterSdk getSdk() {
    return FlutterSdk.getFlutterSdk(project);
  }

  private List<FlutterSample> getSamplesForFile(@NotNull VirtualFile file) {
    final FlutterSdk sdk = getSdk();
    if (sdk == null) {
      return Collections.emptyList();
    }

    final String filePath = FileUtil.normalize(file.getPath());
    final String pathSuffix = FileUtil.normalize(sdk.getHomePath()) + "/packages/flutter/";

    final List<FlutterSample> samples = new ArrayList<>();
    for (FlutterSample sample : sdk.getSamples()) {
      final String samplePath = pathSuffix + sample.getSourcePath();
      if (filePath.equals(samplePath)) {
        samples.add(sample);
      }
    }

    return samples;
  }
}

