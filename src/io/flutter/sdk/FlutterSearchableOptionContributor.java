/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import org.jetbrains.annotations.NotNull;

public class FlutterSearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    add(processor, FlutterBundle.message("settings.try.out.features.still.under.development"));
    add(processor, FlutterBundle.message("settings.show.preview.area"));
    add(processor, FlutterBundle.message("settings.experimental.flutter.logging.view"));
    add(processor, FlutterBundle.message("settings.enable.android.gradle.sync"));
    // For some reason the word "report" is ignored by the search feature, but the other words work.
    add(processor, FlutterBundle.message("settings.report.google.analytics"));
    add(processor, FlutterBundle.message("settings.enable.verbose.logging"));
    add(processor, FlutterBundle.message("settings.format.code.on.save"));
    add(processor, FlutterBundle.message("settings.organize.imports.on.save"));
    add(processor, FlutterBundle.message("settings.flutter.version"));
    add(processor, FlutterBundle.message("settings.open.inspector.on.launch"));
    add(processor, FlutterBundle.message("settings.hot.reload.on.save"));
    add(processor, FlutterBundle.message("settings.disable.tracking.widget.creation"));
    add(processor, FlutterBundle.message("settings.enable.bazel.test.runner"));
  }

  private static void add(@NotNull SearchableOptionProcessor processor, @NotNull String key) {
    processor.addOptions(key, null, key, FlutterConstants.FLUTTER_SETTINGS_PAGE_ID,
                         FlutterSettingsConfigurable.FLUTTER_SETTINGS_PAGE_NAME, true);
  }
}
