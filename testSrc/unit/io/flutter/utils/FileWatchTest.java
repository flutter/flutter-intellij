/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.TestDir;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Verifies that we can watch files and directories.
 */
public class FileWatchTest {
  @Rule
  public final TestDir tmp = new TestDir();

  @Test
  public void shouldFireEvents() throws Exception {
    final VirtualFile dir = tmp.ensureDir("abc");

    final AtomicInteger eventCount = new AtomicInteger();
    final FileWatch fileWatch = FileWatch.subscribe(dir, ImmutableSet.of("child"), eventCount::incrementAndGet);
    assertEquals(0, eventCount.get());

    // create
    tmp.writeFile("abc/child", "");
    final int count;
    // The number of events fired is an implementation detail of the VFS. We just need at least one.
    assertNotEquals(0, count = eventCount.get());

    // modify
    tmp.writeFile("abc/child", "hello");
    assertEquals(count + 1, eventCount.get());

    // delete
    tmp.deleteFile("abc/child");
    assertEquals(count + 2, eventCount.get());
  }
}
