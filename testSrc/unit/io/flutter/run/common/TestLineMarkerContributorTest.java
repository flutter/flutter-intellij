/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import io.flutter.AbstractDartElementTest;
import org.junit.Test;

import java.util.Date;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Verifies {@link TestLineMarkerContributor}.
 */
public class TestLineMarkerContributorTest extends AbstractDartElementTest {
  private final CommonTestConfigUtils testConfigUtils = new CommonTestConfigUtils() {
  };
  private final TestLineMarkerContributor contributor = new TestLineMarkerContributor(testConfigUtils) {
  };

  private static TestStateStorage.Record recordWithMagnitude(int magnitude) {
    return new TestStateStorage.Record(magnitude, new Date(), 0L, 0, "", "", "");
  }

  @Test
  public void getTestStateIconMapsMagnitudesToExpectedIcons() {
    // Passed / Complete (1) -> Green2
    assertEquals(
      AllIcons.RunConfigurations.TestState.Green2,
      TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(1)));

    // Ignored (5) -> Yellow2
    assertEquals(
      AllIcons.RunConfigurations.TestState.Yellow2,
      TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(5)));

    // Failed (6) and Error (8) -> Red2
    assertEquals(
      AllIcons.RunConfigurations.TestState.Red2,
      TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(6)));
    assertEquals(
      AllIcons.RunConfigurations.TestState.Red2,
      TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(8)));

    // Non-terminal or unmapped magnitudes (0 = skipped, 2 = not run, 3 = running, 4 = terminated) -> null
    assertNull(TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(0)));
    assertNull(TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(2)));
    assertNull(TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(3)));
    assertNull(TestLineMarkerContributor.getTestStateIcon(recordWithMagnitude(4)));
  }

  @Test
  public void getInfoUsesStoredTestStateAndFallsBackToDefaultIcon() throws Exception {
    run(() -> {
      final PsiElement testKeyword = setUpDartElement(
        "main() {\n  test('my unit test', () {});\n}", "test", LeafPsiElement.class);
      final Project project = Objects.requireNonNull(fixture.getProject());
      final String path = FileUtil.toSystemIndependentName(
        testKeyword.getContainingFile().getVirtualFile().getPath());
      final String url = "dart_location://" + path + ",1,2,[\"my unit test\"]";
      final TestStateStorage storage = TestStateStorage.getInstance(project);
      storage.removeState(url);

      try {
        final RunLineMarkerContributor.Info defaultInfo = contributor.getInfo(testKeyword);
        assertNotNull(defaultInfo);
        assertEquals(TestType.SINGLE.getIcon(), defaultInfo.icon);

        storage.writeState(url, recordWithMagnitude(1));
        final RunLineMarkerContributor.Info passedInfo = contributor.getInfo(testKeyword);
        assertNotNull(passedInfo);
        assertEquals(AllIcons.RunConfigurations.TestState.Green2, passedInfo.icon);

        storage.writeState(url, recordWithMagnitude(5));
        final RunLineMarkerContributor.Info ignoredInfo = contributor.getInfo(testKeyword);
        assertNotNull(ignoredInfo);
        assertEquals(AllIcons.RunConfigurations.TestState.Yellow2, ignoredInfo.icon);

        storage.writeState(url, recordWithMagnitude(6));
        final RunLineMarkerContributor.Info failedInfo = contributor.getInfo(testKeyword);
        assertNotNull(failedInfo);
        assertEquals(AllIcons.RunConfigurations.TestState.Red2, failedInfo.icon);
      }
      finally {
        storage.removeState(url);
      }
    });
  }
}
