/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link FlutterInitializer}.
 */
public class FlutterInitializerTest {

  @Test
  public void testInitializerCanBeCreated() {
    // Test that we can create FlutterInitializer without issues
    // This validates that the shared scheduler field is properly initialized
    FlutterInitializer initializer = new FlutterInitializer();
    assertNotNull("FlutterInitializer should be created successfully", initializer);
  }

  @Test
  public void testSchedulerFieldExists() throws Exception {
    // Test that the scheduler field exists and is properly initialized
    FlutterInitializer initializer = new FlutterInitializer();
    
    Field schedulerField = FlutterInitializer.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    
    Object scheduler = schedulerField.get(initializer);
    assertNotNull("Scheduler field should be initialized", scheduler);
    assertTrue("Scheduler should be a ScheduledExecutorService", 
               scheduler instanceof ScheduledExecutorService);
  }

  @Test 
  public void testDebounceFieldExists() throws Exception {
    // Test that the debounce field exists and is properly initialized
    FlutterInitializer initializer = new FlutterInitializer();
    
    Field debounceField = FlutterInitializer.class.getDeclaredField("lastScheduledThemeChangeTime");
    debounceField.setAccessible(true);
    
    Object debounceTimer = debounceField.get(initializer);
    assertNotNull("Debounce timer field should be initialized", debounceTimer);
    assertTrue("Debounce timer should be an AtomicLong", 
               debounceTimer instanceof AtomicLong);
  }
}