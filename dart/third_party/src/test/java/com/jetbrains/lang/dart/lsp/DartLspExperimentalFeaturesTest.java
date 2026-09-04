// Copyright 2026 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.jetbrains.lang.dart.lsp;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.sdk.DartConfigurable;

public class DartLspExperimentalFeaturesTest extends CodeInsightFixtureTestCase {

  @Override
  public void tearDown() throws Exception {
    try {
      PropertiesComponent.getInstance(getProject()).unsetValue("dart.lsp.experimental.enabled");
    } catch (Throwable e) {
      addSuppressedException(e);
    } finally {
      super.tearDown();
    }
  }

  public void testExperimentalFeaturesSettingDefault() {
    assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));
  }

  public void testToggleExperimentalFeaturesSetting() {
    assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", false, true);
    assertFalse(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));

    PropertiesComponent.getInstance(getProject()).setValue("dart.lsp.experimental.enabled", true, true);
    assertTrue(DartConfigurable.isExperimentalLspFeaturesEnabled(getProject()));
  }

  public void testLspMethodsExperimentalStatus() {
    assertFalse(LspMethod.DIAGNOSTIC_SERVER.isExperimental());
    assertFalse(LspMethod.HOVER.isExperimental());
    assertFalse(LspMethod.TYPE_DEFINITION.isExperimental());
    assertTrue(LspMethod.DEFINITION.isExperimental());
    assertTrue(LspMethod.DOCUMENT_HIGHLIGHT.isExperimental());

    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.DIAGNOSTIC_SERVER));
    assertFalse(LspMethod.Companion.getExperimentalFeatures().contains(LspMethod.HOVER));
  }
}
