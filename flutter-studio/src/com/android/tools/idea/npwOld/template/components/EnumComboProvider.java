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

import com.android.SdkConstants;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Provides a combobox well suited for handling {@link Parameter.Type#ENUM} parameters.
 */
// Disable JComboBox warnings. We have no choice but to use it as we're stuck with JDK6
@SuppressWarnings({"UndesirableClassUsage", "unchecked"})
public final class EnumComboProvider extends ParameterComponentProvider<JComboBox> {
  public EnumComboProvider(@NotNull Parameter parameter) {
    super(parameter);
  }

  /**
   * Parse an enum option, which looks something like this:
   *
   * {@code
   * <option id="choice_id" minApi="15" minBuildApi="17">Choice Description</option>
   * }
   */
  private static ApiComboBoxItem<String> createItemForOption(@NotNull Parameter parameter, @NotNull Element option) {
    String optionId = option.getAttribute(SdkConstants.ATTR_ID);
    assert optionId != null && !optionId.isEmpty() : SdkConstants.ATTR_ID;
    NodeList childNodes = option.getChildNodes();
    assert childNodes.getLength() == 1 && childNodes.item(0).getNodeType() == Node.TEXT_NODE;
    String optionLabel = childNodes.item(0).getNodeValue().trim();
    int minSdk = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_API, parameter.name, 1);
    int minBuildApi = getIntegerOptionValue(option, TemplateMetadata.ATTR_MIN_BUILD_API, parameter.name, 1);
    return new ApiComboBoxItem<>(optionId, optionLabel, minSdk, minBuildApi);
  }

  /**
   * Helper method to parse any integer attributes found in an option enumeration.
   */
  private static int getIntegerOptionValue(@NotNull Element option, String attribute, @Nullable String parameterName, int defaultValue) {
    String stringValue = option.getAttribute(attribute);
    try {
      return StringUtil.isEmpty(stringValue) ? defaultValue : Integer.parseInt(stringValue);
    }
    catch (Exception e) {
      getLog().warn(String.format("Invalid %1$s value (%2$s) for option %3$s in parameter %4$s", attribute, stringValue,
                                  option.getAttribute(SdkConstants.ATTR_ID), parameterName), e);
      return defaultValue;
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(EnumComboProvider.class);
  }

  @NotNull
  @Override
  protected JComboBox createComponent(@NotNull Parameter parameter) {
    List<Element> options = parameter.getOptions();
    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();

    assert !options.isEmpty();
    for (Element option : options) {
      comboBoxModel.addElement(createItemForOption(parameter, option));
    }
    return new JComboBox(comboBoxModel);
  }

  @Nullable
  @Override
  public AbstractProperty<?> createProperty(@NotNull final JComboBox comboBox) {
    return new ApiComboBoxTextProperty(comboBox);
  }

  /**
   * Swing property which interacts with {@link ApiComboBoxItem}s.
   *
   * NOTE: This is currently only needed here but we can promote it to ui.wizard.properties if it's
   * ever needed in more places.
   */
  private static class ApiComboBoxTextProperty extends AbstractProperty<String> implements ActionListener {
    @NotNull private final JComboBox myComboBox;

    public ApiComboBoxTextProperty(@NotNull JComboBox comboBox) {
      myComboBox = comboBox;
      myComboBox.addActionListener(this);
    }

    @Override
    protected void setDirectly(@NotNull String value) {
      int index = -1;
      DefaultComboBoxModel model = ((DefaultComboBoxModel)myComboBox.getModel());
      for (int i = 0; i < model.getSize(); i++) {
        ApiComboBoxItem<String> item = ((ApiComboBoxItem<String>)model.getElementAt(i));
        if (value.equals(item.getData())) {
          index = i;
          break;
        }
      }
      myComboBox.setSelectedIndex(index);
    }

    @NotNull
    @Override
    public String get() {
      ApiComboBoxItem<String> item = (ApiComboBoxItem<String>)myComboBox.getSelectedItem();
      return item != null ? item.getData() : "";
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      notifyInvalidated();
    }
  }
}
