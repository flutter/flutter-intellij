// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.highlighting;

import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.util.DartResolveUtil;

/**
 * Test the Dart code highlighting functionality.
 * <p>
 * This class tests the {@link com.jetbrains.lang.dart.highlight.DartSyntaxHighlighter} class, which is responsible for providing syntax highlighting for Dart code.
 */
public class DartHighlightingTest extends DartCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/highlighting";
  }

  // See: https://github.com/flutter/dart-intellij-third-party/issues/212
  public void skipped_testSpelling() {
    // [01/14/2026]: This is failing reliably and the path forward seems to be to prefer
    //  com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
    // `com.intellij.grazie.spellcheck` is not on our build path so if we want to update this
    // test to use it, we'd have to add it.
    //noinspection unchecked
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.checkHighlighting(true, false, true);
  }

  public void testEscapeSequences() {
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.checkHighlighting(true, true, true);
  }

  public void testColorAnnotatorIdePart() {
    // includes cases not covered by testSyncAsyncAwaitYield, testBuiltInIdentifiers and testEscapeSequences
    myFixture.configureByFile(getTestName(false) + ".dart");
    myFixture.checkHighlighting(true, true, true);
  }

  public void testUriInPartOf() {
    final PsiFile libFile = myFixture.addFileToProject("foo/bar/libFile.dart", "library libName;");
    final PsiFile part1File = myFixture.addFileToProject("part1.dart", "part of 'part1.dart'"); // self reference
    final PsiFile part2File = myFixture.addFileToProject("part2.dart", "part of 'foo/bar/wrong.dart'"); // wrong reference
    final PsiFile part3File = myFixture.addFileToProject("part3.dart", "part of 'foo/bar/libFile.dart"); // reference to libName
    final PsiFile part4File = myFixture.addFileToProject("part4.dart", "part of anotherLib;"); // reference to anotherLib

    assertEquals("libName", DartResolveUtil.getLibraryName(libFile));
    assertEquals("part1.dart", DartResolveUtil.getLibraryName(part1File));
    assertEquals("wrong.dart", DartResolveUtil.getLibraryName(part2File));
    assertEquals("libName", DartResolveUtil.getLibraryName(part3File));
    assertEquals("anotherLib", DartResolveUtil.getLibraryName(part4File));
  }
}
