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
package com.android.tools.idea.npw.validator;

import static com.google.common.base.CharMatcher.anyOf;
import static com.google.common.base.CharMatcher.inRange;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.google.common.base.CharMatcher;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Validates the module name and its location
 */
public final class ModuleValidator implements Validator<String> {
  private @Nullable Project myProject; // May be null for new projects
  private @NotNull PathValidator myPathValidator;
  private @NotNull StringProperty myProjectPath;
  private final CharMatcher ILLEGAL_CHAR_MATCHER = inRange('a', 'z').or(inRange('A', 'Z')).or(inRange('0', '9')).or(anyOf("_-:")).negate();

  public ModuleValidator(@NotNull Project project) {
    this(new StringValueProperty(project.getBasePath()));
    myProject = project;
  }

  public ModuleValidator(@NotNull StringProperty projectPath) {
    myProjectPath = projectPath;
    myPathValidator = PathValidator.createDefault("module location");
  }

  @NotNull
  @Override
  public Result validate(@NotNull String name) {
    if (name.isEmpty()) {
      return new Result(Severity.ERROR, message("android.wizard.validate.empty.module.name"));
    }

    if (myProject != null && ModuleManager.getInstance(myProject).findModuleByName(name) != null) {
      return new Result(Severity.ERROR, message("android.wizard.validate.module.already.exists", name));
    }

    int illegalCharIdx = ILLEGAL_CHAR_MATCHER.indexIn(name);
    if (illegalCharIdx >= 0) {
      return new Result(Severity.ERROR, message("android.wizard.validate.module.illegal.character", name.charAt(illegalCharIdx), name));
    }

    // Note: Module name may have ":", needs to be converted to a path
    File moduleRoot = NewModuleModel.getModuleRoot(myProjectPath.get(), name);
    return myPathValidator.validate(moduleRoot);
  }

  @NotNull
  @TestOnly
  PathValidator getPathValidator() {
    return myPathValidator;
  }
}
