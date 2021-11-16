/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.fileEditor.TextEditor;

public interface FilePerfModelFactory {
  EditorPerfModel create(TextEditor textEditor);
}
