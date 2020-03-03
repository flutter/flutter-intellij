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
package com.android.tools.idea.npw.cpp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supported C++ standard versions that NDK code can use
 *
 * @see ConfigureCppSupportStep
 */
public enum CppStandardType {
  DEFAULT("Toolchain Default", null),
  CXX11("C++11", "-std=c++11"),
  CXX14("C++14", "-std=c++14");

  CppStandardType(@NotNull String dialogName, @Nullable String compilerFlag) {
    myDialogName = dialogName;
    myCompilerFlag = compilerFlag;
  }

  @NotNull
  private final String myDialogName;

  @Nullable/*if no additional flag required*/
  private final String myCompilerFlag;

  @Nullable
  public String getCompilerFlag() {
    return myCompilerFlag;
  }

  @Override
  public String toString() {
    return myDialogName;
  }
}
