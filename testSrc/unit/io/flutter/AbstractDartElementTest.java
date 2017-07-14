/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;


import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.lang.dart.DartLanguage;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;

public class AbstractDartElementTest {
  @Rule
  public final ProjectFixture fixture = Testing.makeCodeInsightModule();

  protected void run(Testing.RunnableThatThrows callback) throws Exception {
    Testing.runOnDispatchThread(callback);
  }

  /**
   * Creates the syntax tree for a Dart file and returns the innermost element with the given text.
   */
  @NotNull
  protected <E extends PsiElement> E setUpDartElement(String fileText, String elementText, Class<E> expectedClass) {
    final int offset = fileText.indexOf(elementText);
    if (offset < 0) {
      throw new IllegalArgumentException("'" + elementText + "' not found in '" + fileText + "'");
    }

    final PsiFile file = PsiFileFactory.getInstance(fixture.getProject())
      .createFileFromText(DartLanguage.INSTANCE, fileText);

    PsiElement elt = file.findElementAt(offset);
    while (elt != null) {
      if (elementText.equals(elt.getText())) {
        return expectedClass.cast(elt);
      }
      elt = elt.getParent();
    }

    throw new RuntimeException("unable to find element with text: " + elementText);
  }
}
