/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.template.components;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.google.common.base.Strings;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A class responsible for converting a {@link Parameter} to a {@link JComponent}. Any parameter
 * that represents a value (most of them, except for e.g. {@link Parameter.Type#SEPARATOR}) should
 * be sure to also create an appropriate Swing property to control the component.
 */
public abstract class ParameterComponentProvider<T extends JComponent> extends ComponentProvider<T> {

  @NotNull private final Parameter myParameter;

  protected ParameterComponentProvider(@NotNull Parameter parameter) {
    myParameter = parameter;
  }

  @NotNull
  @Override
  public final T createComponent() {
    T component = createComponent(myParameter);
    component.setToolTipText(WizardUtils.toHtmlString(Strings.nullToEmpty(myParameter.help)));
    return component;
  }

  @NotNull
  protected abstract T createComponent(@NotNull Parameter parameter);
}
