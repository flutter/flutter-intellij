/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import junit.framework.TestCase;
import org.junit.Assert;

public class ThrottlingBucketTest extends TestCase {
  public void testRemoveDrop() throws Exception {
    final int bucketSize = 10;
    final ThrottlingBucket bucket = new ThrottlingBucket(bucketSize);
    for (int i = 0; i < bucketSize; i++) {
      Assert.assertTrue(bucket.removeDrop());
    }
    Assert.assertFalse(bucket.removeDrop());
  }
}
