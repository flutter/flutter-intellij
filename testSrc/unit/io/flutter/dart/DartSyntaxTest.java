/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;


import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.AbstractDartElementTest;
import org.junit.Test;

import static org.junit.Assert.*;

public class DartSyntaxTest extends AbstractDartElementTest {

  @Test
  public void isTestCall() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { test('my first test', () {} ); }", "test", LeafPsiElement.class);
      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(testIdentifier, "test");
      assert call != null;
      assertTrue(DartSyntax.isCallToFunctionNamed(call, "test"));
    });
  }

  @Test
  public void isMainFunctionDeclaration() throws Exception {
    run(() -> {
      final PsiElement mainIdentifier = setUpDartElement("main() { test('my first test', () {} ); }", "main", LeafPsiElement.class);
      final PsiElement main =
        PsiTreeUtil.findFirstParent(mainIdentifier, element -> element instanceof DartFunctionDeclarationWithBodyOrNative);
      assertTrue(DartSyntax.isMainFunctionDeclaration(main));
    });
  }

  @Test
  public void shouldFindEnclosingFunctionCall() throws Exception {
    run(() -> {
      final PsiElement helloElt = setUpDartElement("main() { test(\"hello\"); }", "hello", LeafPsiElement.class);

      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(helloElt, "test");
      assertNotNull("findEnclosingFunctionCall() didn't find enclosing function call", call);
    });
  }

  @Test
  public void shouldGetFirstArgumentFromFunctionCall() throws Exception {
    run(() -> {
      final PsiElement helloElt = setUpDartElement("main() { test(\"hello\"); }", "hello", LeafPsiElement.class);

      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(helloElt, "test");
      assertNotNull(call);
      final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
      assertNotNull("getSyntax() didn't return first argument", lit);
    });
  }

  @Test
  public void shouldUnquoteStringLiteral() throws Exception {
    run(() -> {
      final DartStringLiteralExpression quoted = setUpDartElement("var x = \"hello\";", "\"hello\"", DartStringLiteralExpression.class);
      final String unquoted = DartSyntax.unquote(quoted);
      assertEquals("hello", unquoted);
    });
  }
}
