/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartComponent;
import com.jetbrains.lang.dart.psi.DartComponentName;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import com.jetbrains.lang.dart.resolve.DartResolveProcessor;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import io.flutter.dart.DartSyntax;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common utilities for processing Flutter tests.
 * <p>
 * This class is useful for identifying the {@link TestType} of different Dart objects
 */
public abstract class CommonTestConfigUtils {
  private static final String GROUP = "group";
  private static final String TEST = "test";
  private static final String TEST_WIDGETS = "testWidgets";
  private static final String IS_TEST = "isTest";
  private static final String IS_TEST_GROUP = "isTestGroup";
  private static final Key<CachedValue<Map<String, Optional<TestType>>>> CUSTOM_TEST_CACHE_KEY = Key.create("CUSTOM_TEST_CACHE_KEY");

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
   * <li>{@link TestType#SINGLE} if the call is a {@link DartCallExpression} for a single test (such as {@code test}, {@code testWidgets}, or a function annotated with {@code @isTest})</li>
   * <li>{@link TestType#GROUP} if the call is a {@link DartCallExpression} for a test group (such as {@code group} or a function annotated with {@code @isTestGroup})</li>
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

  @Nullable
  protected TestType findNamedTestCall(@NotNull PsiElement element) {
    if (element instanceof DartCallExpression call) {
      return getTestType(call);
    }
    return null;
  }

  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  @Nullable
  public String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    final DartCallExpression call = findEnclosingTestCall(elt);
    if (call == null) return null;

    return extractTestName(call);
  }

  @VisibleForTesting
  @Nullable
  public String extractTestName(@NotNull DartCallExpression call) {
    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    return DartSyntax.unquote(lit);
  }

  /**
   * Finds the closest enclosing {@link DartCallExpression} of {@param element} that is a test or group call.
   */
  @Nullable
  private DartCallExpression findEnclosingTestCall(@NotNull PsiElement element) {
    while (element != null) {
      if (element instanceof DartCallExpression call) {
        if (findNamedTestCall(call) != null) {
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

  @Nullable
  private static TestType getTestType(@NotNull DartCallExpression call) {
    if (DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class) == null) {
      return null;
    }

    final String functionName = DartSyntax.getCalledFunctionName(call);
    if (functionName == null || functionName.isEmpty()) {
      return null;
    }

    if (GROUP.equals(functionName)) {
      return TestType.GROUP;
    }
    if (TEST.equals(functionName) || TEST_WIDGETS.equals(functionName)) {
      return TestType.SINGLE;
    }

    return findCustomTestAnnotationType(call, functionName);
  }

  @Nullable
  private static TestType findCustomTestAnnotationType(@NotNull DartCallExpression call, @NotNull String functionName) {
    final PsiFile file = call.getContainingFile();
    if (file == null) {
      return null;
    }

    final Map<String, Optional<TestType>> cache = CachedValuesManager.getCachedValue(file, CUSTOM_TEST_CACHE_KEY, () ->
      CachedValueProvider.Result.create(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT)
    );

    return cache.computeIfAbsent(functionName, name -> {
      for (DartComponent component : PsiTreeUtil.getChildrenOfTypeAsList(file, DartComponent.class)) {
        if (name.equals(component.getName())) {
          final TestType type = getTestTypeFromMetadata(component);
          if (type != null) {
            return Optional.of(type);
          }
        }
      }

      final VirtualFile virtualFile = DartResolveUtil.getRealVirtualFile(file);
      if (virtualFile == null) {
        return Optional.empty();
      }

      final List<DartComponentName> componentNames = new ArrayList<>();
      DartResolveUtil.processTopLevelDeclarations(
        call,
        new DartResolveProcessor(componentNames, name),
        virtualFile,
        name
      );

      for (DartComponentName componentName : componentNames) {
        final PsiElement parent = componentName.getParent();
        if (parent instanceof DartComponent component) {
          final TestType type = getTestTypeFromMetadata(component);
          if (type != null) {
            return Optional.of(type);
          }
        }
      }

      return Optional.empty();
    }).orElse(null);
  }

  @Nullable
  private static TestType getTestTypeFromMetadata(@NotNull DartComponent component) {
    if (component.getMetadataByName(IS_TEST) != null) {
      return TestType.SINGLE;
    }
    if (component.getMetadataByName(IS_TEST_GROUP) != null) {
      return TestType.GROUP;
    }
    return null;
  }
}
