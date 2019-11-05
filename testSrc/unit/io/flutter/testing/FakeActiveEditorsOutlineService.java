/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import io.flutter.editor.ActiveEditorsOutlineService;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A fake implementation of the {@link ActiveEditorsOutlineService} that always returns a golden {@link FlutterOutline} from a file.
 */
public class FakeActiveEditorsOutlineService extends ActiveEditorsOutlineService {
  private FlutterOutline flutterOutline;

  public FakeActiveEditorsOutlineService(Project project, @NotNull String flutterOutlinePath) {
    super(project);
    loadOutline(flutterOutlinePath);
  }

  public void loadOutline(@NotNull String flutterOutlinePath) {
    String outlineContents;
    try {
      outlineContents = new String(Files.readAllBytes(Paths.get(flutterOutlinePath)));
    }
    catch (IOException e) {
      Assert.fail("Unable to load file " + flutterOutlinePath);
      e.printStackTrace();
      outlineContents = null;
    }
    flutterOutline = null;
    if (outlineContents != null) {
      flutterOutline = FlutterOutline.fromJson(new JsonParser().parse(outlineContents).getAsJsonObject());
    }
  }

  @Nullable
  @Override
  public FlutterOutline getOutline(String path) {
    return flutterOutline;
  }

  public static final String SIMPLE_TEST_PATH = "testData/sample_tests/test/simple_test.dart";
  public static final String SIMPLE_OUTLINE_PATH = "testData/sample_tests/test/simple_outline.txt";

  public static final String CUSTOM_TEST_PATH = "testData/sample_tests/test/custom_test.dart";
  public static final String CUSTOM_OUTLINE_PATH = "testData/sample_tests/test/custom_outline.txt";

  public static final String NO_TESTS_PATH = "testData/sample_tests/test/no_tests.dart";
  public static final String NO_TESTS_OUTLINE_PATH = "testData/sample_tests/test/no_tests_outline.txt";
}
