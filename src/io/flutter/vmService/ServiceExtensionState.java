/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import org.jetbrains.annotations.Nullable;

public record ServiceExtensionState(boolean enabled, @Nullable Object value) {
}
