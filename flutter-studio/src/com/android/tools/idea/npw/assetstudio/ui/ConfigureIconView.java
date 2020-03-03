/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.observable.core.StringProperty;
import com.intellij.openapi.components.PersistentStateComponent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public interface ConfigureIconView extends PersistentStateComponent<PersistentState> {
  /**
   * Returns the root panel for this view.
   */
  @NotNull
  JComponent getRootComponent();

  /**
   * Adds a listener which will be triggered whenever the asset represented by this view is
   * modified in any way.
   */
  void addAssetListener(@NotNull ActionListener listener);

  /**
   * The asset output name.
   */
  @NotNull
  StringProperty outputName();

  /**
   * Returns the {@link IconGenerator} for this view.
   */
  @NotNull
  IconGenerator getIconGenerator();
}
