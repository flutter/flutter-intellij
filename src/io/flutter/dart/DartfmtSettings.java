/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

import java.lang.reflect.Field;

/**
 * A class to set the default value for "use dartfmt when formatting the whole file" setting once.
 */
public class DartfmtSettings {
  private static final String codeStyleSettingsClass = "com.jetbrains.lang.dart.ide.application.options.DartCodeStyleSettings";

  private static final String oneTimeSetKey = "io.flutter.dartfmt.oneTimeSet";

  public static boolean hasBeenOneTimeSet() {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    return properties.getBoolean(oneTimeSetKey, false);
  }

  public static void setDartfmtValue() {
    if (!dartPluginHasSetting()) {
      return;
    }

    try {
      // CodeStyleSettingsManager.getSettings(project).getCustomSettings(DartCodeStyleSettings.class).DELEGATE_TO_DARTFMT
      final Class settingsClass = Class.forName(codeStyleSettingsClass);
      //noinspection unchecked
      final CustomCodeStyleSettings settings = CodeStyleSettingsManager.getInstance().getCurrentSettings().getCustomSettings(settingsClass);
      final Field delegateDartfmtField = settingsClass.getField("DELEGATE_TO_DARTFMT");
      delegateDartfmtField.setBoolean(settings, true);
    }
    catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
      return;
    }

    // Set the setting to remember that we toggled this value.
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(oneTimeSetKey, true);
  }

  public static boolean dartPluginHasSetting() {
    try {
      Class.forName(codeStyleSettingsClass);
      return true;
    }
    catch (Throwable t) {
      return false;
    }
  }
}
