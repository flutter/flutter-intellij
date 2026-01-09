/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.dart.DartSyntax;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.editor.ActiveEditorsOutlineService;
import io.flutter.utils.OpenApiUtils;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.util.containers.ContainerUtil;
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

  public void refreshOutline(@NotNull PsiElement element) {
    getTestsFromOutline(element.getContainingFile());
  }

  /**
   * Determines if {@param element} is a test call and returns its type.
   *
   * <p>
   * A test call is one of the following:
   * <ul>
   * <li>{@link TestType#SINGLE} if the call is a {@link DartCallExpression} marked by the {@link FlutterOutline} as {@link ElementKind#UNIT_TEST_TEST}</li>
   * <li>{@link TestType#GROUP} if the call is a {@link DartCallExpression} marked by the {@link FlutterOutline} as {@link ElementKind#UNIT_TEST_GROUP}</li>
   * </ul>
   *
   * @return a {@link TestType} if {@param element} corresponds to a test call site, or null if {@param element} is not a test call site.
   */
  public TestType asTestCall(@NotNull PsiElement element) {
    // Named tests.
    final TestType namedTestCall = findNamedTestCall(element);
    //noinspection RedundantIfStatement
    if (namedTestCall != null) {
      return namedTestCall;
    }

    return null;
  }

  private final Map<String, OutlineCache> cache = ContainerUtil.newHashMap();

  private void clearCachedInfo(String path) {
    synchronized (this) {
      cache.remove(path);
    }
  }

  /**
   * Gets the elements from the outline that are runnable tests.
   */
  @NotNull
  private Map<Integer, TestType> getTestsFromOutline(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final ActiveEditorsOutlineService outlineService = getActiveEditorsOutlineService(project);
    if (outlineService == null) {
      return ContainerUtil.newHashMap();
    }

    final FlutterOutline outline = outlineService.getIfUpdated(file);
    final String path = file.getVirtualFile().getPath();
    final boolean outlineOutdated;
    synchronized (this) {
      final OutlineCache entry = cache.get(path);
      outlineOutdated = cache.containsKey(path) && outline != entry.outline;
    }
    // If the outline is outdated, then request a new pass to generate line markers.
    if (outline == null || outlineOutdated) {
      clearCachedInfo(path);
      final LineMarkerUpdatingListener listener = getListenerForFile(file);
      if (listener != null) {
        outlineService.addListener(listener);
      }
      return ContainerUtil.newHashMap();
    }

    synchronized (this) {
      final OutlineCache entry = new OutlineCache(outline, file);
      cache.put(path, entry);
      return entry.callToTestType;
    }
  }

  @Nullable
  protected TestType findNamedTestCall(@NotNull PsiElement element) {
    if (element instanceof DartCallExpression call) {
      return getTestsFromOutline(element.getContainingFile()).get(call.getTextOffset());
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

    return StringUtil.escapeProperty(name, false);
  }

  /**
   * Finds the {@link DartCallExpression} in the key set of {@param callToTestType} and also the closest parent of {@param element}.
   */
  @Nullable
  private DartCallExpression findEnclosingTestCall(@NotNull PsiElement element, @NotNull Map<Integer, TestType> callToTestType) {
    while (element != null) {
      if (element instanceof DartCallExpression call) {
        if (callToTestType.containsKey(call.getTextOffset())) {
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
   * The cache of listeners for the path of each {@link PsiFile} that has an outded {@link FlutterOutline}.
   */
  private static final Map<String, LineMarkerUpdatingListener> listenerCache = ContainerUtil.newHashMap();

  private LineMarkerUpdatingListener getListenerForFile(@NotNull final PsiFile file) {
    final String path = file.getVirtualFile().getCanonicalPath();
    final ActiveEditorsOutlineService service = getActiveEditorsOutlineService(file.getProject());
    if (!listenerCache.containsKey(path) && service != null) {
      listenerCache.put(path, new LineMarkerUpdatingListener(this, file.getProject(), service));
      var disposableParent = FlutterDartAnalysisServer.getInstance(file.getProject());
      Disposer.register(disposableParent, () -> {
        listenerCache.remove(path);
      });
    }
    return listenerCache.get(path);
  }

  private static class OutlineCache {
    final Map<Integer, TestType> callToTestType;
    final FlutterOutline outline;

    private OutlineCache(FlutterOutline outline, PsiFile file) {
      this.callToTestType = ContainerUtil.newHashMap();
      this.outline = outline;

      populateTestTypeMap(outline, file);
    }

    /**
     * Traverses the {@param outline} tree and adds to {@link OutlineCache#callToTestType } the {@link DartCallExpression}s that are tests or test groups.
     */
    private void populateTestTypeMap(@NotNull FlutterOutline outline, @NotNull PsiFile file) {
      if (outline.getDartElement() != null) {
        final PsiElement element;

        switch (outline.getDartElement().getKind()) {
          case UNIT_TEST_GROUP: {
            // We found a test group.
            element = file.findElementAt(outline.getOffset());
            final DartCallExpression enclosingCall = DartSyntax.findClosestEnclosingFunctionCall(element);
            if (enclosingCall != null) {
              callToTestType.put(enclosingCall.getTextOffset(), TestType.GROUP);
            }
          }
          break;

          case UNIT_TEST_TEST: {
            // We found a unit test.
            element = file.findElementAt(outline.getOffset());
            final DartCallExpression enclosingCall = DartSyntax.findClosestEnclosingFunctionCall(element);
            if (enclosingCall != null) {
              callToTestType.put(enclosingCall.getTextOffset(), TestType.SINGLE);
            }
          }
          break;

          default:
            // We found no test.
            break;
        }
      }

      if (outline.getChildren() != null) {
        for (FlutterOutline child : outline.getChildren()) {
          populateTestTypeMap(child, file);
        }
      }
    }
  }

  /**
   * {@link ActiveEditorsOutlineService.Listener} that forces IntelliJ to recompute line markers and other file annotations when the
   * {@link FlutterOutline} updates.
   *
   * <p>
   * Used to ensure that we don't get stuck with out-of-date line markers.
   */
  private record LineMarkerUpdatingListener(@NotNull CommonTestConfigUtils commonTestConfigUtils, @NotNull Project project,
                                            @NotNull ActiveEditorsOutlineService service) implements ActiveEditorsOutlineService.Listener {

    @Override
    public void onOutlineChanged(@NotNull String filePath, @Nullable FlutterOutline outline) {
      commonTestConfigUtils.clearCachedInfo(filePath);
      forceFileAnnotation();
      service.removeListener(this);
    }

    // TODO(djshuckerow): this can be merged with the Dart plugin's forceFileAnnotation:
    // https://github.com/JetBrains/intellij-plugins/blob/master/Dart/src/com/jetbrains/lang/dart/analyzer/DartServerData.java#L300
    private void forceFileAnnotation() {
      // It's ok to call DaemonCodeAnalyzer.restart() right in this thread, without invokeLater(),
      // but it will cache RemoteAnalysisServerImpl$ServerResponseReaderThread in FileStatusMap.threads and as a result,
      // DartAnalysisServerService.myProject will be leaked in tests

      OpenApiUtils.safeInvokeLater(
        () -> DaemonCodeAnalyzer.getInstance(project).restart(),
        ModalityState.defaultModalityState(),
        project.getDisposed()
      );
    }
  }
}
