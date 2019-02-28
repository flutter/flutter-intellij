/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative;
import io.flutter.AbstractDartElementTest;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestConfigUtilsTest extends AbstractDartElementTest {

  @Test
  public void mainTestCall() throws Exception {
    run(() -> {
      final PsiElement mainIdentifier = setUpDartElement("main() { test('my first test', () {} ); }", "main", LeafPsiElement.class);
      final PsiElement main =
        PsiTreeUtil.findFirstParent(mainIdentifier, element -> element instanceof DartFunctionDeclarationWithBodyOrNative);
      assert main != null;
      assertTrue(TestConfigUtils.getInstance().isMainFunctionDeclarationWithTests(main));
    });
  }

  @Test
  public void mainTestCall_negative() throws Exception {
    run(() -> {
      final PsiElement mainIdentifier = setUpDartElement("main() { print('no tests here!'); }", "main", LeafPsiElement.class);
      final PsiElement main =
        PsiTreeUtil.findFirstParent(mainIdentifier, element -> element instanceof DartFunctionDeclarationWithBodyOrNative);
      assert main != null;
      assertFalse(TestConfigUtils.getInstance().isMainFunctionDeclarationWithTests(main));
    });
  }

}
