/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import io.flutter.AbstractDartElementTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.Icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlutterCompletionContributorTest extends AbstractDartElementTest {

  @Test
  public void testFindIconForColors() {
    Icon redIcon = FlutterCompletionContributor.findIcon("Colors", "red");
    assertTrue(redIcon instanceof ColorIcon);

    Icon unknownColor = FlutterCompletionContributor.findIcon("Colors", "notAColor");
    assertNull(unknownColor);
  }

  @Test
  public void testFindIconForCupertinoColors() {
    Icon greenIcon = FlutterCompletionContributor.findIcon("CupertinoColors", "systemGreen");
    assertTrue(greenIcon instanceof ColorIcon);

    Icon unknownColor = FlutterCompletionContributor.findIcon("CupertinoColors", "notAColor");
    assertNull(unknownColor);
  }

  @Test
  public void testFindIconForIcons() {
    Icon addIcon = FlutterCompletionContributor.findIcon("Icons", "add");
    assertNotNull(addIcon);
    assertFalse(addIcon instanceof EmptyIcon);

    Icon unknownIcon = FlutterCompletionContributor.findIcon("Icons", "notAnIcon");
    assertTrue(unknownIcon instanceof EmptyIcon);
  }

  @Test
  public void testFindIconForCupertinoIcons() {
    Icon bookIcon = FlutterCompletionContributor.findIcon("CupertinoIcons", "book");
    assertNotNull(bookIcon);
    assertFalse(bookIcon instanceof EmptyIcon);

    Icon unknownIcon = FlutterCompletionContributor.findIcon("CupertinoIcons", "notAnIcon");
    assertTrue(unknownIcon instanceof EmptyIcon);
  }

  @Test
  public void testFindIconEdgeCases() {
    assertNull(FlutterCompletionContributor.findIcon("Colors", null));
    assertNull(FlutterCompletionContributor.findIcon(null, "red"));
    assertNull(FlutterCompletionContributor.findIcon("OtherClass", "red"));
  }

  @Test
  public void testFillCompletionVariantsDecoratesMatchingColorAndPreservesExpensiveRenderer() throws Exception {
    run(() -> {
      PsiElement position = setUpDartElement("main() { Colors.IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz", LeafPsiElement.class);
      CompletionParameters parameters = mock(CompletionParameters.class);
      when(parameters.getPosition()).thenReturn(position);

      CompletionResultSet resultSet = mock(CompletionResultSet.class);
      LookupElement baseLookup = new LookupElement() {
        @Override
        public @NotNull String getLookupString() {
          return "blue";
        }

        @Override
        public void renderElement(@NotNull LookupElementPresentation presentation) {
          presentation.setItemText("blue");
          presentation.setTypeText("MaterialColor");
        }

        @Override
        public @Nullable LookupElementRenderer<? extends LookupElement> getExpensiveRenderer() {
          return new LookupElementRenderer<LookupElement>() {
            @Override
            public void renderElement(@NotNull LookupElement element, @NotNull LookupElementPresentation presentation) {
              presentation.setItemText("blue");
              presentation.setTypeText("MaterialColor (resolved)");
            }
          };
        }
      };

      CompletionResult completionResult =
        CompletionResult.wrap(baseLookup, PrefixMatcher.ALWAYS_TRUE, CompletionSorter.emptySorter());
      assertNotNull(completionResult);

      doAnswer(invocation -> {
        Consumer<CompletionResult> consumer = invocation.getArgument(1);
        consumer.consume(completionResult);
        return null;
      }).when(resultSet).runRemainingContributors(eq(parameters), any());

      new FlutterCompletionContributor().fillCompletionVariants(parameters, resultSet);

      ArgumentCaptor<CompletionResult> captor = ArgumentCaptor.forClass(CompletionResult.class);
      verify(resultSet).passResult(captor.capture());

      LookupElement decorated = captor.getValue().getLookupElement();
      LookupElementPresentation presentation = new LookupElementPresentation();
      decorated.renderElement(presentation);
      assertEquals("blue", presentation.getItemText());
      assertEquals("", presentation.getTypeText());
      assertTrue(presentation.getTypeIcon() instanceof ColorIcon);
      assertTrue(presentation.isTypeIconRightAligned());

      // Verify expensive renderer also preserves the color icon after delegate expensive rendering.
      @SuppressWarnings("unchecked")
      LookupElementRenderer<LookupElement> expensiveRenderer =
        (LookupElementRenderer<LookupElement>) decorated.getExpensiveRenderer();
      assertNotNull(expensiveRenderer);
      LookupElementPresentation expensivePresentation = new LookupElementPresentation();
      expensiveRenderer.renderElement(decorated, expensivePresentation);
      assertEquals("blue", expensivePresentation.getItemText());
      assertEquals("", expensivePresentation.getTypeText());
      assertTrue(expensivePresentation.getTypeIcon() instanceof ColorIcon);
      assertTrue(expensivePresentation.isTypeIconRightAligned());
    });
  }

  @Test
  public void testFillCompletionVariantsPassesThroughUnmatchedMember() throws Exception {
    run(() -> {
      PsiElement position = setUpDartElement("main() { Colors.IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz", LeafPsiElement.class);
      CompletionParameters parameters = mock(CompletionParameters.class);
      when(parameters.getPosition()).thenReturn(position);

      CompletionResultSet resultSet = mock(CompletionResultSet.class);
      LookupElement baseLookup = LookupElementBuilder.create("notAColor");
      CompletionResult completionResult =
        CompletionResult.wrap(baseLookup, PrefixMatcher.ALWAYS_TRUE, CompletionSorter.emptySorter());
      assertNotNull(completionResult);

      doAnswer(invocation -> {
        Consumer<CompletionResult> consumer = invocation.getArgument(1);
        consumer.consume(completionResult);
        return null;
      }).when(resultSet).runRemainingContributors(eq(parameters), any());

      new FlutterCompletionContributor().fillCompletionVariants(parameters, resultSet);

      ArgumentCaptor<CompletionResult> captor = ArgumentCaptor.forClass(CompletionResult.class);
      verify(resultSet).passResult(captor.capture());
      assertSame(baseLookup, captor.getValue().getLookupElement());
    });
  }

  @Test
  public void testFillCompletionVariantsSupportsImportPrefixAndMidChain() throws Exception {
    run(() -> {
      // Import prefix (`m.Colors.<caret>`)
      assertColorCompletionDecorated("import 'package:flutter/material.dart' as m; main() { m.Colors.IntellijIdeaRulezzz; }");

      // Mid-chain before method call (`Colors.<caret>.withValues(...)`)
      assertColorCompletionDecorated("main() { Colors.IntellijIdeaRulezzz.withValues(alpha: 0.5); }");

      // Mid-chain before property/shade (`Colors.<caret>.shade200`)
      assertColorCompletionDecorated("main() { Colors.IntellijIdeaRulezzz.shade200; }");

      // Mid-chain before index operator (`Colors.<caret>[200]`)
      assertColorCompletionDecorated("main() { Colors.IntellijIdeaRulezzz[200]; }");

      // Combined import prefix + multiline + mid-chain
      assertColorCompletionDecorated("main() {\n  m.Colors\n    .IntellijIdeaRulezzz\n    .withValues(alpha: 0.5);\n}");
    });
  }

  @Test
  public void testFillCompletionVariantsIgnoresNonQualifiedOrUnsupportedPrefix() throws Exception {
    run(() -> {
      // Unqualified identifier (`Colors<caret>`)
      assertCompletionIgnored("main() { Colors; }", "Colors");

      // Non-reference token (`123`)
      assertCompletionIgnored("main() { var x = 123; }", "123");

      // Left-hand side of dot (`Col<caret>.red`)
      assertCompletionIgnored("main() { ColIntellijIdeaRulezzz.red; }", "ColIntellijIdeaRulezzz");

      // Downstream member after constant (`Colors.red.<caret>` and `Icons.add.<caret>`)
      assertCompletionIgnored("main() { Colors.red.IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz");
      assertCompletionIgnored("main() { Icons.add.IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz");

      // Non-reference qualifier (`getObj().<caret>`)
      assertCompletionIgnored("main() { getObj().IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz");

      // Unsupported class with and without import prefix
      assertCompletionIgnored("main() { OtherClass.IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz");
      assertCompletionIgnored("main() { m.OtherClass.IntellijIdeaRulezzz; }", "IntellijIdeaRulezzz");
    });
  }

  private void assertColorCompletionDecorated(@NotNull String dartSource) {
    PsiElement position = setUpDartElement(dartSource, "IntellijIdeaRulezzz", LeafPsiElement.class);
    CompletionParameters parameters = mock(CompletionParameters.class);
    when(parameters.getPosition()).thenReturn(position);

    CompletionResultSet resultSet = mock(CompletionResultSet.class);
    LookupElement baseLookup = LookupElementBuilder.create("red").withTypeText("MaterialColor");
    CompletionResult completionResult =
      CompletionResult.wrap(baseLookup, PrefixMatcher.ALWAYS_TRUE, CompletionSorter.emptySorter());
    assertNotNull(completionResult);

    doAnswer(invocation -> {
      Consumer<CompletionResult> consumer = invocation.getArgument(1);
      consumer.consume(completionResult);
      return null;
    }).when(resultSet).runRemainingContributors(eq(parameters), any());

    new FlutterCompletionContributor().fillCompletionVariants(parameters, resultSet);

    ArgumentCaptor<CompletionResult> captor = ArgumentCaptor.forClass(CompletionResult.class);
    verify(resultSet).passResult(captor.capture());

    LookupElement decorated = captor.getValue().getLookupElement();
    assertNull(decorated.getExpensiveRenderer());
    LookupElementPresentation presentation = new LookupElementPresentation();
    decorated.renderElement(presentation);
    assertEquals("red", presentation.getItemText());
    assertEquals("", presentation.getTypeText());
    assertTrue(presentation.getTypeIcon() instanceof ColorIcon);
    assertTrue(presentation.isTypeIconRightAligned());
  }

  private void assertCompletionIgnored(@NotNull String dartSource, @NotNull String elementText) {
    PsiElement position = setUpDartElement(dartSource, elementText, LeafPsiElement.class);
    CompletionParameters parameters = mock(CompletionParameters.class);
    when(parameters.getPosition()).thenReturn(position);
    CompletionResultSet resultSet = mock(CompletionResultSet.class);

    new FlutterCompletionContributor().fillCompletionVariants(parameters, resultSet);
    verify(resultSet, never()).runRemainingContributors(any(), any());
  }
}
