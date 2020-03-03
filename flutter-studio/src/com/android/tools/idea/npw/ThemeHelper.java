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
package com.android.tools.idea.npw;

import static com.intellij.openapi.util.text.StringUtil.trimStart;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.module.Module;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Theme utility class for use with templates.
 */
public class ThemeHelper {
  private static final String DEFAULT_THEME_NAME = "AppTheme";    //$NON-NLS-1$
  private static final String ALTERNATE_THEME_NAME = "Theme.App"; //$NON-NLS-1$
  private static final String APP_COMPAT = "Theme.AppCompat.";    //$NON-NLS-1$

  private final Module myModule;
  private final LocalResourceRepository myProjectRepository;

  public ThemeHelper(@NotNull Module module) {
    myModule = module;
    myProjectRepository = ResourceRepositoryManager.getProjectResources(module);
  }

  @Nullable
  public String getAppThemeName() {
    String manifestTheme = MergedManifestManager.getSnapshot(myModule).getManifestTheme();
    if (manifestTheme != null) {
      manifestTheme = trimStart(manifestTheme, SdkConstants.STYLE_RESOURCE_PREFIX);
      return manifestTheme;
    }
    if (getProjectStyleResource(DEFAULT_THEME_NAME) != null) {
      return DEFAULT_THEME_NAME;
    }
    if (getProjectStyleResource(ALTERNATE_THEME_NAME) != null) {
      return ALTERNATE_THEME_NAME;
    }
    return null;
  }

  public boolean isAppCompatTheme(@NotNull String themeName) {
    StyleResourceValue theme = getProjectStyleResource(themeName);
    return isAppCompatTheme(themeName, theme);
  }

  public static boolean themeExists(@NotNull Configuration configuration, @NotNull String themeName) {
    return getStyleResource(configuration, themeName) != null;
  }

  public boolean isLocalTheme(@NotNull String themeName) {
    return getProjectStyleResource(themeName) != null;
  }

  public static Boolean hasActionBar(@NotNull Configuration configuration, @NotNull String themeName) {
    StyleResourceValue theme = getStyleResource(configuration, themeName);
    if (theme == null) {
      return null;
    }
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;

    // TODO(namespaces): resolve themeName in the context of the right manifest file.
    ResourceValue value =
      resolver.resolveResValue(resolver.findItemInStyle(theme, ResourceReference.attr(ResourceNamespace.TODO(), "windowActionBar")));
    if (value == null || value.getValue() == null) {
      return true;
    }
    return SdkConstants.VALUE_TRUE.equals(value.getValue());
  }

  @Nullable
  private static StyleResourceValue getStyleResource(@NotNull Configuration configuration, @NotNull String themeName) {
    configuration.setTheme(themeName);
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;

    ResourceUrl url = ResourceUrl.parse(themeName);
    if (url == null) {
      return null;
    }
    // TODO(namespaces): resolve themeName in the context of the right manifest file.
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    if (reference == null) {
      return null;
    }
    return resolver.getStyle(reference);
  }

  @Nullable
  private StyleResourceValue getProjectStyleResource(@Nullable String theme) {
    if (theme == null) {
      return null;
    }
    List<ResourceItem> items = myProjectRepository.getResources(ResourceNamespace.TODO(), ResourceType.STYLE, theme);
    if (items.isEmpty()) {
      return null;
    }
    return (StyleResourceValue)items.get(0).getResourceValue();
  }

  private boolean isAppCompatTheme(@NotNull String themeName, @Nullable StyleResourceValue localTheme) {
    while (localTheme != null) {
      // TODO: namespaces
      String parentThemeName = localTheme.getParentStyleName();
      if (parentThemeName == null) {
        if (themeName.lastIndexOf('.') > 0) {
          parentThemeName = themeName.substring(0, themeName.lastIndexOf('.'));
        }
        else {
          return false;
        }
      }
      themeName = parentThemeName;
      localTheme = getProjectStyleResource(themeName);
    }
    return themeName.startsWith(APP_COMPAT);
  }
}
