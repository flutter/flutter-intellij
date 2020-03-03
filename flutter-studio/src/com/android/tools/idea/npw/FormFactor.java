/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import icons.StudioIllustrations.FormFactors;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representations of all Android hardware devices we can target when building an app.
 */
public enum FormFactor {
  MOBILE("Mobile", "Phone and Tablet", 15, SdkVersionInfo.LOWEST_ACTIVE_API, SdkVersionInfo.HIGHEST_KNOWN_API, Lists.newArrayList(20),
         Lists.newArrayList(SystemImage.DEFAULT_TAG, SystemImage.GOOGLE_APIS_TAG, SystemImage.GOOGLE_APIS_X86_TAG), null,
         FormFactors.MOBILE, FormFactors.MOBILE_LARGE),
  WEAR("Wear", "Wear OS", 21, SdkVersionInfo.LOWEST_ACTIVE_API_WEAR, SdkVersionInfo.HIGHEST_KNOWN_API_WEAR,
       null, Lists.newArrayList(SystemImage.WEAR_TAG), null, FormFactors.WEAR, FormFactors.WEAR_LARGE),
  TV("TV", "TV", 21, SdkVersionInfo.LOWEST_ACTIVE_API_TV, SdkVersionInfo.HIGHEST_KNOWN_API_TV,
     null, Lists.newArrayList(SystemImage.TV_TAG), null, FormFactors.TV, FormFactors.TV_LARGE),
  CAR("Car", "Android Auto", 21, 21, 21, null, null, MOBILE, FormFactors.CAR, FormFactors.CAR_LARGE),
  THINGS("Things", "Android Things", 24, 24, SdkVersionInfo.HIGHEST_KNOWN_API, null, null, null, FormFactors.THINGS, FormFactors.THINGS_LARGE),
  GLASS("Glass", "Glass", 19, -1, -1, null, Lists.newArrayList(SystemImage.GLASS_TAG), null, FormFactors.GLASS, FormFactors.GLASS_LARGE);

  private static final Map<String, FormFactor> myFormFactors = new ImmutableMap.Builder<String, FormFactor>()
    .put(MOBILE.id, MOBILE)
    .put(WEAR.id, WEAR)
    .put(TV.id, TV)
    .put(CAR.id, CAR)
    .put(THINGS.id, THINGS)
    .put(GLASS.id, GLASS).build();

  public final String id;
  @Nullable private String myDisplayName;
  public final int defaultApi;
  @NotNull private final List<Integer> myApiBlacklist;
  @NotNull private final List<IdDisplay> myTags;
  private final int myMinOfflineApiLevel;
  private final int myMaxOfflineApiLevel;
  @Nullable public final FormFactor baseFormFactor;
  @NotNull private final Icon myIcon64;
  @NotNull private final Icon myIcon128;

  FormFactor(@NotNull String id, @Nullable String displayName,
             int defaultApi, int minOfflineApiLevel, int maxOfflineApiLevel, @Nullable List<Integer> apiBlacklist,
             @Nullable List<IdDisplay> apiTags, @Nullable FormFactor baseFormFactor, @NotNull Icon icon64, @NotNull Icon icon128) {
    this.id = id;
    myDisplayName = displayName;
    this.defaultApi = defaultApi;
    myMinOfflineApiLevel = minOfflineApiLevel;
    myIcon64 = icon64;
    myIcon128 = icon128;
    myMaxOfflineApiLevel = Math.min(maxOfflineApiLevel, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API);
    myApiBlacklist = apiBlacklist != null ? apiBlacklist : Collections.emptyList();
    myTags = apiTags != null ? apiTags : Collections.emptyList();
    this.baseFormFactor = baseFormFactor;
  }

  @NotNull
  public static FormFactor get(@NotNull String id) {
    final FormFactor result = myFormFactors.get(id);
    return result == null ? MOBILE : result;
  }

  @NotNull
  public static FormFactor getFormFactor(@NotNull Device device) {
    if (HardwareConfigHelper.isWear(device)) {
      return WEAR;
    }
    else if (HardwareConfigHelper.isTv(device)) {
      return TV;
    }
    else if (HardwareConfigHelper.isThings(device)) {
      return THINGS;
    }    // Glass, Car not yet in the device list

    return MOBILE;
  }

  @Override
  public String toString() {
    return myDisplayName == null ? id : myDisplayName;
  }

  public int getMinOfflineApiLevel() {
    return myMinOfflineApiLevel;
  }

  public int getMaxOfflineApiLevel() {
    return myMaxOfflineApiLevel;
  }

  @NotNull
  public Icon getLargeIcon() {
    return myIcon128;
  }

  public boolean hasEmulator() {
    return this != GLASS;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon64;
  }

  public boolean isSupported(@Nullable IdDisplay tag, int targetSdkLevel) {
    // If a white-list is present, only allow things on the white-list
    if (!myTags.isEmpty() && !myTags.contains(tag)) {
      return false;
    }

    return !myApiBlacklist.contains(targetSdkLevel);
  }
}
