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
package com.android.tools.idea.npwOld.assetstudio.wizard;

import java.util.function.Function;

public class PersistentState {
  public String get(String a) {
    return "";
  }
  public <T> T get(String a, T b) {
    return b;
  }
  //public int get(String a, int b) {
  //  return b;
  //}
  public <T> T getDecoded(String a, Function<String, T> b) {
    return b.apply(a);
  }
  public <T> T getChild(String a) {
    return null;
  }
  public PersistentState getOrCreateChild(String a) {
    return null;
  }
  public void set(String a, String b) {
  }
  public <T> void set(String a, T b, T c) {
  }
  //public void set(String a, int b, int c) {
  //}
  public <T> void setEncoded(String a, T b, Function<T, String> c) {
  }
  public void setChild(String a, Object b) {
  }
}
