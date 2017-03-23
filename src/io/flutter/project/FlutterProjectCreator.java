/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

public class FlutterProjectCreator {

  private static final Logger LOG = Logger.getInstance(FlutterProjectCreator.class);

  public static void setupProject(@NotNull Project project, ModifiableRootModel model, VirtualFile baseDir, String flutterSdkPath)
    throws ConfigurationException {
    // TODO(devoncarew): Store the flutterSdkPath info (in the project? module?).
    final FlutterSdk sdk = FlutterSdk.forPath(flutterSdkPath);
    if (sdk == null) {
      throw new ConfigurationException(flutterSdkPath + " is not a valid Flutter SDK");
    }

    model.addContentEntry(baseDir);
    createProjectFiles(model, baseDir, sdk);
  }

  public static void createProjectFiles(@NotNull final ModifiableRootModel model,
                                        @NotNull final VirtualFile baseDir,
                                        @NotNull final FlutterSdk sdk) {
    // Create files.
    try {
      sdk.run(FlutterSdk.Command.CREATE, model.getModule(), baseDir, null, baseDir.getPath());
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }
  }
}
