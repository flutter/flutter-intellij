/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class RefreshableTest {

  private final List<String> logEntries = new ArrayList<>();
  private final Semaphore unpublished = new Semaphore(0);
  private Refreshable<String> value;

  @Before
  public void setUp() {
    value = new Refreshable<>((value) -> {
      log("unpublished: " + value);
      unpublished.release();
    });
  }

  @Test
  public void valueShouldBeNullAtStart() {
    assertNull(value.getNow());
    checkLog();
  }

  @Test
  public void refreshShouldPublishNewValue() throws Exception {
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog();
    value.close();
    waitForUnpublish();
    checkLog("unpublished: hello");
  }

  @Test
  public void refreshShouldUnpublishPreviousValue() throws Exception {
    value.refresh(() -> "one");
    assertEquals("one", value.getWhenReady());

    value.refresh(() -> "two");
    assertEquals("two", value.getWhenReady());
    waitForUnpublish();
    checkLog("unpublished: one");

    value.close();
    waitForUnpublish();
    checkLog("unpublished: two");
  }

  @Test
  public void refreshShouldNotifySubscriber() {
    value.subscribe(() -> {
      assertEquals("subscriber should see new value", "hello", value.getNow());
      assertTrue("should be on Swing dispatch thread", SwingUtilities.isEventDispatchThread());
      log("got event");
    });
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog("got event");
  }

  @Test
  public void refreshShouldNotNotifySubscriberOfDuplicateValue() throws Exception {
    value.subscribe(() -> log("got event"));
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog("got event");

    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog();

    value.close();
    waitForUnpublish();
    checkLog("unpublished: hello");
  }

  @Test
  public void refreshShouldNotPublishWhenCallbackThrowsException() {
    value.refresh(() -> "first");
    assertEquals("first", value.getWhenReady());

    value.subscribe(() -> log("got event"));

    value.refresh(() -> {
      throw new RuntimeException("expected failure in test");
    });
    assertEquals("first", value.getWhenReady());
    checkLog();
  }

  @Test
  public void refreshShouldRecoverIfSubscriberThrows() {
    value.subscribe(() -> {
      throw new RuntimeException("expected failure in test");
    });
    value.subscribe(() -> {
      assertEquals("subscriber should see new value", "hello", value.getNow());
      assertTrue("should be on Swing dispatch thread", SwingUtilities.isEventDispatchThread());
      log("got event");
    });
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog("got event");
  }

  @Test
  public void whenPublishedShouldFireOnce() throws Exception {
    value.whenPublished(() -> log("got event"));

    value.refresh(() -> "first");
    assertEquals("first", value.getWhenReady());
    checkLog("got event");

    value.refresh(() -> "second");
    assertEquals("second", value.getWhenReady());
    waitForUnpublish();
    checkLog("unpublished: first");
  }

  @Test
  public void whenPublishedShouldFireWhenFirstValueIsNull() {
    value.whenPublished(() -> log("got event"));
    value.refresh(() -> null);
    assertNull(value.getWhenReady());
    checkLog("got event");
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
    startedFirstTask.get(); // wait for first task to start running.

    assertNull("should have blocked on the dependency", value.getNow());
    checkLog();

    value.refresh(secondTask);
    assertTrue("should have cancelled first task", value.isCancelled(firstTask));
    assertFalse("should not have cancelled second task", value.isCancelled(secondTask));

    dependency.run(); // Make first task exit, allowing second to run.
    assertEquals("second task", value.getWhenReady());
    waitForUnpublish();
    checkLog("unpublished: first task");
  }

  @Test
  public void refreshShouldYieldToQueuedEvents() throws Exception {
    // Queue up some events.
    SwingUtilities.invokeAndWait(() -> {
      value.refresh(() -> {
        log("created first");
        return "first";
      });
      value.refresh(() -> {
        log("created second");
        return "second";
      });
      SwingUtilities.invokeLater(() -> value.refresh(() -> {
        log("created third");
        return "third";
      }));
    });

    assertEquals("third", value.getWhenReady());
    checkLog("created third");
  }

  @Test
  public void publishShouldYieldToQueuedEvents() throws Exception {
    value.subscribe(() -> log("got event"));
    final Semaphore finish = startRefresh("first");

    SwingUtilities.invokeAndWait(() -> {
      finish.release();

      // Make sure it's blocked until we exit.
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException e) {
        log("unexpected exception: " + e);
      }

      log("event handler done");
    });

    assertEquals("first", value.getWhenReady());
    checkLog("entered refresh callback: first",
             "exited refresh callback: first",
             "event handler done",
             "got event");
  }

  @Test
  public void shouldNotPublishWhenClosedDuringRefreshCallback() throws Exception {
    value.subscribe(() -> log("got event"));
    final Semaphore finish = startRefresh("first");
    value.close();
    finish.release();
    assertNull(value.getWhenReady());
    waitForUnpublish();
    checkLog("entered refresh callback: first",
             "exited refresh callback: first",
             "unpublished: first");
  }

  @Test
  public void shouldNotPublishWhenClosedBeforePublish() throws Exception {
    value.subscribe(() -> log("got event"));
    final Semaphore finish = startRefresh("first");

    SwingUtilities.invokeAndWait(() -> {
      SwingUtilities.invokeLater(() -> {
        value.close();
        log("closed");
      });
      finish.release();
    });
    assertNull(value.getWhenReady());
    waitForUnpublish();
    checkLog("entered refresh callback: first",
             "exited refresh callback: first",
             "closed",
             "unpublished: first");
  }

  private void waitForUnpublish() throws Exception {
    assertTrue("timed out waiting for value to be unpublished",
               unpublished.tryAcquire(1, TimeUnit.SECONDS));
  }

  /**
   * Starts running a refresh, but block until the test tells it to finish.
   *
   * <p>The caller should invoke release on the returned semaphore to unblock
   * the refresh callback.
   */
  private @NotNull Semaphore startRefresh(String newValue) throws Exception {
    final Semaphore start = new Semaphore(0);
    final Semaphore finish = new Semaphore(0);
    value.refresh(() -> {
      log("entered refresh callback: " + newValue);
      start.release();
      finish.acquire();
      log("exited refresh callback: " + newValue);
      return newValue;
    });
    start.acquire();
    return finish;
  }

  private synchronized boolean log(String message) {
    return logEntries.add(message);
  }

  private synchronized List<String> getLogEntries() {
    return ImmutableList.copyOf(logEntries);
  }

  private void checkLog(String... expectedEntries) {
    assertThat("logEntries entries are different", getLogEntries(), is(ImmutableList.copyOf(expectedEntries)));
    logEntries.clear();
  }
}
