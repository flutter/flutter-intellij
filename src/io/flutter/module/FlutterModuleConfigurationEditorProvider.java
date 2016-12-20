/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;

public class FlutterModuleConfigurationEditorProvider implements ModuleConfigurationEditorProvider {
  @Override
  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    final Module module = state.getRootModel().getModule();

    if (ModuleType.get(module) != FlutterModuleType.getInstance()) {
      return ModuleConfigurationEditor.EMPTY;
    }
    else {
      return new ModuleConfigurationEditor[]{new CommonContentEntriesEditor(module.getName(), state)};
    }
  }
}
