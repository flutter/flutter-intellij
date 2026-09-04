// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.documentation;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.ide.completion.DartLookupObject;
import com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider;
import com.jetbrains.lang.dart.psi.DartComponent;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Test the {@link com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider} class.
 * <p>
 * This class is responsible for providing documentation to the IDE, such as the quick navigation info and the documentation URLs.
 */
public class DartDocumentationProviderTest extends DartCodeInsightFixtureTestCase {

  private final DartDocumentationProvider myProvider = new DartDocumentationProvider();

  private void doTestQuickNavigateInfo(String fileContents) {
    final int caretOffset = fileContents.indexOf("<caret>");
    assertTrue(caretOffset != -1);
    final String realContents = fileContents.substring(0, caretOffset) + fileContents.substring(caretOffset + "<caret>".length());
    final PsiFile psiFile = myFixture.addFileToProject("test.dart", realContents);
    final PsiElement element = PsiTreeUtil.getParentOfType(psiFile.findElementAt(caretOffset), DartComponent.class);
    assertNotNull("target element not found at offset " + caretOffset, element);
    assertNull(myProvider.getQuickNavigateInfo(element, element));
  }

  private void doTestDocUrl(@NotNull final String expectedUrl, @NotNull final String fileRelPath, @NotNull final String declText) {
    final String filePath = DartSdk.getDartSdk(getProject()).getHomePath() + "/lib/" + fileRelPath;
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    final int caretOffset = psiFile.getText().indexOf(declText);
    assertTrue(caretOffset != -1);
    final PsiElement element = PsiTreeUtil.getParentOfType(psiFile.findElementAt(caretOffset), DartComponent.class);
    assertNotNull("target element not found at offset " + caretOffset, element);
    assertSameElements(myProvider.getUrlFor(element, element), Collections.singletonList(expectedUrl));
  }

  public void testFieldRef() {
    doTestQuickNavigateInfo("class A { int <caret>x; foo() => x; }");
  }

  public void testFunctionRef() {
    doTestQuickNavigateInfo("<caret>f(); g() => f();");
  }

  public void testEnumRef() {
    doTestQuickNavigateInfo("enum E { <caret>E1 } var e = E.E1;");
  }

  public void testPsiDirectoryRef() {
    final PsiFile psiFile = myFixture.addFileToProject("test.dart", "/// test docs\nvoid() main() {}");
    final PsiDirectory psiDirectory = psiFile.getContainingDirectory();

    String generatedDocs = myProvider.generateDoc(psiDirectory, null);
    assertNull("expected no docs for directory", generatedDocs);
  }

  public void testDocUrls() {
    doTestDocUrl("https://api.dart.dev/stable/dart-core/int-class.html",
                 "core/int.dart",
                 "abstract class int extends num {");
    doTestDocUrl("https://api.dart.dev/stable/dart-core/String/String.fromCharCodes.html",
                 "core/string.dart",
                 "external factory String.fromCharCodes(Iterable<int> charCodes,");
    doTestDocUrl("https://api.dart.dev/stable/dart-core/List/List.html",
                 "core/list.dart",
                 "external factory List([int length]);");
    doTestDocUrl("https://api.dart.dev/stable/dart-core/int/int.fromEnvironment.html",
                 "core/int.dart",
                 "external const factory int.fromEnvironment(String name, {int defaultValue});");
    doTestDocUrl("https://api.dart.dev/stable/dart-math/cos.html",
                 "math/math.dart",
                 "external double cos(num radians);");
    doTestDocUrl("https://api.dart.dev/stable/dart-core/List/length.html",
                 "core/list.dart",
                 "set length(int newLength);");
  }

  public void testGenerateDocWithAndWithoutActiveLookup() {
    DartConfigurable.setExperimentalLspFeaturesEnabled(getProject(), true);

    final PsiFile psiFile = myFixture.addFileToProject("test.dart", "/// Documentation for class A\nclass A {}");
    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());
    final PsiElement element = PsiTreeUtil.getParentOfType(psiFile.findElementAt(psiFile.getText().indexOf("A {}")), DartComponent.class);
    assertNotNull(element);

    // 1. Without an active lookup, generateDoc returns null when experimental LSP features is enabled
    assertNull("Expected null doc without active lookup", myProvider.generateDoc(element, null));

    // 2. With an active lookup containing DartLookupObject, generateDoc generates doc
    LookupElement lookupElement =
      LookupElementBuilder.create(new DartLookupObject(getProject(), null, 0), "A");
    LookupManager.getInstance(getProject()).showLookup(myFixture.getEditor(), lookupElement);
    try {
      final String doc = myProvider.generateDoc(element, null);
      assertNotNull("Expected non-null doc with active DartLookupObject", doc);
      assertTrue("Expected doc to contain class documentation", doc.contains("Documentation for class A"));
    }
    finally {
      LookupManager.getInstance(getProject()).hideActiveLookup();
    }

    // 3. With an active lookup containing a non-DartLookupObject, generateDoc returns null
    LookupElement plainLookupElement =
      LookupElementBuilder.create("plain");
    LookupManager.getInstance(getProject()).showLookup(myFixture.getEditor(), plainLookupElement);
    try {
      assertNull("Expected null doc with non-DartLookupObject", myProvider.generateDoc(element, null));
    }
    finally {
      LookupManager.getInstance(getProject()).hideActiveLookup();
    }
  }
}
