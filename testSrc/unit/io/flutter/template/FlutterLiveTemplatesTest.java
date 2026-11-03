/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.template;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.ide.template.DartTemplateContextType;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for Flutter live template XML registrations and {@link DartToplevelTemplateContextType}.
 */
public class FlutterLiveTemplatesTest {

  private static final String FLUTTER_GROUP = "Flutter";

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void defaultLiveTemplatesAreLoadedFromDeclarativeRegistration() {
    final TemplateSettings settings = TemplateSettings.getInstance();
    assertNotNull(settings);

    final DartToplevelTemplateContextType topLevelContext =
      TemplateContextTypes.getByClass(DartToplevelTemplateContextType.class);

    for (String key : List.of("stless", "stful", "inh", "stanim")) {
      final TemplateImpl template = settings.getTemplate(key, FLUTTER_GROUP);
      assertNotNull("Expected Flutter live template '" + key + "' to be registered", template);
      assertTrue(
        "Expected '" + key + "' to be enabled in DART_TOPLEVEL context",
        template.getTemplateContext().isEnabled(topLevelContext)
      );
    }

    for (String key : List.of("thof", "ihof", "mdof")) {
      final TemplateImpl template = settings.getTemplate(key, FLUTTER_GROUP);
      assertNotNull("Expected Flutter live template '" + key + "' to be registered", template);
      assertFalse(
        "Expected '" + key + "' not to be directly enabled in DART_TOPLEVEL context",
        template.getTemplateContext().isEnabled(topLevelContext)
      );
    }
  }

  @Test
  public void dartToplevelContextRegistrationAndHierarchy() {
    final DartToplevelTemplateContextType context =
      TemplateContextTypes.getByClass(DartToplevelTemplateContextType.class);
    assertNotNull(context);
    assertEquals("DART_TOPLEVEL", context.getContextId());
    assertEquals("Top-level", context.getPresentableName());

    final TemplateContextType baseContext = context.getBaseContextType();
    assertNotNull("Expected baseContextId=\"DART\" to resolve a parent context", baseContext);
    assertEquals("DART", baseContext.getContextId());
    assertTrue(baseContext instanceof DartTemplateContextType.Generic);
  }

  @Test
  public void dartToplevelContextMatchesTopLevelAndRejectsClassBodyAndComments() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final DartToplevelTemplateContextType context =
        TemplateContextTypes.getByClass(DartToplevelTemplateContextType.class);
      final PsiFileFactory factory =
        PsiFileFactory.getInstance(Objects.requireNonNull(fixture.getProject()));

      final String code =
        "// header comment\n" +
        "void topLevelAnchor() {}\n" +
        "class MyWidget {\n" +
        "  void insideClassAnchor() {}\n" +
        "}\n";
      final PsiFile dartFile = factory.createFileFromText("main.dart", DartLanguage.INSTANCE, code);

      final int commentOffset = code.indexOf("header comment");
      final int topLevelOffset = code.indexOf("void topLevelAnchor");
      final int classBodyOffset = code.indexOf("void insideClassAnchor");

      assertFalse(
        "Should not match inside a Dart comment",
        context.isInContext(TemplateActionContext.expanding(dartFile, commentOffset))
      );
      assertTrue(
        "Should match at Dart top-level",
        context.isInContext(TemplateActionContext.expanding(dartFile, topLevelOffset))
      );
      assertFalse(
        "Should not match inside a DartClassDefinition",
        context.isInContext(TemplateActionContext.expanding(dartFile, classBodyOffset))
      );

      final PsiFile plainFile =
        factory.createFileFromText("notes.txt", PlainTextLanguage.INSTANCE, "plain text");
      assertFalse(
        "Should not match outside Dart files",
        context.isInContext(TemplateActionContext.expanding(plainFile, 0))
      );
    });
  }
}
