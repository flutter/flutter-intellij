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
package com.android.tools.idea.npw.dynamicapp;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableString;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.bool.AndExpression;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.string.IsEmptyExpression;
import com.android.tools.idea.observable.expressions.string.TrimExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ModuleDownloadDeviceFeature {
  private JPanel myRootPanel;
  private JComboBox<DeviceFeatureKind> myFeatureNameCombo;
  private JPanel myFeatureValueContainer;
  private TextFieldWithAutoCompletion<String> myFeatureValueTextField;
  private LinkLabel<Void> myRemoveFeatureLinkLabel;
  private ObservableString deviceFeatureValueTrim;
  private static final CharMatcher DISALLOWED_IN_DEVICE_VALUE = CharMatcher.anyOf("<&\"");

  @NotNull
  private final DeviceFeatureModel myModel;

  @NotNull
  private final List<ModuleDownloadDeviceFeatureListener> myListeners = new ArrayList<>();

  @NotNull
  private final BindingsManager myBindings = new BindingsManager();

  @NotNull
  private final ListenerManager myBindingsListeners = new ListenerManager();

  public ModuleDownloadDeviceFeature(@NotNull Project project,
                                     @NotNull DeviceFeatureModel model,
                                     @NotNull ObservableValue<Boolean> isActive,
                                     @NotNull ValidatorPanel validator) {
    myModel = model;

    myFeatureNameCombo.setModel(new DefaultComboBoxModel<>(DeviceFeatureKind.values()));
    myFeatureValueTextField = TextFieldWithAutoCompletion.create(project, new ArrayList<>(), true, null);
    myFeatureValueContainer.add(myFeatureValueTextField, BorderLayout.CENTER);

    myRemoveFeatureLinkLabel.setIcon(StudioIcons.Common.CLOSE);

    UIUtil.addParentChangeListener(myRootPanel, new ActivationListener());

    // Invoke listeners when close button is pressed
    myRemoveFeatureLinkLabel.setListener((aSource, aLinkData) -> myListeners.forEach(x -> x.removeFeatureInvoked()), null);

    deviceFeatureValueTrim = new TrimExpression(myModel.deviceFeatureValue());
    // isActive && device feature value is empty
    BooleanExpression isInvalidExpression =
      new AndExpression(isActive, new IsEmptyExpression(deviceFeatureValueTrim));
    validator.registerValidator(isInvalidExpression, isInvalid -> isInvalid
                                                                  ? new Validator.Result(Validator.Severity.ERROR,
                                                                                         "Device feature value must be set")
                                                                  : Validator.Result.OK);

    validator.registerValidator(deviceFeatureValueTrim, value -> {
      int illegalCharIdx = DISALLOWED_IN_DEVICE_VALUE.indexIn(value);
      if (illegalCharIdx < 0) {
        return Validator.Result.OK;
      }
      else {
        return new Validator.Result(Validator.Severity.ERROR, String.format("Illegal character '%c' in %s '%s'",
                                                                            value.charAt(illegalCharIdx),
                                                                            myModel.deviceFeatureType(),
                                                                            myModel.deviceFeatureValue()));
      }
    });
  }

  @NotNull
  private static List<String> getModelForFeatureType(DeviceFeatureKind featureType) {
    switch (featureType) {
      case GL_ES_VERSION:
        return ImmutableList.of(
          "0x00020000",
          "0x00030000",
          "0x00030001");

      case NAME:
        // Note: From https://developer.android.com/reference/android/content/pm/PackageManager
        return ImmutableList.of(
          "android.hardware.audio.low_latency",
          "android.hardware.audio.output",
          "android.hardware.audio.pro",
          "android.hardware.bluetooth",
          "android.hardware.bluetooth_le",
          "android.hardware.camera",
          "android.hardware.camera.any",
          "android.hardware.camera.ar",
          "android.hardware.camera.autofocus",
          "android.hardware.camera.capability.manual_post_processing",
          "android.hardware.camera.capability.manual_sensor",
          "android.hardware.camera.capability.raw",
          "android.hardware.camera.external",
          "android.hardware.camera.flash",
          "android.hardware.camera.front",
          "android.hardware.camera.level.full",
          "android.hardware.consumerir",
          "android.hardware.ethernet",
          "android.hardware.faketouch",
          "android.hardware.faketouch.multitouch.distinct",
          "android.hardware.faketouch.multitouch.jazzhand",
          "android.hardware.fingerprint",
          "android.hardware.gamepad",
          "android.hardware.location",
          "android.hardware.location.gps",
          "android.hardware.location.network",
          "android.hardware.microphone",
          "android.hardware.nfc",
          "android.hardware.nfc.hce",
          "android.hardware.nfc.hcef",
          "android.hardware.opengles.aep",
          "android.hardware.ram.low",
          "android.hardware.ram.normal",
          "android.hardware.screen.landscape",
          "android.hardware.screen.portrait",
          "android.hardware.sensor.accelerometer",
          "android.hardware.sensor.ambient_temperature",
          "android.hardware.sensor.barometer",
          "android.hardware.sensor.compass",
          "android.hardware.sensor.gyroscope",
          "android.hardware.sensor.heartrate",
          "android.hardware.sensor.heartrate.ecg",
          "android.hardware.sensor.hifi_sensors",
          "android.hardware.sensor.light",
          "android.hardware.sensor.proximity",
          "android.hardware.sensor.relative_humidity",
          "android.hardware.sensor.stepcounter",
          "android.hardware.sensor.stepdetector",
          "android.hardware.strongbox_keystore",
          "android.hardware.telephony",
          "android.hardware.telephony.cdma",
          "android.hardware.telephony.euicc",
          "android.hardware.telephony.gsm",
          "android.hardware.telephony.mbms",
          "android.hardware.touchscreen",
          "android.hardware.touchscreen.multitouch",
          "android.hardware.touchscreen.multitouch.distinct",
          "android.hardware.touchscreen.multitouch.jazzhand",
          "android.hardware.type.automotive",
          "android.hardware.type.embedded",
          "android.hardware.type.pc",
          "android.hardware.type.television",
          "android.hardware.type.watch",
          "android.hardware.usb.accessory",
          "android.hardware.usb.host",
          "android.hardware.vr.headtracking",
          "android.hardware.vr.high_performance",
          "android.hardware.vulkan.compute",
          "android.hardware.vulkan.level",
          "android.hardware.vulkan.version",
          "android.hardware.wifi",
          "android.hardware.wifi.aware",
          "android.hardware.wifi.direct",
          "android.hardware.wifi.passpoint",
          "android.hardware.wifi.rtt",
          "android.software.activities_on_secondary_displays",
          "android.software.app_widgets",
          "android.software.autofill",
          "android.software.backup",
          "android.software.cant_save_state",
          "android.software.companion_device_setup",
          "android.software.connectionservice",
          "android.software.device_admin",
          "android.software.freeform_window_management",
          "android.software.home_screen",
          "android.software.input_methods",
          "android.software.leanback",
          "android.software.leanback_only",
          "android.software.live_tv",
          "android.software.live_wallpaper",
          "android.software.managed_users",
          "android.software.midi",
          "android.software.picture_in_picture",
          "android.software.print",
          "android.software.securely_removes_users",
          "android.software.sip",
          "android.software.sip.voip",
          "android.software.verified_boot",
          "android.software.vr.mode",
          "android.software.webview"
        );
      default:
        throw new IllegalArgumentException();
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myRootPanel;
  }

  public void addListener(@NotNull ModuleDownloadDeviceFeatureListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull ModuleDownloadDeviceFeatureListener listener) {
    myListeners.remove(listener);
  }

  /**
   * Keeps track of component activation so that listeners/bindings are created and released
   * when the component is activated or deactivated (respectively).
   */
  private class ActivationListener implements PropertyChangeListener {
    @NotNull
    private TextFieldProperty<String> myDeviceFeatureValueComboTextProperty;
    @NotNull
    private ObjectProperty<DeviceFeatureKind> myFeatureNameComboSelectedItem;

    private ActivationListener() {
      myDeviceFeatureValueComboTextProperty = new TextFieldProperty<>(myFeatureValueTextField);
      myFeatureNameComboSelectedItem = ObjectProperty.wrap(new SelectedItemProperty<>(myFeatureNameCombo));
    }

    @Override
    public void propertyChange(@NotNull PropertyChangeEvent evt) {
      // Setup/release bindings when ancestor changes
      if (evt.getNewValue() == null) {
        myBindings.releaseAll();
        myBindingsListeners.releaseAll();
      }
      else {
        myBindings.bindTwoWay(myFeatureNameComboSelectedItem, myModel.deviceFeatureType());
        myBindings.bindTwoWay(myDeviceFeatureValueComboTextProperty, myModel.deviceFeatureValue());

        // Ensure that each item in the "feature type" combo box has its own
        // backing (temporary) property, so that when switching item in the combo
        // box, the associated value is saved and/or restored.

        // Save UI value into temporary property for each "device feature type"
        List<StringProperty> tempValues = new ArrayList<>();
        for (DeviceFeatureKind value : DeviceFeatureKind.values()) {
          StringProperty tempProp = new StringValueProperty();
          tempValues.add(tempProp);
          myBindings.bind(tempProp, myDeviceFeatureValueComboTextProperty, myModel.deviceFeatureType().isEqualTo(value));
        }

        // Restore UI value from temporary property when a "device feature type" item is selected
        myBindingsListeners.receiveAndFire(myModel.deviceFeatureType(), value -> {
          int index = 0;
          for (DeviceFeatureKind featureType : DeviceFeatureKind.values()) {
            if (value == featureType) {
              myFeatureValueTextField.setVariants(getModelForFeatureType(featureType));
              myFeatureValueTextField.setText(tempValues.get(index).get());
            }
            index++;
          }
        });
      }
    }
  }

  private static class TextFieldProperty<T> extends StringProperty {
    @NotNull
    private TextFieldWithAutoCompletion<T> myTextField;

    private TextFieldProperty(@NotNull TextFieldWithAutoCompletion<T> textField) {
      myTextField = textField;
      myTextField.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          notifyInvalidated();
        }
      });
    }

    @Override
    protected void setDirectly(@NotNull String value) {
      myTextField.setText(value);
    }

    @NotNull
    @Override
    public String get() {
      return myTextField.getText();
    }
  }
}
