/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

// TODO(quangson91): Figure out why color setting page display as alphabet
// Ref: https://github.com/flutter/flutter-intellij/pull/2394#discussion_r196756990
public final class FlutterLogColorPage implements ColorSettingsPage {
  @NotNull
  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new HashMap<>();
  @NotNull
  private static final String DEMO_TEXT = "Flutter log:\n" +
                                          "<none>02-02 18:52:57.132: NONE/FlutterIsCool: log message</none>\n" +
                                          "<finest>02-02 18:52:57.132: FINEST/FlutterIsCool: log message</finest>\n" +
                                          "<finer>02-02 18:52:57.132: FINER/FlutterIsCool: log message</finer>\n" +
                                          "<fine>02-02 18:52:57.132: FINE/FlutterIsCool: log message</fine>\n" +
                                          "<config>02-02 18:52:57.132: CONFIG/FlutterIsCool: log message</config>\n" +
                                          "<info>02-02 18:52:57.132: INFO/FlutterIsCool: log message</info>\n" +
                                          "<warning>02-02 18:52:57.132: WARNING/FlutterIsCool: log message</warning>\n" +
                                          "<severe>02-02 18:52:57.132: SEVERE/FlutterIsCool: log message</severe>\n" +
                                          "<shout>02-02 18:52:57.132: SHOUT/FlutterIsCool: log message</shout>";

  static {
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("none", FlutterLogConstants.NONE_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("finest", FlutterLogConstants.FINEST_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("finer", FlutterLogConstants.FINER_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("fine", FlutterLogConstants.FINE_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("config", FlutterLogConstants.CONFIG_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("info", FlutterLogConstants.INFO_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("warning", FlutterLogConstants.WARNING_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("severe", FlutterLogConstants.SEVERE_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("shout", FlutterLogConstants.SHOUT_OUTPUT_KEY);
  }

  @NotNull
  private static final AttributesDescriptor[] ATTRIBUTES_DESCRIPTORS =
    new AttributesDescriptor[]{
      new AttributesDescriptor(FlutterBundle.message("none.level.title"), FlutterLogConstants.NONE_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("finest.level.title"), FlutterLogConstants.FINEST_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("finer.level.title"), FlutterLogConstants.FINER_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("fine.level.title"), FlutterLogConstants.FINE_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("config.level.title"), FlutterLogConstants.CONFIG_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("info.level.title"), FlutterLogConstants.INFO_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("warning.level.title"), FlutterLogConstants.WARNING_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("severe.level.title"), FlutterLogConstants.SEVERE_OUTPUT_KEY),
      new AttributesDescriptor(FlutterBundle.message("shout.level.title"), FlutterLogConstants.SHOUT_OUTPUT_KEY)};

  @Override
  @NotNull
  public String getDisplayName() {
    return "Flutter Log";
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRIBUTES_DESCRIPTORS;
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @Override
  @NotNull
  public String getDemoText() {
    return DEMO_TEXT;
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }
}
