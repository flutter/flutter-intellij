/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npwOld.assetstudio;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.npwOld.assetstudio.BuiltInImages.getResourcesNames;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Methods for accessing library of material design icons.
 */
public final class MaterialDesignIcons {
  private static final String DEFAULT_ICON_NAME = "action/ic_android_black_24dp.xml";
  private static final String PATH = "images/material_design_icons/";
  private static final Pattern CATEGORY_PATTERN = Pattern.compile(PATH + "(\\w+)/");

  /** Do not instantiate - all methods are static. */
  private MaterialDesignIcons() {
  }

  @Nullable
  public static String getPathForBasename(@NotNull String basename) {
    return getBasenameToPathMap(path -> getResourcesNames(path, DOT_XML)).get(basename);
  }

  @NotNull
  @VisibleForTesting
  static Map<String, String> getBasenameToPathMap(@NotNull Function<String, List<String>> generator) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    int dotXmlLength = DOT_XML.length();

    for (String category : getCategories()) {
      String path = PATH + category + '/';

      for (String name : generator.apply(path)) {
        builder.put(name.substring(0, name.length() - dotXmlLength), path + name);
      }
    }

    return builder.build();
  }

  @NotNull
  public static Collection<String> getCategories() {
    return getCategories(getResourceUrl(PATH));
  }

  @NotNull
  public static List<String> getIconNames(@NotNull String categoryName) {
    return getResourcesNames(getIconDirectoryPath(categoryName), SdkConstants.DOT_XML);
  }

  @NotNull
  public static URL getIcon(@NotNull String iconName, @NotNull String categoryName) {
    return getResourceUrl(getIconDirectoryPath(categoryName) + iconName);
  }

  @NotNull
  public static URL getDefaultIcon() {
    URL url = getResourceUrl(PATH + DEFAULT_ICON_NAME);
    assert url != null;
    return url;
  }

  @VisibleForTesting
  static Collection<String> getCategories(@Nullable URL url) {
    if (url == null) {
      return Collections.emptyList();
    }

    switch (url.getProtocol()) {
      case "file":
        return getCategoriesFromFile(new File(url.getPath()));
      case "jar":
        try {
          JarURLConnection connection = (JarURLConnection)url.openConnection();
          return getCategoriesFromJar(connection.getJarFile());
        } catch (IOException e) {
          return Collections.emptyList();
        }
      default:
        return Collections.emptyList();
    }
  }

  @NotNull
  @VisibleForTesting
  static Collection<String> getCategoriesFromFile(@NotNull File file) {
    String[] array = file.list();

    if (array == null) {
      return Collections.emptyList();
    }

    List<String> list = Arrays.asList(array);
    list.sort(String::compareTo);

    return list;
  }

  @NotNull
  @VisibleForTesting
  static Collection<String> getCategoriesFromJar(@NotNull ZipFile jar) {
    return jar.stream()
        .map(MaterialDesignIcons::getCategory)
        .filter(Objects::nonNull)
        .sorted()
        .collect(Collectors.toList());
  }

  @Nullable
  private static String getCategory(@NotNull ZipEntry entry) {
    Matcher matcher = CATEGORY_PATTERN.matcher(entry.getName());
    return matcher.matches() ? matcher.group(1) : null;
  }

  @NotNull
  private static String getIconDirectoryPath(String categoryName) {
    return PATH + categoryName.toLowerCase(Locale.ENGLISH) + '/';
  }

  private static URL getResourceUrl(String iconPath) {
    return MaterialDesignIcons.class.getClassLoader().getResource(iconPath);
  }
}
