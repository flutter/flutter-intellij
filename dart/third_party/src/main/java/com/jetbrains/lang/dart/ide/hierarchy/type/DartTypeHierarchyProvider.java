// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.dartlsp.impl.features.hierarchy.type.LspTypeHierarchyBrowser;
import com.intellij.platform.dartlsp.impl.features.hierarchy.type.LspTypeHierarchyProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.psi.DartClass;
import com.jetbrains.lang.dart.psi.DartReference;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Type hierarchy provider for Dart.
 *
 * <p>While LSP features are experimental, this class acts as a proxy: delegating to
 * {@link LspTypeHierarchyProvider} when LSP is enabled, and falling back to legacy DAS
 * when disabled or on unsupported SDK versions.
 *
 * <p>TODO(lsp): When LSP is enabled by default and legacy DAS hierarchy is retired,
 * remove the &lt;typeHierarchyProvider language="Dart"&gt; registration from plugin.xml
 * and delete this class so the platform's LspTypeHierarchyProvider handles Dart directly.
 */
public final class DartTypeHierarchyProvider implements HierarchyProvider {
  private final HierarchyProvider myLspProvider = new LspTypeHierarchyProvider();

  @Override
  public @Nullable PsiElement getTarget(final @NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null && DartConfigurable.isExperimentalLspFeaturesEnabled(project)) {
      return myLspProvider.getTarget(dataContext);
    }
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (project == null || editor == null) return null;

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    final PsiElement psiElement = file == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    final DartReference dartReference = PsiTreeUtil.getParentOfType(psiElement, DartReference.class);
    if (dartReference != null) {
      return dartReference.resolveDartClass().getDartClass();
    }
    return PsiTreeUtil.getParentOfType(psiElement, DartClass.class);
  }

  @Override
  public @NotNull HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
      if (DartConfigurable.isExperimentalLspFeaturesEnabled(target.getProject())) {
          return myLspProvider.createHierarchyBrowser(target);
      }
      final DartClass dartClass = target instanceof DartClass ? (DartClass) target : PsiTreeUtil.getParentOfType(target, DartClass.class);
      if (dartClass == null) {
          throw new IllegalArgumentException("Target element must be a DartClass or inside one");
      }
      return new DartTypeHierarchyBrowser(target.getProject(), dartClass);
  }

  @Override
  public void browserActivated(final @NotNull HierarchyBrowser hierarchyBrowser) {
    if (hierarchyBrowser instanceof LspTypeHierarchyBrowser) {
      myLspProvider.browserActivated(hierarchyBrowser);
      return;
    }
    ((DartTypeHierarchyBrowser)hierarchyBrowser).changeView(TypeHierarchyBrowserBase.getTypeHierarchyType());
  }
}
