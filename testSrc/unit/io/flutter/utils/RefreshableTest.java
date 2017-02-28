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
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class RefreshableTest {

  private Refreshable<String> value;

  private final List<String> logEntries = new ArrayList<>();

  private final Semaphore canUnpublish = new Semaphore(0);
  private final Semaphore unpublished = new Semaphore(0);

  private final Semaphore canFireCloseEvent = new Semaphore(0);
  private final Semaphore closeEventReceived = new Semaphore(0);

  @Before
  public void setUp() {
    value = new Refreshable<>((value) -> {
      acquireOrLog(canUnpublish, "unpublish not expected here");
      log("unpublished: " + value);
      unpublished.release();
    });

    value.subscribe(() -> {
      if (!SwingUtilities.isEventDispatchThread()) {
        log("subscriber should be called on Swing thread");
        return;
      }

      if (value.getState() == Refreshable.State.CLOSED) {
        acquireOrLog(canFireCloseEvent, "close event not expected here");
      }

      log(value.getState() + ": " + value.getNow());

      if (value.getState() == Refreshable.State.CLOSED) {
        closeEventReceived.release();
      }
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
    checkLog("BUSY: null",
             "BUSY: hello",
             "IDLE: hello");

    value.close();
    expectUnpublish();
    expectCloseEvent();
    checkLog(
      "unpublished: hello",
      "CLOSED: null");
  }

  @Test
  public void refreshShouldProvideAndUnpublishPreviousValue() throws Exception {
    value.refresh((req) -> {
      log("previous: " + req.getPrevious());
      return "one";
    });
    assertEquals("one", value.getWhenReady());
    checkLog("BUSY: null",
             "previous: null",
             "BUSY: one",
             "IDLE: one");

    value.refresh((req) -> {
      log("previous: " + req.getPrevious());
      return "two";
    });
    expectUnpublish();
    assertEquals("two", value.getWhenReady());
    checkLog(
      "BUSY: one",
      "previous: one",
      "unpublished: one",
      "BUSY: two",
      "IDLE: two");

    value.close();
    expectUnpublish();
    expectCloseEvent();
    checkLog("unpublished: two",
             "CLOSED: null");
  }

  @Test
  public void refreshShouldNotPublishOrUnpublishDuplicateValue() throws Exception {
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog("BUSY: null",
             "BUSY: hello",
             "IDLE: hello");

    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog("BUSY: hello",
             "IDLE: hello");

    value.close();
    expectUnpublish();
    expectCloseEvent();
    checkLog("unpublished: hello",
             "CLOSED: null");
  }

  @Test
  public void refreshShouldNotPublishWhenCallbackThrowsException() throws Exception {
    value.refresh(() -> "first");
    assertEquals("first", value.getWhenReady());
    checkLog("BUSY: null",
             "BUSY: first",
             "IDLE: first");

    value.refresh(() -> {
      throw new RuntimeException("expected failure in test");
    });
    assertEquals("first", value.getWhenReady());
    checkLog("BUSY: first",
             "IDLE: first");

    value.refresh((req) -> {
      log("previous: " + req.getPrevious());
      return "second";
    });
    expectUnpublish();
    assertEquals("second", value.getWhenReady());
    checkLog("BUSY: first",
             "previous: first",
             "unpublished: first",
             "BUSY: second",
             "IDLE: second");
  }

  @Test
  public void refreshShouldRecoverIfSubscriberThrows() {
    value.subscribe(() -> {
      throw new RuntimeException("expected failure in test");
    });
    value.subscribe(() -> log(value.getState() + ": " + value.getNow() + " (last subscriber)"));
    value.refresh(() -> "hello");
    assertEquals("hello", value.getWhenReady());
    checkLog("BUSY: null",
             "BUSY: null (last subscriber)",
             "BUSY: hello",
             "BUSY: hello (last subscriber)",
             "IDLE: hello",
             "IDLE: hello (last subscriber)");
  }

  @Test
  public void refreshShouldCancelRunningTaskWhenNewTaskIsSubmitted() throws Exception {
    // Create a task that will block until we say to finish.
    final FutureTask startedFirstTask = new FutureTask<>(() -> null);
    final FutureTask<String> dependency = new FutureTask<>(() -> "first task");

    final AtomicReference<Refreshable.Request> firstRequest = new AtomicReference<>();
    final Refreshable.Callback<String> firstTask = (request) -> {
      firstRequest.set(request);
      startedFirstTask.run();
      return dependency.get();
    };

    final Callable<String> secondTask = () -> "second task";

    value.refresh(firstTask);
    startedFirstTask.get(); // wait for first task to start running.

    assertNull("should have blocked on the dependency", value.getNow());
    checkLog("BUSY: null");

    value.refresh(secondTask);
    assertTrue("should have cancelled first task", firstRequest.get().isCancelled());
    checkLog();

    dependency.run(); // Make first task exit, allowing second to run.
    expectUnpublish();
    assertEquals("second task", value.getWhenReady());
    checkLog("unpublished: first task",
             "BUSY: second task",
             "IDLE: second task");
  }

  @Test
  public void refreshShouldYieldToQueuedEvents() throws Exception {
    // Queue up some events.
    SwingUtilities.invokeAndWait(() -> {
      value.refresh(() -> {
        log("shouldn't create first");
        return "first";
      });
      value.refresh((req) -> {
        log("shouldn't create second");
        return "second";
      });
      SwingUtilities.invokeLater(() -> value.refresh((req) -> {
        log("created third; previous: " + req.getPrevious());
        return "third";
      }));
    });

    assertEquals("third", value.getWhenReady());
    checkLog("BUSY: null",
             "created third; previous: null",
             "BUSY: third",
             "IDLE: third");
  }

  @Test
  public void publishShouldYieldToQueuedEvents() throws Exception {
    final Semaphore finish = startRefresh("first");

    SwingUtilities.invokeAndWait(() -> {
      finish.release();

      // Make sure publishing values is blocked until we exit.
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException e) {
        log("unexpected exception: " + e);
      }

      log("event handler done");
    });

    assertEquals("first", value.getWhenReady());
    checkLog("BUSY: null",
             "entered refresh",
             "exited refresh: first",
             "event handler done",
             "BUSY: first",
             "IDLE: first");
  }

  @Test
  public void shouldNotPublishWhenClosedDuringRefreshCallback() throws Exception {
    final Semaphore finish = startRefresh("first");
    value.close();
    expectCloseEvent();
    finish.release();
    expectUnpublish();
    assertNull(value.getWhenReady());
    checkLog(
      "BUSY: null",
      "entered refresh",
      "CLOSED: null",
      "exited refresh: first",
      "unpublished: first");
  }

  @Test
  public void shouldNotPublishWhenClosedBeforePublish() throws Exception {
    final Semaphore finish = startRefresh("first");

    SwingUtilities.invokeAndWait(() -> {
      SwingUtilities.invokeLater(() -> {
        // Need to lose race with background tasks's call to reschedule(),
        // But win the race to publish().
        // So, schedule Swing event now, but make it slower.
        // (No suitable semaphore for this one.)
        try {
          Thread.sleep(50);
        }
        catch (InterruptedException e) {
          log("interrupted");
        }
        value.close();
        log("called close()");
      });
      finish.release();
    });
    expectUnpublish();
    expectCloseEvent();
    assertNull(value.getWhenReady());

    checkLog("BUSY: null",
             "entered refresh",
             "exited refresh: first",
             "called close()",
             "unpublished: first",
             "CLOSED: null");
  }

  private void expectUnpublish() throws Exception {
    canUnpublish.release();
    acquireOrLog(unpublished, "should have unpublished");
  }

  private void expectCloseEvent() throws Exception {
    canFireCloseEvent.release();
    acquireOrLog(closeEventReceived, "should have gotten close event");
  }

  private void acquireOrLog(Semaphore s, String error) {
    try {
      if (!s.tryAcquire(1, TimeUnit.SECONDS)) {
        log(error);
      }
    } catch (InterruptedException e) {
      log(error + " (interrupted)");
    }
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
      log("entered refresh");
      start.release();
      finish.acquire();
      log("exited refresh: " + newValue);
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
