/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;


import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import io.flutter.ide.DartTestUtils;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;

import java.util.Objects;

public class AbstractDartElementTest {
  @Rule
  public final ProjectFixture<CodeInsightTestFixture> fixture = Testing.makeCodeInsightModule();

  protected void run(@NotNull Testing.RunnableThatThrows callback) throws Exception {
    Testing.runOnDispatchThread(callback);
  }

  /**
   * Creates the syntax tree for a Dart file at a specific path and returns the innermost element with the given text.
   */
  @NotNull
  protected <E extends PsiElement> E setUpDartElement(String filePath, String fileText, String elementText, Class<E> expectedClass) {
    assert fileText != null && elementText != null && expectedClass != null && fixture.getProject() != null;
    return DartTestUtils.setUpDartElement(filePath, fileText, elementText, expectedClass, Objects.requireNonNull(fixture.getProject()));
  }

  /**
   * Creates the syntax tree for a Dart file and returns the innermost element with the given text.
   */
  @NotNull
  protected <E extends PsiElement> E setUpDartElement(String fileText, String elementText, Class<E> expectedClass) {
    assert fileText != null && elementText != null && expectedClass != null && fixture.getProject() != null;
    return DartTestUtils.setUpDartElement(null, fileText, elementText, expectedClass, Objects.requireNonNull(fixture.getProject()));
  }
}
