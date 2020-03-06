/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npwOld.module;

import com.android.tools.idea.npwOld.FormFactor;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public interface ModuleTemplateGalleryEntry extends ModuleGalleryEntry {

  /**
   * @return The file from where this template was loaded.
   */
  @NotNull
  File getTemplateFile();

  /**
   * @return form factor associated with this template.
   */
  @NotNull
  FormFactor getFormFactor();

  /**
   * @return true if this template belongs to a Library.
   */
  boolean isLibrary();

  /**
   * @return true if this template belongs to an Instant App.
   */
  boolean isInstantApp();
}
