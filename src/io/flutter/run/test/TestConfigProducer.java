/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.*;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterRunConfigurationProducer;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Determines when we can run a test using "flutter test".
 */
public class TestConfigProducer extends RunConfigurationProducer<TestConfig> {

  protected TestConfigProducer() {
    super(TestConfigType.getInstance());
  }

  /**
   * If the current file looks like a Flutter test, initializes the run config to run it.
   * <p>
   * Returns true if successfully set up.
   */
  @Override
  protected boolean setupConfigurationFromContext(TestConfig config, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    final PsiElement elt = context.getPsiLocation();
    if (elt instanceof PsiDirectory) {
      return setupForDirectory(config, (PsiDirectory)elt);
    }

    final DartFile file = FlutterRunConfigurationProducer.getDartFile(context);
    if (file == null) {
      return false;
    }

    final String testName = findTestName(elt);
    if (testName != null) {
      return setupForSingleTest(config, context, file, testName);
    }

    return setupForDartFile(config, context, file);
  }

  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  private String findTestName(@Nullable PsiElement elt) {
    while (elt != null) {
      // Look for a call expression: test("something", ...);
      if (elt instanceof DartCallExpression) {
        final DartCallExpression call = (DartCallExpression) elt;
        final String name = getCalledFunctionName(call);
        if (name != null && name.equals("test")) {
          final DartStringLiteralExpression lit = getFirstArgumentIfStringLiteral(call);
          if (lit == null) return null;
          return getStringIfNotTemplate(lit);
        }
      }
      elt = elt.getParent();
    }
    return null; // not found
  }

  @Nullable
  private String getCalledFunctionName(@NotNull DartCallExpression call) {
    if (!(call.getFirstChild() instanceof DartReference)) return null;
    return call.getFirstChild().getText();
  }

  @Nullable
  private DartStringLiteralExpression getFirstArgumentIfStringLiteral(@NotNull DartCallExpression call) {
    if (call.getArguments() == null) return null;

    final DartArgumentList list = call.getArguments().getArgumentList();
    if (list == null) return null;

    final DartExpression first = list.getExpressionList().get(0);
    if (first instanceof DartStringLiteralExpression) {
      return (DartStringLiteralExpression)first;
    }
    return null;
  }

  @Nullable
  private String getStringIfNotTemplate(@NotNull DartStringLiteralExpression expr) {
    if (!expr.getShortTemplateEntryList().isEmpty() || !expr.getLongTemplateEntryList().isEmpty()) {
      return null; // is a template
    }
    // We expect a quote, string part, quote.
    if (expr.getFirstChild() == null) return null;
    final PsiElement second = expr.getFirstChild().getNextSibling();
    if (second.getNextSibling() != expr.getLastChild()) return null; // not three items
    if (!(second instanceof LeafPsiElement)) return null;
    final LeafPsiElement leaf = (LeafPsiElement) second;

    if (leaf.getElementType() != DartTokenTypes.REGULAR_STRING_PART) return null;
    return leaf.getText();
  }

  private boolean setupForSingleTest(TestConfig config, ConfigurationContext context, DartFile file, String testName) {
    final VirtualFile testFile = verifyFlutterTestFile(config, context, file);
    if (testFile == null) return false;

    config.setFields(TestFields.forTestName(testName, testFile.getPath()));
    config.setGeneratedName();

    return true;
  }

  private boolean setupForDartFile(TestConfig config, ConfigurationContext context, DartFile file) {
    final VirtualFile testFile = verifyFlutterTestFile(config, context, file);
    if (testFile == null) return false;

    config.setFields(TestFields.forFile(testFile.getPath()));
    config.setGeneratedName();

    return true;
  }

  private VirtualFile verifyFlutterTestFile(TestConfig config, ConfigurationContext context, DartFile file) {
    final PubRoot root = PubRoot.forPsiFile(file);
    if (root == null) return null;

    if (!FlutterModuleUtils.isFlutterModule(context.getModule())) return null;

    final VirtualFile candidate = FlutterRunConfigurationProducer.getFlutterEntryFile(context, false);
    if (candidate == null) return null;

    final String relativePath = root.getRelativePath(candidate);
    if (relativePath == null || !relativePath.startsWith("test/")) return null;

    return candidate;
  }

  private boolean setupForDirectory(TestConfig config, PsiDirectory dir) {
    final PubRoot root = PubRoot.forDescendant(dir.getVirtualFile(), dir.getProject());
    if (root == null) return false;
    
    if (!FlutterModuleUtils.hasFlutterModule(dir.getProject())) return false;

    if (!root.hasTests(dir.getVirtualFile())) return false;

    config.setFields(TestFields.forDir(dir.getVirtualFile().getPath()));
    config.setGeneratedName();
    return true;
  }

  /**
   * Returns true if a run config was already created for this file. If so we will reuse it.
   */
  @Override
  public boolean isConfigurationFromContext(TestConfig config, ConfigurationContext context) {
    final VirtualFile fileOrDir = config.getFields().getFileOrDir();
    if (fileOrDir == null) return false;

    final PsiElement target = context.getPsiLocation();
    if (target instanceof PsiDirectory) {
      return ((PsiDirectory)target).getVirtualFile().equals(fileOrDir);
    }

    if (!FlutterRunConfigurationProducer.hasDartFile(context, fileOrDir.getPath())) return false;

    final String testName = findTestName(context.getPsiLocation());
    if (config.getFields().getScope() == TestFields.Scope.NAME) {
      if (testName == null || !testName.equals(config.getFields().getTestName())) return false;
    } else {
      if (testName != null) return false;
    }

    return true;
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return DartPlugin.isDartTestConfiguration(other.getConfigurationType());
  }
}
