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
package com.android.tools.idea.npwOld.template;

import com.android.annotations.NonNull;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point for transforming java files into kotlin as well as providing the necessary configuration for kotlin
 */
public interface ConvertJavaToKotlinProvider {
  ExtensionPointName<ConvertJavaToKotlinProvider> EP_NAME =
    new ExtensionPointName<>("com.android.tools.idea.npwOld.template.convertJavaToKotlinProvider");

  @NonNull String getKotlinVersion();

  List<PsiFile> convertToKotlin(@NotNull Project project, @NotNull List<PsiJavaFile> files);

  void configureKotlin(@NotNull Project project);
}
