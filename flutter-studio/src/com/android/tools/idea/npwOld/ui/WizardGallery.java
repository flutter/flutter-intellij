/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npwOld.ui;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;

import com.android.tools.adtui.ASGallery;
import com.google.common.base.Function;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import java.awt.Dimension;
import java.awt.Image;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * The Wizard gallery widget for displaying a collection of images and labels.
 * <p/>
 * Relies on two functions to obtain the image and label for the model object.
 */
public final class WizardGallery<E> extends ASGallery<E> {
  public WizardGallery(@NotNull String title,
                       @NotNull Function<? super E, Icon> imageProvider,
                       @NotNull Function<? super E, String> labelProvider) {
    super(JBList.createDefaultListModel(), imageProvider, labelProvider, DEFAULT_GALLERY_THUMBNAIL_SIZE, null, false);

    setBorder(BorderFactory.createLineBorder(JBColor.border()));
    getAccessibleContext().setAccessibleDescription(title);
  }

  @NotNull
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    Dimension cellSize = computeCellSize();
    int heightInsets = getInsets().top + getInsets().bottom;
    int widthInsets = getInsets().left + getInsets().right;
    // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
    return new Dimension(cellSize.width * 5 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
  }
}
