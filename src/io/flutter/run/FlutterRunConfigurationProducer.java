/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.ide.DartWritingAccessProvider;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartImportStatement;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import io.flutter.dart.DartPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Determines when we can run a Dart file as a Flutter app.
 * <p>
 * (For example, when right-clicking on main.dart in the Project view.)
 */
public class FlutterRunConfigurationProducer extends RunConfigurationProducer<SdkRunConfig> {

  public FlutterRunConfigurationProducer() {
    super(FlutterRunConfigurationType.getInstance());
  }

  /**
   * If the current file contains the main method for a Flutter app, updates the FutterRunConfiguration
   * and sets its corresponding source location.
   * <p>
   * Returns false if it wasn't a match.
   */
  @Override
  protected boolean setupConfigurationFromContext(final @NotNull SdkRunConfig config,
                                                  final @NotNull ConfigurationContext context,
                                                  final @NotNull Ref<PsiElement> sourceElement) {
    final VirtualFile main = getFlutterEntryFile(context, true);
    if (main == null) return false;

    config.setFields(new SdkFields(main, context.getProject()));
    config.setGeneratedName();

    final PsiElement elt = sourceElement.get();
    if (elt != null) {
      sourceElement.set(elt.getContainingFile());
    }
    return true;
  }

  /**
   * Returns true if an existing SdkRunConfig points to the current Dart file.
   */
  @Override
  public boolean isConfigurationFromContext(final @NotNull SdkRunConfig configuration,
                                            final @NotNull ConfigurationContext context) {
    return hasDartFile(context, configuration.getFields().getFilePath());
  }

  /**
   * Returns true if Flutter's run configuration should take priority over another one that
   * applies to the same source file.
   */
  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    // Prefer Flutter runner to plain Dart runner for Flutter code.
    return DartPlugin.isDartRunConfiguration(other.getConfigurationType());
  }

  /**
   * Returns the file containing a Flutter app's main() function, or null if not a match.
   */
  @Nullable
  public static VirtualFile getFlutterEntryFile(final @NotNull ConfigurationContext context, boolean requireFlutterImport) {
    final DartFile dart = getDartFile(context);
    if (dart == null) return null;

    if (DartResolveUtil.getMainFunction(dart) == null) return null;
    if (requireFlutterImport && findImportUrls(dart).noneMatch((url) -> url.startsWith("package:flutter/"))) {
      return null;
    }

    final VirtualFile virtual = DartResolveUtil.getRealVirtualFile(dart);
    if (virtual == null) return null;

    if (!ProjectRootManager.getInstance(context.getProject()).getFileIndex().isInContent(virtual)) {
      return null;
    }

    if (DartWritingAccessProvider.isInDartSdkOrDartPackagesFolder(dart.getProject(), virtual)) {
      return null;
    }

    return virtual;
  }

  /**
   * Returns true if the context points to the given file and it's a Dart file.
   */
  public static boolean hasDartFile(@NotNull ConfigurationContext context, String dartPath) {
    final DartFile dart = getDartFile(context);
    if (dart == null) {
      return false;
    }

    final VirtualFile virtual = DartResolveUtil.getRealVirtualFile(dart);
    return virtual != null && virtual.getPath().equals(dartPath);
  }

  /**
   * Returns the Dart file at the current location, or null if not a match.
   */
  private static @Nullable DartFile getDartFile(final @NotNull ConfigurationContext context) {
    final PsiElement elt = context.getPsiLocation();
    if (elt == null) return null;

    final PsiFile psiFile = elt.getContainingFile();
    if (!(psiFile instanceof DartFile)) return null;

    return (DartFile)psiFile;
  }

  /**
   * Returns the import URL's in a Dart file.
   */
  private static @NotNull Stream<String> findImportUrls(@NotNull DartFile file) {
    final DartImportStatement[] imports = PsiTreeUtil.getChildrenOfType(file, DartImportStatement.class);
    if (imports == null) return Stream.empty();

    return Arrays.stream(imports).map(DartImportStatement::getUriString);
  }
}
