/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;


import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DartSyntaxTest {

  @Rule
  public final ProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void isTestCall() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { test('my first test', () {} ); }", "test", LeafPsiElement.class);
      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(testIdentifier, "test");
      assertTrue(DartSyntax.isTestCall(call));
    });
  }

  @Test
  public void shouldFindEnclosingFunctionCall() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final PsiElement helloElt = setUpDartElement("main() { test(\"hello\"); }", "hello", LeafPsiElement.class);

      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(helloElt, "test");
      assertNotNull("findEnclosingFunctionCall() didn't find enclosing function call", call);
    });
  }

  @Test
  public void shouldGetFirstArgumentFromFunctionCall() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final PsiElement helloElt = setUpDartElement("main() { test(\"hello\"); }", "hello", LeafPsiElement.class);

      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(helloElt, "test");
      assertNotNull(call);
      final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
      assertNotNull("getSyntax() didn't return first argument", lit);
    });
  }

  @Test
  public void shouldUnquoteStringLiteral() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final DartStringLiteralExpression quoted = setUpDartElement("var x = \"hello\";", "\"hello\"", DartStringLiteralExpression.class);
      final String unquoted = DartSyntax.unquote(quoted);
      assertEquals("hello", unquoted);
    });
  }

  /**
   * Creates the syntax tree for a Dart file and returns the innermost element with the given text.
   */
  @NotNull
  private <E extends PsiElement> E setUpDartElement(String fileText, String elementText, Class<E> expectedClass) {
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
