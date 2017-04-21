/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FlutterSdkTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Rule
  public final TestDir tmp = new TestDir();

  @Test
  public void canReadFlutterSdkFromUnopenedProjectInExampleDirectory() throws Exception {
    final VirtualFile projectDir = tmp.ensureDir("myproject");
    tmp.ensureDir("myproject/.idea/libraries");
    tmp.writeFile("myproject/.idea/libraries/Dart_SDK.xml",
                  "<component name=\"libraryTable\">\n" +
                  "  <library name=\"Dart SDK\">\n" +
                  "    <CLASSES>\n" +
                  "      <root url=\"file://$PROJECT_DIR$/../../bin/cache/dart-sdk/lib/core\" />\n" +
                  "      <root url=\"file://$PROJECT_DIR$/../../bin/cache/dart-sdk/lib/something_else\" />\n" +
                  "    </CLASSES>\n" +
                  "    <JAVADOC />\n" +
                  "    <SOURCES />\n" +
                  "  </library>\n" +
                  "</component>");
    final FlutterSdk sdk = FlutterSdk.getForProjectDir(projectDir);
    assertNotNull("getForProjectDir() didn't find Flutter SDK", sdk);
    assertEquals(projectDir.getParent().getParent().getPath(), sdk.getHomePath());
  }
}
