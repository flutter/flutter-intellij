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
package com.android.tools.idea.npwOld.template.components;

import com.android.tools.idea.npwOld.platform.Language;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a combobox which presents the user with a list of Programming Languages.
 */
public final class LanguageComboProvider extends ComponentProvider<JComboBox> {
  @NotNull
  @Override
  public JComboBox<Language> createComponent() {
    JComboBox<Language> languageCombo = new ComboBox<>(new DefaultComboBoxModel<>(Language.values()));
    languageCombo.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(JList list, Language value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    });
    languageCombo.setToolTipText("The programming language used for code generation");
    return languageCombo;
  }

  @NotNull
  @Override
  public AbstractProperty<?> createProperty(@NotNull JComboBox sourceSetCombo) {
    return new SelectedItemProperty<String>(sourceSetCombo);
  }
}

