package io.flutter.utils;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AsyncUtilsTest {

  @Test
  public void testWhenCompleteUiThread() throws InterruptedException {
    CompletableFuture<String> future = new CompletableFuture<>();
    AtomicReference<String> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    AsyncUtils.whenCompleteUiThread(future, (value, throwable) -> {
      result.set(value);
      error.set(throwable);
      latch.countDown();
    });

    future.complete("success");
    latch.await(1, TimeUnit.SECONDS);

    assertEquals("success", result.get());
    assertNull(error.get());
  }

  @Test
  public void testInvokeLater() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean ran = new AtomicBoolean(false);

    AsyncUtils.invokeLater(() -> {
      ran.set(true);
      latch.countDown();
    });

    latch.await(1, TimeUnit.SECONDS);
    assertTrue(ran.get());
  }
}
