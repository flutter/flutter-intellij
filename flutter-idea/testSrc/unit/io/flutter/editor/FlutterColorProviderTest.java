/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.assertNotNull;

public class FlutterColorProviderTest extends AbstractDartElementTest {

  @Test
  public void locatesColorReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Colors.blue; }", "Colors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesColorCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Color(0xFFE3F2FD); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color");
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstColorCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color(0xFFE3F2FD); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstColorArray() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const [Color(0xFFE3F2FD)]; }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color");
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstColorWhitespace() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color( 0xFFE3F2FD); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstARGBColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color.fromARGB(255, 255, 0, 0); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesARGBColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Color.fromARGB(255, 255, 0, 0); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color.fromARGB");
      assertNotNull(element);
    });
  }

  @Test
  public void locatesConstRGBOColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color.fromRGBO(255, 0, 0, 1.0); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesRGBOColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Color.fromRGBO(255, 255, 0, 1.0); }", "Color", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color.fromRGBO");
      assertNotNull(element);
    });
  }

  @Test
  public void locatesColorShadeReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Colors.blue.shade700; }", "Colors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesColorArrayReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Colors.blue[200]]; }", "Colors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesCuppertinoColorReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { CupertinoColors.systemGreen; }", "CupertinoColors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesColorReferenceWithComment() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Colors . blue . /* darkish */ shade700; }", "Colors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesCuppertinoColorReferenceWithWitespace() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { CupertinoColors . systemGreen; }", "CupertinoColors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

  @Test
  public void locatesCuppertinoColorReferenceWithLineEndComment() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { CupertinoColors . // comment\n systemGreen; }", "CupertinoColors", LeafPsiElement.class);
      final Color color = new FlutterColorProvider().getColorFrom(testIdentifier);
      assertNotNull(color);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assertNotNull(element);
    });
  }

}
