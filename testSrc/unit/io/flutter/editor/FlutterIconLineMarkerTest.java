/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartRecursiveVisitor;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class FlutterIconLineMarkerTest extends AbstractDartElementTest {

  @Test
  public void locatesIconsReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Icons.access_alarm; }", "Icons", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesIconCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier =
        setUpDartElement("main() { IconData(0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "IconData");
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstIconCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier =
        setUpDartElement("main() { const IconData(0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesCupertinoIconsReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { CupertinoIcons.book; }", "CupertinoIcons", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesCupertinoIconsReferenceWithComment() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { CupertinoIcons . /* a book */ book; }", "CupertinoIcons", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesIconCtorWithWhitespace() throws Exception {
    run(() -> {
      final PsiElement testIdentifier =
        setUpDartElement("main() { IconData ( 0xe190 ); }", "IconData", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "IconData");
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstIconCtorWithLineEndComment() throws Exception {
    run(() -> {
      final PsiElement testIdentifier =
        setUpDartElement("main() { const IconData // comment\n ( 0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier);
      assertNotNull(marker);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assertNotNull(element);
    });
  }

}
