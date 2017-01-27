/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.*;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.ide.actions.DartPubActionBase;
import com.jetbrains.lang.dart.psi.DartFile;
import gnu.trove.THashSet;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterErrors;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterSdkAction;
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


    if (DartPubActionBase.isInProgress()) return null;

    final VirtualFile file = FlutterUtils.getRealVirtualFile(psiFile);
    if (file == null || !file.isInLocalFileSystem()) return null;

    final Project project = psiFile.getProject();
    if (!ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) return null;

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (!FlutterModuleUtils.isFlutterModule(module)) return null;

    final VirtualFile pubspec = findPubspecOrNull(project, psiFile);

    if (pubspec == null || myIgnoredPubspecPaths.contains(pubspec.getPath())) return null;

    //TODO(pq): consider validating package name here (`get` will fail if it's invalid).

    final VirtualFile packages = pubspec.getParent().findChild(FlutterConstants.PACKAGES_FILE);
    if (packages == null) {
      return createProblemDescriptors(manager, psiFile, pubspec, FlutterBundle.message("packages.get.never.done"));
    }

    if (FileDocumentManager.getInstance().isFileModified(pubspec) || pubspec.getTimeStamp() > packages.getTimeStamp()) {
      return createProblemDescriptors(manager, psiFile, pubspec, FlutterBundle.message("pubspec.edited"));
    }

    return null;
  }


  @Nullable
  private static VirtualFile findPubspecOrNull(@NotNull Project project, @Nullable PsiFile psiFile) {
    try {
      return FlutterModuleUtils.findPubspecFrom(project, psiFile);
    }
    catch (ExecutionException e) {
      return null;
    }
  }

  @NotNull
  private ProblemDescriptor[] createProblemDescriptors(@NotNull final InspectionManager manager,
                                                       @NotNull final PsiFile psiFile,
                                                       @NotNull final VirtualFile pubspecFile,
                                                       @NotNull final String errorMessage) {
    final LocalQuickFix[] fixes = new LocalQuickFix[]{
      new PackageUpdateFix(FlutterBundle.message("get.dependencies"), FlutterConstants.PACKAGES_GET_ACTION_ID),
      new PackageUpdateFix(FlutterBundle.message("upgrade.dependencies"), FlutterConstants.PACKAGES_UPGRADE_ACTION_ID),
      new IgnoreWarningFix(myIgnoredPubspecPaths, pubspecFile.getPath())};

    return new ProblemDescriptor[]{
      manager.createProblemDescriptor(psiFile, errorMessage, true, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
  }

  private static class PackageUpdateFix extends IntentionAndQuickFixAction {
    private final String myFixName;
    private final String myActionId;

    private PackageUpdateFix(@NotNull final String fixName, @NotNull final String actionId) {
      myFixName = fixName;
      myActionId = actionId;
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
      final VirtualFile file = FlutterUtils.getRealVirtualFile(psiFile);
      if (file == null || !file.isInLocalFileSystem()) return;

      final VirtualFile pubspecFile = findPubspecOrNull(project, psiFile);
      if (pubspecFile == null) return;

      final Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module == null) return;

      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) return;


      final AnAction packageAction = ActionManager.getInstance().getAction(myActionId);
      if (packageAction instanceof FlutterSdkAction) {
        try {
          ((FlutterSdkAction)packageAction).perform(sdk, project, null);
        }
        catch (ExecutionException e) {
          FlutterErrors.showError("Error performing action", e.getMessage());
        }
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
