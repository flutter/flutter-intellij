/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.*;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.psi.DartFile;
import gnu.trove.THashSet;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class FlutterDependencyInspection extends LocalInspectionTool {
  private final Set<String> myIgnoredPubspecPaths = new THashSet<>(); // remember for the current session only, do not serialize

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile psiFile, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    if (!isOnTheFly) return null;

    if (!(psiFile instanceof DartFile)) return null;

    if (DartPlugin.isPubActionInProgress()) return null;

    final VirtualFile file = FlutterUtils.getRealVirtualFile(psiFile);
    if (file == null || !file.isInLocalFileSystem()) return null;

    final Project project = psiFile.getProject();
    if (!ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) return null;

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (!FlutterModuleUtils.isFlutterModule(module)) return null;

    final PubRoot root = PubRoot.forPsiFile(psiFile);

    if (root == null || myIgnoredPubspecPaths.contains(root.getPubspec().getPath())) return null;

    // TODO(pq): consider validating package name here (`get` will fail if it's invalid).

    if (root.getPackages() == null) {
      return createProblemDescriptors(manager, psiFile, root, FlutterBundle.message("packages.get.never.done"));
    }

    if (!root.hasUpToDatePackages()) {
      return createProblemDescriptors(manager, psiFile, root, FlutterBundle.message("pubspec.edited"));
    }

    return null;
  }

  @NotNull
  private ProblemDescriptor[] createProblemDescriptors(@NotNull final InspectionManager manager,
                                                       @NotNull final PsiFile psiFile,
                                                       @NotNull final PubRoot root,
                                                       @NotNull final String errorMessage) {
    final LocalQuickFix[] fixes = new LocalQuickFix[]{
      new PackageUpdateFix(FlutterBundle.message("get.dependencies"), FlutterSdk::startPackagesGet),
      new PackageUpdateFix(FlutterBundle.message("upgrade.dependencies"), FlutterSdk::startPackagesUpgrade),
      new IgnoreWarningFix(myIgnoredPubspecPaths, root.getPubspec().getPath())};

    return new ProblemDescriptor[]{
      manager.createProblemDescriptor(psiFile, errorMessage, true, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
  }

  private interface SdkAction {
    void run(FlutterSdk sdk, @NotNull PubRoot root, @NotNull Project project) throws ExecutionException;
  }

  private static class PackageUpdateFix extends IntentionAndQuickFixAction {
    private final String myFixName;
    private final SdkAction mySdkAction;

    private PackageUpdateFix(@NotNull final String fixName, @NotNull final SdkAction action) {
      myFixName = fixName;
      mySdkAction = action;
    }

    @Override
    @NotNull
    public String getName() {
      return myFixName;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final PsiFile psiFile, @Nullable final Editor editor) {
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) return;

      final PubRoot root = PubRoot.forPsiFile(psiFile);
      if (root == null) return;

      try {
        // TODO(skybrian) analytics?
        mySdkAction.run(sdk, root, project);
      } catch (ExecutionException e) {
        FlutterMessages.showError("Error performing action", e.getMessage());
      }
    }
  }

  private static class IgnoreWarningFix extends IntentionAndQuickFixAction {
    @NotNull private final Set<String> myIgnoredPubspecPaths;
    @NotNull private final String myPubspecPath;

    public IgnoreWarningFix(@NotNull final Set<String> ignoredPubspecPaths, @NotNull final String pubspecPath) {
      myIgnoredPubspecPaths = ignoredPubspecPaths;
      myPubspecPath = pubspecPath;
    }

    @Override
    @NotNull
    public String getName() {
      return FlutterBundle.message("ignore.warning");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final PsiFile psiFile, @Nullable final Editor editor) {
      myIgnoredPubspecPaths.add(myPubspecPath);
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
  }
}
