/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class FlutterEditorAnnotatorTest extends AbstractDartElementTest {
  @Test
  public void locatesColorReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Colors.blue; }", "Colors", LeafPsiElement.class);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesIconReference() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Icons.access_alarm; }", "Icons", LeafPsiElement.class);
      final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesColorCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Color(0xFFE3F2FD); }", "Color", LeafPsiElement.class);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color");
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesConstColorCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color(0xFFE3F2FD); }", "Color", LeafPsiElement.class);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesConstColorArray() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const [Color(0xFFE3F2FD)]; }", "Color", LeafPsiElement.class);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color");
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesConstColorWhitespace() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color( 0xFFE3F2FD); }", "Color", LeafPsiElement.class);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesIconCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier =
        setUpDartElement("main() { IconData(0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "IconData");
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesConstIconCtor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier =
        setUpDartElement("main() { const IconData(0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesConstARGBColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color.fromARGB(255, 255, 0, 0); }", "Color", LeafPsiElement.class);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesARGBColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Color.fromARGB(255, 255, 0, 0); }", "Color", LeafPsiElement.class);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color.fromARGB");
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesConstRGBOColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { const Color.fromRGBO(255, 0, 0, 1.0); }", "Color", LeafPsiElement.class);
      final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }

  @Test
  public void locatesRGBOColor() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("main() { Color.fromRGBO(255, 255, 0, 1.0); }", "Color", LeafPsiElement.class);
      final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "Color.fromRGBO");
      assert element != null;

      final FlutterEditorAnnotator annotator = new FlutterEditorAnnotator();
      final AnnotationSession annotationSession = new AnnotationSession(testIdentifier.getContainingFile());
      final AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(annotationSession);

      annotator.annotate(element, annotationHolder);

      assertTrue(annotationHolder.hasAnnotations());
      assertEquals(1, annotationHolder.size());

      final Annotation annotation = annotationHolder.get(0);
      assertEquals(HighlightSeverity.INFORMATION, annotation.getSeverity());
    });
  }
}
