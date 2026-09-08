/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.ide;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.lang.dart.DartLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from similar class in the Dart plugin.
 */
public class DartTestUtils {

  public static final String BASE_TEST_DATA_PATH = findTestDataPath();
 
  /**
   * Creates the syntax tree for a Dart file at a specific path and returns the innermost element with the given text.
   */
  @NotNull
  public static <E extends PsiElement> E setUpDartElement(@Nullable String filePath,
                                                          @NotNull String fileText,
                                                          @NotNull String elementText,
                                                          @NotNull Class<E> expectedClass,
                                                          @NotNull Project project) {
    final int offset = fileText.indexOf(elementText);
    if (offset < 0) {
      throw new IllegalArgumentException("'" + elementText + "' not found in '" + fileText + "'");
    }

    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    assert factory != null;
    final PsiFile file;
    if (filePath != null) {
      file = factory.createFileFromText(filePath, DartLanguage.INSTANCE, fileText);
    }
    else {
      file = factory.createFileFromText(DartLanguage.INSTANCE, fileText);
    }

    assert file != null;
    PsiElement elt = file.findElementAt(offset);
    while (elt != null) {
      if (elementText.equals(elt.getText())) {
        return expectedClass.cast(elt);
      }
      elt = elt.getParent();
    }

    throw new RuntimeException("unable to find element with text: " + elementText);
  }

  private static String findTestDataPath() {
    return "testData/sdk";
  }
}
