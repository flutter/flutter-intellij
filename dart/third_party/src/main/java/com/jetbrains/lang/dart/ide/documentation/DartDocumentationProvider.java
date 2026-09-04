// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.documentation;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.ide.completion.DartLookupObject;
import com.jetbrains.lang.dart.psi.DartClass;
import com.jetbrains.lang.dart.psi.DartComponent;
import com.jetbrains.lang.dart.psi.DartFactoryConstructorDeclaration;
import com.jetbrains.lang.dart.psi.DartNamedConstructorDeclaration;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class DartDocumentationProvider implements DocumentationProvider {
  private static final String BASE_DART_DOC_URL = "https://api.dart.dev/stable/";

  @Override
  public @Nls String generateDoc(final @NotNull PsiElement element, final @Nullable PsiElement originalElement) {
    final Lookup activeLookup = LookupManager.getInstance(element.getProject()).getActiveLookup();
    final LookupElement item = activeLookup != null ? activeLookup.getCurrentItem() : null;
    final boolean isFromCompletion = item != null && item.getObject() instanceof DartLookupObject;
    if (isFromCompletion) {
      return DartDocUtil.generateDoc(element);
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return object instanceof DartLookupObject ? ((DartLookupObject)object).findPsiElement() : null;
  }

  @Override
  public @Nls String getQuickNavigateInfo(final PsiElement element, final PsiElement originalElement) {
    return null;
  }

  @Override
  public @Nullable List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    if (!(element instanceof DartComponent) && !(element.getParent() instanceof DartComponent)) {
      return null;
    }

    final DartComponent component = (DartComponent)(element instanceof DartComponent ? element : element.getParent());
    if (!component.isPublic()) return null;

    final String docUrl = constructDocUrl(component);
    return docUrl == null ? null : Collections.singletonList(docUrl);
  }

  private static @Nullable String constructDocUrl(final @NotNull DartComponent component) {
    // class:       https://api.dart.dev/stable/dart-web_audio/AnalyserNode-class.html
    // constructor: https://api.dart.dev/stable/dart-core/DateTime/DateTime.fromMicrosecondsSinceEpoch.html
    //              https://api.dart.dev/stable/dart-core/List/List.html
    // method:      https://api.dart.dev/stable/dart-core/Object/toString.html
    // property:    https://api.dart.dev/stable/dart-core/List/length.html
    // function:    https://api.dart.dev/stable/dart-math/cos.html

    final String libRelatedUrlPart = getLibRelatedUrlPart(component);
    final String name = component.getName();
    if (libRelatedUrlPart == null || name == null) return null;

    final String baseUrl = BASE_DART_DOC_URL + libRelatedUrlPart + "/";

    if (component instanceof DartClass) {
      return baseUrl + name + "-class.html";
    }

    final DartClass dartClass = PsiTreeUtil.getParentOfType(component, DartClass.class, true);

    if (component instanceof DartNamedConstructorDeclaration) {
      assert dartClass != null;
      return baseUrl + dartClass.getName() + "/" +
             StringUtil.join(((DartNamedConstructorDeclaration)component).getComponentNameList(), NavigationItem::getName, ".") +
             ".html";
    }

    if (component instanceof DartFactoryConstructorDeclaration) {
      assert dartClass != null;
      return baseUrl + dartClass.getName() + "/" +
             StringUtil.join(((DartFactoryConstructorDeclaration)component).getComponentNameList(), NavigationItem::getName, ".") +
             ".html";
    }

    if (dartClass != null) {
      // method, property
      return baseUrl + dartClass.getName() + "/" + name + ".html";
    }
    else {
      // library-level function
      return baseUrl + name + ".html";
    }
  }

  private static @Nullable String getLibRelatedUrlPart(final @NotNull PsiElement element) {
    for (VirtualFile libFile : DartResolveUtil.findLibrary(element.getContainingFile())) {
      final DartUrlResolver urlResolver = DartUrlResolver.getInstance(element.getProject(), libFile);

      final String dartUrl = urlResolver.getDartUrlForFile(libFile);
      // "dart:html" -> "dart-html"
      if (dartUrl.startsWith(DartUrlResolver.DART_PREFIX)) {
        return "dart-" + dartUrl.substring(DartUrlResolver.DART_PREFIX.length());
      }
    }

    return null;
  }
}
