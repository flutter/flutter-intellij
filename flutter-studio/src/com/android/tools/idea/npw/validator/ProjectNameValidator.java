/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.validation.Validator;
import com.google.common.base.CharMatcher;
import org.jetbrains.annotations.NotNull;


/**
 * Validates a project name
 */
public final class ProjectNameValidator implements Validator<String> {
  private static final CharMatcher DISALLOWED_IN_PROJECT_NAME = CharMatcher.anyOf("/\\:<>\"?*|");

  @NotNull
  @Override
  public Result validate(@NotNull String name) {
    if (name.isEmpty()) {
      return new Result(Severity.ERROR, message("android.wizard.validate.empty.application.name"));
    }

    int illegalCharIdx = DISALLOWED_IN_PROJECT_NAME.indexIn(name);
    if (illegalCharIdx >= 0) {
      return new Result(Severity.ERROR, message("android.wizard.validate.project.illegal.character", name.charAt(illegalCharIdx), name));
    }

    if (!Character.isUpperCase(name.charAt(0))) {
      return new Result(Severity.INFO, message("android.wizard.validate.lowercase.application.name"));
    }

    return Result.OK;
  }
}
