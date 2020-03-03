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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "ConditionalDeliverySettings",
  storages = @Storage("conditionalDelivery.experimental.xml")
)
public class ConditionalDeliverySettings implements PersistentStateComponent<ConditionalDeliverySettings> {

  public boolean USE_CONDITIONAL_DELIVERY_SYNC;

  @NotNull
  public static ConditionalDeliverySettings getInstance() {
    return ServiceManager.getService(ConditionalDeliverySettings.class);
  }

  @Override
  @NotNull
  public ConditionalDeliverySettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ConditionalDeliverySettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
