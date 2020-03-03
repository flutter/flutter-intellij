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
package com.android.tools.idea.npw.platform;

import static org.jetbrains.android.util.AndroidBundle.message;

import icons.AndroidIcons;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representations of navigation types.
 */
public enum NavigationType {
  NONE("None", null, ""),
  NAVIGATION_DRAWER("Navigation Drawer", AndroidIcons.Wizards.NavigationDrawer,
                    message("android.wizard.activity.navigation.navigation_drawer.details")),
  BOTTOM_NAVIGATION("Bottom Navigation", AndroidIcons.Wizards.BottomNavigation,
                    message("android.wizard.activity.navigation.bottom_navigation.details")),
  TABS("Tabs", AndroidIcons.Wizards.NavigationTabs, message("android.wizard.activity.navigation.tabs.details"));

  @NotNull private final String name;
  @Nullable private final Icon icon;
  @NotNull private final String details;

  NavigationType(@NotNull String name, @Nullable Icon icon, @NotNull String details) {
    this.name = name;
    this.icon = icon;
    this.details = details;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @Nullable
  public Icon getIcon() {
    return icon;
  }

  @NotNull
  public String getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return getName();
  }

  public static NavigationType[] valuesExceptNone() {
    return Stream.of(values()).filter(type -> type != NONE).toArray(NavigationType[]::new);
  }
}
