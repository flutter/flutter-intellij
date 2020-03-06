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
package com.android.tools.idea.npwOld.validator;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.validation.Validator;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Validates a Java class name
 */
public final class ClassNameValidator implements Validator<String> {

  @NotNull
  @Override
  public Result validate(@NotNull String name) {
    if (StringUtil.isEmpty(name) || name.indexOf('.') >= 0 || !AndroidUtils.isIdentifier(name)) {
      return new Result(Severity.ERROR, message("android.wizard.validate.invalid.class.name"));
    }
    return Result.OK;
  }
}
