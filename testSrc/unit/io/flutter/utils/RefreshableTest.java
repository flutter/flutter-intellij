/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RefreshableTest {

  private final Refreshable<String> value = new Refreshable<>();

  @Test
  public void valueShouldBeNullAtStart() {
    assertNull(value.getNow());
  }

  @Test
  public void refreshShouldPublishNewValue() {
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
  }

  @Test
  public void refreshShouldNotifySubscriber() {
    final AtomicInteger callCount = new AtomicInteger();
    value.subscribe(() -> {
      assertEquals("subscriber should see new value", "hello", value.getNow());
      assertTrue("should be on Swing dispatch thread", SwingUtilities.isEventDispatchThread());
      callCount.incrementAndGet();
    });
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    assertEquals(1, callCount.get());
  }

  @Test
  public void shouldNotNotifySubscriberOfDuplicateValue() {
    final AtomicInteger callCount = new AtomicInteger();
    value.subscribe(callCount::incrementAndGet);
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    assertEquals(1, callCount.get());

    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    assertEquals(1, callCount.get());
  }

  @Test
  public void shouldNotPublishWhenTaskThrowsException() {
    value.refresh(() -> "first");
    assertEquals("first", value.getWhenReady());

    final AtomicInteger callCount = new AtomicInteger();
    value.subscribe(callCount::incrementAndGet);

    value.refresh(() -> {
      throw new RuntimeException("expected failure in test");
    });
    assertEquals("first", value.getWhenReady());
    assertEquals("should not have notifiied subscribers", 0, callCount.get());
  }

  @Test
  public void shouldRecoverIfSubscriberThrows() {
    value.subscribe(() -> {
      throw new RuntimeException("expected failure in test");
    });
    final AtomicInteger callCount = new AtomicInteger();
    value.subscribe(() -> {
      assertEquals("subscriber should see new value", "hello", value.getNow());
      assertTrue("should be on Swing dispatch thread", SwingUtilities.isEventDispatchThread());
      callCount.incrementAndGet();
    });
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    assertEquals(1, callCount.get());
  }

  @Test
  public void whenPublishedShouldFireOnce() {
    final AtomicInteger callCount = new AtomicInteger();
    value.whenPublished(callCount::incrementAndGet);

    value.refresh(() -> "first");
    assertEquals("first", value.getWhenReady());
    assertEquals(1, callCount.get());

    value.refresh(() -> "second");
    assertEquals("second", value.getWhenReady());
    assertEquals(1, callCount.get());
  }

  @Test
  public void whenPublishedShouldFireWhenFirstValueIsNull() {
    final AtomicInteger callCount = new AtomicInteger();
    value.whenPublished(callCount::incrementAndGet);
    value.refresh(() -> null);
    assertNull(value.getWhenReady());
    assertEquals(1, callCount.get());
  }

  @Test
  public void refreshShouldCancelRunningTaskWhenNewTaskIsSubmitted() throws Exception {
    // Create a task that will block until we say to finish.
    final FutureTask startedFirstTask = new FutureTask<>(() -> null);
    final FutureTask<String> dependency = new FutureTask<>(() -> "first task");
    final Callable<String> firstTask = () -> {
      startedFirstTask.run();
      return dependency.get();
    };

    final Callable<String> secondTask = () -> "second task";

    value.refresh(firstTask);
    startedFirstTask.get(); // wait for first task to run.

    assertNull("should have blocked on the dependency", value.getNow());

    value.refresh(secondTask);
    assertTrue("should have cancelled first task", value.isCancelled(firstTask));
    assertFalse("should not have cancelled second task", value.isCancelled(secondTask));

    dependency.run(); // Make first task exit, allowing second to run.
    assertEquals("second task", value.getWhenReady());
  }

  @Test
  public void shouldNotPublishIfTaskThrowsCancellationException() throws Exception {

  }
}
