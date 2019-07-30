/*
 * Copyright  2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.Rule;
import org.junit.Test;

public class AndroidxTest {

  private static final String ANDROID_PROPERTIES =
    "org.gradle.jvmargs=-Xmx1536M\n";

  private static final String ANDROIDX_PROPERTIES =
    "org.gradle.jvmargs=-Xmx1536M\n" +
    "\n" +
    "android.useAndroidX=true\n" +
    "android.enableJetifier=true";

  private static final String NON_STANDARD_PROPERTIES =
    "# comment\n" +
    "android.useAndroidX=\\\n" +
    "  true\n";

  @Rule
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Test
  public void androidOldAppRecognized() {
    createTestHarness("android", null, ANDROID_PROPERTIES);
    assertFalse(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidxOldAppRecognized() {
    createTestHarness("android", null, ANDROIDX_PROPERTIES);
    assertTrue(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidxOldModuleRecognized() {
    createTestHarness(".android", null, ANDROIDX_PROPERTIES);
    assertTrue(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidOldPluginRecognized() {
    createTestHarness("example/android", null, ANDROID_PROPERTIES);
    assertFalse(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidxOldPluginRecognized() {
    createTestHarness("example/android", null, ANDROIDX_PROPERTIES);
    assertTrue(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidAppRecognized() {
    createTestHarness("android", FlutterProjectType.APP, ANDROID_PROPERTIES);
    assertFalse(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidxAppRecognized() {
    createTestHarness("android", FlutterProjectType.APP, ANDROIDX_PROPERTIES);
    assertTrue(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidxModuleRecognized() {
    createTestHarness(".android", FlutterProjectType.MODULE, ANDROIDX_PROPERTIES);
    assertTrue(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidPluginRecognized() {
    createTestHarness("example/android", FlutterProjectType.PLUGIN, ANDROID_PROPERTIES);
    assertFalse(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void androidxPluginRecognized() {
    createTestHarness("example/android", FlutterProjectType.PLUGIN, ANDROIDX_PROPERTIES);
    assertTrue(FlutterUtils.isAndroidxProject(fixture.getProject()));
  }

  @Test
  public void packageRecognized() {
    boolean result = FlutterUtils.isAndroidxProject(fixture.getProject());
    assertFalse(result);
  }

  @Test
  public void userEditsAreValid() {
    createTestHarness("android", FlutterProjectType.APP, NON_STANDARD_PROPERTIES);
    boolean result = FlutterUtils.isAndroidxProject(fixture.getProject());
    assertTrue(result);
  }

  private void createTestHarness(@NotNull String androidPath, @Nullable FlutterProjectType type, @NotNull String propertiesContent) {
    @SystemIndependent String basePath = fixture.getProject().getBasePath();
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(basePath);
    try {
      Testing.runOnDispatchThread(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            if (type != null) {
              VirtualFile metadataFile = projectDir.createChildData(this, ".metadata");
              String metadataContent = "project_type: ";
              switch (type) {
                case APP:
                  metadataContent += "app\n";
                  break;
                case MODULE:
                  metadataContent += "module\n";
                  break;
                case PACKAGE:
                  metadataContent += "package\n";
                  break;
                case PLUGIN:
                  metadataContent += "plugin\n";
                  break;
                case IMPORT:
                  fail();
                  break;
              }
              try (OutputStream out = new BufferedOutputStream(metadataFile.getOutputStream(this))) {
                out.write(metadataContent.getBytes(StandardCharsets.UTF_8));
              }
            }
            String[] dirs = androidPath.split("/");
            VirtualFile androidDir = projectDir;
            for (String dir : dirs) {
              androidDir = androidDir.createChildDirectory(this, dir);
            }
            assertNotEquals(androidDir, projectDir);
            VirtualFile propertiesFile = androidDir.createChildData(this, "gradle.properties");
            try (OutputStream out = new BufferedOutputStream(propertiesFile.getOutputStream(this))) {
              out.write(propertiesContent.getBytes(StandardCharsets.UTF_8));
            }
          }
          catch (IOException x) {
            fail(x.getMessage());
          }
        });
      });
    }
    catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
