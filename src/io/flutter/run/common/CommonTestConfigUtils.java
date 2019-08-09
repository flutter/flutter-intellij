/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.dart.DartSyntax;
import io.flutter.editor.ActiveEditorsOutlineService;
import org.apache.commons.lang.StringEscapeUtils;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static org.dartlang.analysis.server.protocol.ElementKind.UNIT_TEST_GROUP;
import static org.dartlang.analysis.server.protocol.ElementKind.UNIT_TEST_TEST;

/**
 * Common utilities for processing Flutter tests.
 * <p>
 * This class is useful for identifying the {@link TestType} of different Dart objects
 */
public abstract class CommonTestConfigUtils {

  public static String convertHttpServiceProtocolToWs(String url) {
    return StringUtil.trimTrailing(
      url.replaceFirst("http:", "ws:"), '/') + "/ws";
  }

  /**
   * Determines if {@param element} is a test call and returns its type.
   *
   * <p>
   * A test call is one of the following:
   * <ul>
   * <li>{@link TestType#SINGLE} if the call is a {@link DartCallExpression} marked by the {@link FlutterOutline} as {@link ElementKind#UNIT_TEST_TEST}</li>
   * <li>{@link TestType#GROUP} if the call is a {@link DartCallExpression} marked by the {@link FlutterOutline} as {@link ElementKind#UNIT_TEST_GROUP}</li>
   * <li>{@link TestType#MAIN} if the call is a {@link DartFunctionDeclarationWithBodyOrNative} named "main" that includes test calls.</li>
   * </ul>
   *
   * @return a {@link TestType} if {@param element} corresponds to a test call site, or null if {@param element} is not a test call site.
   */
  public TestType asTestCall(@NotNull PsiElement element) {
    // Named tests.
    final TestType namedTestCall = findNamedTestCall(element);
    if (namedTestCall != null) return namedTestCall;

    // Main.
    if (isMainFunctionDeclarationWithTests(element)) return TestType.MAIN;
    return null;
  }

  /**
   * Gets the elements from the outline that are runnable tests.
   */
  @NotNull
  protected Map<DartCallExpression, TestType> getTestsFromOutline(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final ActiveEditorsOutlineService service = getActiveEditorsOutlineService(project);
    if (service == null) {
      return new HashMap<>();
    }
    final FlutterOutline outline = service.getIfUpdated(file);
    // If the outline is outdated, then request a new pass to generate line markers.
    if (outline == null) {
      service.addListener(forFile(file));
      return new HashMap<>();
    }
    // Visit the fields on the outline to get which calls are actual named tests.
    final Map<DartCallExpression, TestType> callToTestType = new HashMap<>();
    visit(outline, callToTestType, file);
    return callToTestType;
  }

  @VisibleForTesting
  public boolean isMainFunctionDeclarationWithTests(@NotNull PsiElement element) {
    if (DartSyntax.isMainFunctionDeclaration(element)) {
      final Map<DartCallExpression, TestType> callToTestType = getTestsFromOutline(element.getContainingFile());
      return !callToTestType.isEmpty();
    }
    return false;
  }

  @Nullable
  protected TestType findNamedTestCall(@NotNull PsiElement element) {
    if (element instanceof DartCallExpression) {
      final DartCallExpression call = (DartCallExpression)element;
      return getTestsFromOutline(element.getContainingFile()).get(call);
    }
    return null;
  }

  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  @Nullable
  public String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    final DartCallExpression call = findEnclosingTestCall(elt, getTestsFromOutline(elt.getContainingFile()));
    if (call == null) return null;

    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    final String name = DartSyntax.unquote(lit);
    if (name == null) return null;

    return StringEscapeUtils.unescapeJava(name);
  }

  /**
   * Finds the {@link DartCallExpression} in the key set of {@param callToTestType} and also the closest parent of {@param element}.
   */
  @Nullable
  private DartCallExpression findEnclosingTestCall(@NotNull PsiElement element, @NotNull Map<DartCallExpression, TestType> callToTestType) {
    while (element != null) {
      if (element instanceof DartCallExpression) {
        final DartCallExpression call = (DartCallExpression)element;
        if (callToTestType.containsKey(call)) {
          return call;
        }
        // If we found nothing, move up to the element's parent before finding the enclosing function call.
        // This avoids an infinite loop found during testing.
        element = element.getParent();
      }
      element = DartSyntax.findClosestEnclosingFunctionCall(element);
    }
    return null;
  }

  @VisibleForTesting
  @Nullable
  protected ActiveEditorsOutlineService getActiveEditorsOutlineService(@NotNull Project project) {
    return ActiveEditorsOutlineService.getInstance(project);
  }

  /**
   * Traverses the {@param outline} tree and adds to {@param callToTestType } the {@link DartCallExpression}s that are tests or test groups.
   */
  private void visit(@NotNull FlutterOutline outline, @NotNull Map<DartCallExpression, TestType> callToTestType, @NotNull PsiFile file) {
    final PsiElement element = file.findElementAt(outline.getOffset());
    // If the outline is out-of-sync with the file, the element from that offset may be null.
    // The outline analysis is eventually consistent with the contents of the file, so if this happens,
    // this visitor will be invoked again later with a corrected outline.
    if (element == null) {
      return;
    }
    if (outline.getDartElement() != null) {
      switch (outline.getDartElement().getKind()) {
        case UNIT_TEST_GROUP:
          // We found a test group.
          callToTestType.put(DartSyntax.findClosestEnclosingFunctionCall(element), TestType.GROUP);
          break;
        case UNIT_TEST_TEST:
          // We found a unit test.
          callToTestType.put(DartSyntax.findClosestEnclosingFunctionCall(element), TestType.SINGLE);
          break;
        default:
          // We found no test.
          break;
      }
    }
    if (outline.getChildren() != null) {
      for (FlutterOutline child : outline.getChildren()) {
        visit(child, callToTestType, file);
      }
    }
  }

  /**
   * The cache of listeners for the path of each {@link PsiFile} that has an outded {@link FlutterOutline}.
   */
  private static final Map<String, LineMarkerUpdatingListener> listenerCache = new HashMap<>();

  LineMarkerUpdatingListener forFile(@NotNull final PsiFile file) {
    final String path = file.getVirtualFile().getCanonicalPath();
    final ActiveEditorsOutlineService service = getActiveEditorsOutlineService(file.getProject());
    if (!listenerCache.containsKey(path) && service != null) {
      listenerCache.put(path, new LineMarkerUpdatingListener(file.getProject(), service));
    }
    return listenerCache.get(path);
  }

  /**
   * {@link ActiveEditorsOutlineService.Listener} that forces IntelliJ to recompute line markers and other file annotations when the
   * {@link FlutterOutline} updates.
   *
   * <p>
   * Used to ensure that we don't get stuck with out-of-date line markers.
   */
  private static class LineMarkerUpdatingListener
    implements ActiveEditorsOutlineService.Listener {
    @NotNull final Project project;
    @NotNull final ActiveEditorsOutlineService service;


    private LineMarkerUpdatingListener(@NotNull Project project, @NotNull ActiveEditorsOutlineService service) {
      this.project = project;
      this.service = service;
    }

    @Override
    public void onOutlineChanged(@NotNull String path, @Nullable FlutterOutline outline) {
      forceFileAnnotation();
      service.removeListener(this);
    }

    // TODO(djshuckerow): this can be merged with the Dart plugin's forceFileAnnotation:
    // https://github.com/JetBrains/intellij-plugins/blob/master/Dart/src/com/jetbrains/lang/dart/analyzer/DartServerData.java#L300
    private void forceFileAnnotation() {
      // It's ok to call DaemonCodeAnalyzer.restart() right in this thread, without invokeLater(),
      // but it will cache RemoteAnalysisServerImpl$ServerResponseReaderThread in FileStatusMap.threads and as a result,
      // DartAnalysisServerService.myProject will be leaked in tests
      ApplicationManager.getApplication()
        .invokeLater(
          () -> {
            DaemonCodeAnalyzer.getInstance(project).restart();
          },
          ModalityState.NON_MODAL,
          project.getDisposed()
        );
    }
  }
}
