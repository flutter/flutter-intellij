/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;

public class IdeaProjectFixture extends ProjectFixture<IdeaProjectTestFixture> {
  IdeaProjectFixture(Factory<IdeaProjectTestFixture> factory, boolean setupOnDispatchThread) {
    super(factory, setupOnDispatchThread);
  }
}
