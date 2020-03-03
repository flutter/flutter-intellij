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

import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Model for a single row of "device-feature"
 */
public class DeviceFeatureModel {
  @NotNull private final ObjectValueProperty<DeviceFeatureKind> myDeviceFeatureType = new ObjectValueProperty<>(DeviceFeatureKind.NAME);
  @NotNull private final StringProperty myDeviceFeatureValue = new StringValueProperty("");

  @NotNull
  public ObjectValueProperty<DeviceFeatureKind> deviceFeatureType() {
    return myDeviceFeatureType;
  }

  @NotNull
  public StringProperty deviceFeatureValue() {
    return myDeviceFeatureValue;
  }
}