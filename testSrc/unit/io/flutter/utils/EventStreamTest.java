/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class EventStreamTest {

  private final List<String> logEntries = new ArrayList<>();
  private final Object logValueListenerLock = new Object();
  private EventStream<Integer> eventStream;
  private CompletableFuture<Object> callbacksDone;
  private volatile int expectedEvents;
  private volatile int numEvents;
  private boolean onUiThread;

  @Before
  public void setUp() {
    numEvents = 0;

    callbacksDone = new CompletableFuture<>();
    eventStream = new EventStream<>(42);
  }

  void logValueListener(Integer value) {
    synchronized (logValueListenerLock) {
      if (onUiThread && !SwingUtilities.isEventDispatchThread()) {
        log("subscriber should be called on Swing thread");
        return;
      }

      log("" + value);

      numEvents++;
      if (numEvents == expectedEvents) {
        callbacksDone.complete(null);
      }
      else if (numEvents > expectedEvents) {
        log("unexpected number of events fired");
      }
    }
  }

  StreamSubscription<Integer> addLogValueListener(boolean onUiThread) {
    this.onUiThread = onUiThread;
    return eventStream.listen(this::logValueListener, onUiThread);
  }

  @Test
  public void valueSetBeforeStart() {
    eventStream.setValue(100);
    eventStream.setValue(200); // Only value that will show up.
    expectedEvents = 1;
    SwingUtilities.invokeLater(() -> {
      addLogValueListener(true);
    });
    checkLog("200");
  }

  @Test
  public void calledWithDefaultValue() {
    expectedEvents = 1;
    SwingUtilities.invokeLater(() -> {
      addLogValueListener(true);
    });
    checkLog("42");
  }

  @Test
  public void duplicateValues() throws Exception {
    expectedEvents = 6;
    SwingUtilities.invokeAndWait(() -> {
      addLogValueListener(true);
      eventStream.setValue(100);
      eventStream.setValue(100);
      eventStream.setValue(100);
      eventStream.setValue(200);
      eventStream.setValue(200);
    });
    checkLog("42", "200");
  }

  @Test
  public void nullInitialValue() throws Exception {
    expectedEvents = 3;
    SwingUtilities.invokeAndWait(() -> {
      eventStream = new EventStream<>();
      addLogValueListener(true);
      eventStream.setValue(100);
      eventStream.setValue(200);
    });
    checkLog("null", "200");
  }

  @Test
  public void ignoreValuesAfterDispose() {
    expectedEvents = 5;
    final StreamSubscription<Integer> listener = addLogValueListener(false);
    eventStream.setValue(100);
    eventStream.setValue(200);
    eventStream.setValue(300);
    listener.dispose();
    eventStream.setValue(400); // Ignored.
    eventStream.setValue(500); // Ignored.
    eventStream.setValue(9);
    // Subscribe back getting the current value (9).
    addLogValueListener(false);

    checkLog("42", "100", "200", "300", "9");
  }

  @Test
  public void doubleDispose() {
    expectedEvents = 6;
    final StreamSubscription<Integer> listener = addLogValueListener(false);
    final StreamSubscription<Integer> listener2 = addLogValueListener(false);
    eventStream.setValue(100); // This event will be received by both listeners.
    listener.dispose();
    // A second dispose is harmless and doesn't crash anything.
    listener.dispose();
    eventStream.setValue(200);
    eventStream.setValue(300);
    checkLog("42", "42", "100", "100", "200", "300");
  }

  @Test
  public void eventsFromOtherThread() {
    expectedEvents = 6;
    addLogValueListener(false);

    final Timer timer = new Timer(15, null);
    timer.setRepeats(true);
    timer.addActionListener(new ActionListener() {
      int count = 0;

      @Override
      public void actionPerformed(ActionEvent e) {
        count++;
        eventStream.setValue(count);
        if (count == 5) {
          timer.stop();
        }
      }
    });
    timer.start();

    checkLog("42", "1", "2", "3", "4", "5");
  }

  @Test
  public void eventsFromOtherThreadOnUiThread() {
    expectedEvents = 6;
    SwingUtilities.invokeLater(() -> {
      addLogValueListener(true);

      final Timer timer = new Timer(15, null);
      timer.setRepeats(true);
      timer.addActionListener(new ActionListener() {
        int count = 0;

        @Override
        public void actionPerformed(ActionEvent e) {
          count++;
          eventStream.setValue(count);
          if (count == 5) {
            timer.stop();
          }
        }
      });
      timer.start();
    });

    checkLog("42", "1", "2", "3", "4", "5");
  }

  @Test
  public void unsubscribeFromUiThread() {
    expectedEvents = 6;
    SwingUtilities.invokeLater(() -> {
      addLogValueListener(true);

      final StreamSubscription[] subscription = new StreamSubscription[]{null};
      subscription[0] = eventStream.listen((Integer value) -> {
        log("L2: " + value);
        if (value == 3) {
          log("L2: stopped");
          subscription[0].dispose();
        }
      }, true);

      final Timer timer = new Timer(15, null);
      timer.setRepeats(true);
      timer.addActionListener(new ActionListener() {
        int count = 0;

        @Override
        public void actionPerformed(ActionEvent e) {
          count++;
          eventStream.setValue(count);
          if (count == 5) {
            timer.stop();
          }
        }
      });
      timer.start();
    });

    checkLog("42", "L2: 42", "1", "L2: 1", "2", "L2: 2", "3", "L2: 3", "L2: stopped", "4", "5");
  }

  private synchronized void log(String message) {
    synchronized (logEntries) {
      logEntries.add(message);
    }
  }

  private synchronized List<String> getLogEntries() {
    synchronized (logEntries) {
      return ImmutableList.copyOf(logEntries);
    }
  }

  private void reportFailure(Exception e) {
    fail("Exception: " + e + "\nLog: " + getLogEntries().toString());
  }

  private void checkLog(String... expectedEntries) {
    final java.util.Timer timer = new java.util.Timer();
    try {
      final TimerTask task = new TimerTask() {
        @Override
        public void run() {
          timer.cancel();
          callbacksDone.completeExceptionally(new InterruptedException("Expected more events"));
          fail();
        }
      };
      timer.schedule(task, 1000);
      callbacksDone.get();
      timer.cancel();
    }
    catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    assertThat("logEntries entries are different", getLogEntries(), is(ImmutableList.copyOf(expectedEntries)));
    logEntries.clear();
  }
}
