/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.flutter.android.GradleDependencyFetcher;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class GradleDependencyFetcherTest {

  private static Project ourProject;

  @Test
  public void parseDependencies() {
    GradleDependencyFetcher fetcher = new GradleDependencyFetcher(ourProject);
    fetcher.parseDependencies(createMockDependencyReport());
    Map<String, List<String>> deps = fetcher.getDependencies();
    assertEquals(14, deps.size());
    List<String> section = deps.get("debugAndroidTestCompileClasspath");
    assertNotNull(section);
    assertEquals(15, section.size());
    assertTrue(section.contains("org.hamcrest:hamcrest-core:1.3@jar"));
    assertFalse(deps.containsKey(":app:androidDependencies"));
    assertFalse(deps.containsKey("debug"));
    assertTrue(deps.containsKey("debugCompileClasspath"));
    assertTrue(deps.containsKey("releaseUnitTestRuntimeClasspath"));
    section = deps.get("profileCompileClasspath");
    assertEquals(1, section.size());
  }

  @Test
  public void parseFailure1() {
    GradleDependencyFetcher fetcher = new GradleDependencyFetcher(ourProject);
    fetcher.parseDependencies("");
    assertEquals(0, fetcher.getDependencies().size());
  }

  @Test
  public void parseFailure2() {
    GradleDependencyFetcher fetcher = new GradleDependencyFetcher(ourProject);
    fetcher.parseDependencies(createMockDependencyReport().substring(0, 85));
    assertEquals(0, fetcher.getDependencies().size());
  }

  @BeforeClass
  public static void setUp() {
    Disposable disposable = Disposer.newDisposable();
    ApplicationManager.setApplication(new MockApplication(disposable), disposable);
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    ourProject = new MockProject(ApplicationManager.getApplication().getPicoContainer(), disposable);
  }

  private static String createMockDependencyReport() {
    return
      ":app:androidDependencies\n" +
      "debug\n" +
      "debugCompileClasspath - Dependencies for compilation\n" +
      "+--- /Users/test/AndroidStudioProjects/flutter_appx/build/app/intermediates/flutter/flutter-x86.jar\n" +
      "\\--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm/flutter.jar\n" +
      "\n" +
      "debugRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "+--- /Users/test/AndroidStudioProjects/flutter_appx/build/app/intermediates/flutter/flutter-x86.jar\n" +
      "\\--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm/flutter.jar\n" +
      "\n" +
      "debugAndroidTest\n" +
      "debugAndroidTestCompileClasspath - Dependencies for compilation\n" +
      "+--- /Users/test/AndroidStudioProjects/flutter_appx/build/app/intermediates/flutter/flutter-x86.jar\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm/flutter.jar\n" +
      "+--- com.android.support.test.espresso:espresso-core:3.0.1@aar\n" +
      "+--- com.android.support.test:rules:1.0.1@aar\n" +
      "+--- com.android.support.test:runner:1.0.1@aar\n" +
      "+--- com.android.support:support-annotations:25.4.0@jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "+--- net.sf.kxml:kxml2:2.3.0@jar\n" +
      "+--- com.android.support.test.espresso:espresso-idling-resource:3.0.1@aar\n" +
      "+--- com.squareup:javawriter:2.1.1@jar\n" +
      "+--- javax.inject:javax.inject:1@jar\n" +
      "+--- org.hamcrest:hamcrest-integration:1.3@jar\n" +
      "+--- org.hamcrest:hamcrest-library:1.3@jar\n" +
      "+--- com.google.code.findbugs:jsr305:2.0.1@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "debugAndroidTestRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "+--- com.android.support.test.espresso:espresso-core:3.0.1@aar\n" +
      "+--- com.android.support.test:rules:1.0.1@aar\n" +
      "+--- com.android.support.test:runner:1.0.1@aar\n" +
      "+--- com.android.support:support-annotations:25.4.0@jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "+--- net.sf.kxml:kxml2:2.3.0@jar\n" +
      "+--- com.android.support.test.espresso:espresso-idling-resource:3.0.1@aar\n" +
      "+--- com.squareup:javawriter:2.1.1@jar\n" +
      "+--- javax.inject:javax.inject:1@jar\n" +
      "+--- org.hamcrest:hamcrest-integration:1.3@jar\n" +
      "+--- org.hamcrest:hamcrest-library:1.3@jar\n" +
      "+--- com.google.code.findbugs:jsr305:2.0.1@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "debugUnitTest\n" +
      "debugUnitTestCompileClasspath - Dependencies for compilation\n" +
      "+--- /Users/test/AndroidStudioProjects/flutter_appx/build/app/intermediates/flutter/flutter-x86.jar\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm/flutter.jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "debugUnitTestRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "+--- /Users/test/AndroidStudioProjects/flutter_appx/build/app/intermediates/flutter/flutter-x86.jar\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm/flutter.jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "profile\n" +
      "profileCompileClasspath - Dependencies for compilation\n" +
      "\\--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-profile/flutter.jar\n" +
      "\n" +
      "profileRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "\\--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-profile/flutter.jar\n" +
      "\n" +
      "profileUnitTest\n" +
      "profileUnitTestCompileClasspath - Dependencies for compilation\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-profile/flutter.jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "profileUnitTestRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-profile/flutter.jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "release\n" +
      "releaseCompileClasspath - Dependencies for compilation\n" +
      "\\--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-release/flutter.jar\n" +
      "\n" +
      "releaseRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "\\--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-release/flutter.jar\n" +
      "\n" +
      "releaseUnitTest\n" +
      "releaseUnitTestCompileClasspath - Dependencies for compilation\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-release/flutter.jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "releaseUnitTestRuntimeClasspath - Dependencies for runtime/packaging\n" +
      "+--- /Users/test/src/flutter/flutter/bin/cache/artifacts/engine/android-arm-release/flutter.jar\n" +
      "+--- junit:junit:4.12@jar\n" +
      "\\--- org.hamcrest:hamcrest-core:1.3@jar\n" +
      "\n" +
      "BUILD SUCCESSFUL in 0s\n" +
      "1 actionable task: 1 executed\n";
  }
}
