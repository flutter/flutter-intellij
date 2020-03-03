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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.intellij.openapi.components.PersistentStateComponent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

/**
 * Panel which wraps a {@link TextAsset}, allowing the user to enter text value and choose a font
 * from a pulldown.
 */
public final class TextAssetEditor extends JPanel implements AssetComponent<TextAsset>, PersistentStateComponent<PersistentState> {
  private static final String TEXT_PROPERTY = "text";
  private static final String FONT_FAMILY_PROPERTY = "fontFamily";

  private final TextAsset myTextAsset = new TextAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myListeners = new ArrayList<>(1);

  public TextAssetEditor() {
    super(new TabularLayout("50px,180px"));

    JTextField textField = new JTextField();
    List<String> fontFamilies = TextAsset.getAllFontFamilies();
    //noinspection UndesirableClassUsage
    JComboBox<String> fontCombo = new JComboBox<>(fontFamilies.toArray(new String[0]));

    add(textField, new TabularLayout.Constraint(0, 0));
    add(fontCombo, new TabularLayout.Constraint(0, 1));

    myBindings.bindTwoWay(new TextProperty(textField), myTextAsset.text());

    SelectedItemProperty<String> selectedFont = new SelectedItemProperty<>(fontCombo);
    myBindings.bindTwoWay(ObjectProperty.wrap(selectedFont), myTextAsset.fontFamily());

    InvalidationListener onTextChanged = sender -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myListeners) {
        listener.actionPerformed(e);
      }
    };

    myTextAsset.text().addListener(onTextChanged);
    myTextAsset.fontFamily().addListener(onTextChanged);
  }

  @Override
  @NotNull
  public TextAsset getAsset() {
    return myTextAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.clear();
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(TEXT_PROPERTY, myTextAsset.text().get(), TextAsset.DEFAULT_TEXT);
    state.set(FONT_FAMILY_PROPERTY, myTextAsset.fontFamily().get(), myTextAsset.defaultFontFamily());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myTextAsset.text().set(state.get(TEXT_PROPERTY, TextAsset.DEFAULT_TEXT));
    myTextAsset.fontFamily().set(state.get(FONT_FAMILY_PROPERTY, myTextAsset.defaultFontFamily()));
  }
}
