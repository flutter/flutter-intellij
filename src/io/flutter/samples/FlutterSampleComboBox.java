/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class FlutterSampleComboBox extends ComboBox<FlutterSample> {

  private static final class SampleModel extends AbstractListModel<FlutterSample>
    implements ComboBoxModel<FlutterSample> {
    @NotNull
    private final List<FlutterSample> myList;
    private FlutterSample mySelected;

    SampleModel(@NotNull List<FlutterSample> samples) {
      myList = samples;
      mySelected = !myList.isEmpty() ? myList.get(0) : null;
    }

    @Override
    public int getSize() {
      return myList.size();
    }

    @Override
    public FlutterSample getElementAt(int index) {
      return myList.get(index);
    }

    @Override
    public void setSelectedItem(Object item) {
      setSelectedItem((FlutterSample)item);
    }

    @Override
    public FlutterSample getSelectedItem() {
      return mySelected;
    }

    public void setSelectedItem(FlutterSample item) {
      mySelected = item;
      fireContentsChanged(this, 0, getSize());
    }
  }

  private class SampleCellRenderer extends ColoredListCellRenderer<FlutterSample> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends FlutterSample> list,
                                         FlutterSample sample,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      final SimpleTextAttributes style = isEnabled() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
      append(sample.getDisplayLabel(), style);
    }
  }

  FlutterSampleComboBox(@NotNull List<FlutterSample> samples) {
    super(new SampleModel(samples));
    setRenderer(new SampleCellRenderer());
  }

  @NotNull
  @Override
  public FlutterSample getSelectedItem() {
    final FlutterSample selected = (FlutterSample)super.getSelectedItem();
    return selected != null ? selected : getModel().getElementAt(0);
  }
}
