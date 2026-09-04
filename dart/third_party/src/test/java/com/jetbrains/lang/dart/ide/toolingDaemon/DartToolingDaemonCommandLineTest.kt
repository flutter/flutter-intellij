/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.ide.toolingDaemon

import junit.framework.TestCase

class DartToolingDaemonCommandLineTest : TestCase() {

    fun testIdentifiesDtdCommandLine() {
        assertTrue(isDtdCommandLine("/path/to/dart tooling-daemon --machine --ping-interval=15"))
        assertTrue(isDtdCommandLine("/path/to/dart tooling-daemon --machine --ping-interval=30"))
        assertTrue(isDtdCommandLine("/path/to/dart tooling-daemon --machine"))
        assertFalse(isDtdCommandLine("/path/to/dart tooling-daemon --machine --ping-interval=invalid"))
    }

    fun testSupportsDtdPingInterval() {
        assertFalse(supportsDtdPingInterval("3.10.9"))
        assertFalse(supportsDtdPingInterval("3.11.0-dev.100.0"))
        assertTrue(supportsDtdPingInterval("3.11.0"))
        assertTrue(supportsDtdPingInterval("3.12.0-dev.1.0"))
        assertTrue(supportsDtdPingInterval("3.12.0"))
        assertFalse(supportsDtdPingInterval("unknown"))
    }
}
