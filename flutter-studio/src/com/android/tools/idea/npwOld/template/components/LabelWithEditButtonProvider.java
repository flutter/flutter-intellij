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
package com.android.tools.idea.npwOld.template.components;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.templates.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a {@link LabelWithEditButton} for more advanced {@link Parameter.Type#STRING}
 * parameters that only users who know what they're doing should modify.
 */
public final class LabelWithEditButtonProvider extends ParameterComponentProvider<LabelWithEditButton> {
  public LabelWithEditButtonProvider(@NotNull Parameter parameter) {
    super(parameter);
  }

  @NotNull
  @Override
  protected LabelWithEditButton createComponent(@NotNull Parameter parameter) {
    return new LabelWithEditButton();
  }

  @Nullable
  @Override
  public AbstractProperty<?> createProperty(@NotNull LabelWithEditButton editLink) {
    return new TextProperty(editLink);
  }
}
