/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.importing;

import com.intellij.openapi.ui.MessageType;
import java.awt.event.ActionListener;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for controls that deal with module import setup
 */
public interface ModuleImportSettings {
  boolean isModuleSelected();

  void setModuleSelected(boolean selected);

  String getModuleName();

  void setModuleName(String moduleName);

  void setModuleSourcePath(String relativePath);

  void setCanToggleModuleSelection(boolean canToggleModuleSelection);

  void setCanRenameModule(boolean canRenameModule);

  void setValidationStatus(@Nullable MessageType statusSeverity, @Nullable String statusDescription);

  void setVisible(boolean visible);

  void addActionListener(ActionListener actionListener);
}
