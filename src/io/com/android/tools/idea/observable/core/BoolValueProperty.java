/*
 * Copyright (C) 2020 The Android Open Source Project
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
package io.com.android.tools.idea.observable.core;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A boolean-backed AbstractProperty.
 */
public final class BoolValueProperty {

  private Boolean myValue;

  public BoolValueProperty(@NotNull Boolean value) {
    myValue = value;
  }

  public BoolValueProperty() {
    this(false);
  }

  @NotNull
  public Boolean get() {
    return myValue;
  }

  protected void setDirectly(@NotNull Boolean value) {
    myValue = value;
  }

  public final void set(@NotNull Boolean value) {
    if (!isValueEqual(value)) {
      //setNotificationsEnabled(false);
      setDirectly(value);
      //setNotificationsEnabled(true);
      //notifyInvalidated();
    }
  }

  protected boolean isValueEqual(@Nullable Boolean value) {
    return Objects.equal(get(), value);
  }

  @Override
  public String toString() {
    return get().toString();
  }

  public final void addConstraint(Object constraint) {
    // This is only a partial implementation of the Android Studio class. If someone tries to use constraints more implementation is needed.
    throw new Error("constraints not supported");
  }

  public final void addListener(Object listener) {
    // This is only a partial implementation of the Android Studio class. If someone tries to use listeners more implementation is needed.
    throw new Error("listeners not supported");
  }
}
