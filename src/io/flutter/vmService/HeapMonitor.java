/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import org.dartlang.vm.service.consumer.GetMemoryUsageConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

public class HeapMonitor {
  private static final Logger LOG = Logger.getInstance(HeapMonitor.class);

  private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private static final int POLL_PERIOD_IN_MS = 1000;

  public interface HeapListener {
    void handleMemoryUsage(List<MemoryUsage> memoryUsages);
  }

  static class HeapObject extends Obj {
    HeapObject(@NotNull JsonObject json) {
      super(json);
    }

    int getAsInt(String memberName) {
      return json.get(memberName).getAsInt();
    }

    String getAsString(String memberName) {
      return json.get(memberName).getAsString();
    }

    JsonObject getAsJsonObject(String memberName) {
      final JsonElement element = json.get(memberName);
      return element != null ? element.getAsJsonObject() : null;
    }

    Set<Map.Entry<String, JsonElement>> getEntries(String memberName) {
      final JsonObject object = getAsJsonObject(memberName);
      return object != null ? object.entrySet() : Collections.emptySet();
    }
  }

  public static class HeapSample {
    final int bytes;
    final int external;

    public long getSampleTime() {
      return sampleTime;
    }

    public final long sampleTime;

    public HeapSample(int bytes, int external) {
      this.bytes = bytes;
      this.external = external;

      this.sampleTime = System.currentTimeMillis();
    }

    public int getBytes() {
      return bytes;
    }

    public int getExternal() {
      return external;
    }

    @Override
    public String toString() {
      return "bytes: " + bytes;
    }
  }

  private final List<HeapMonitor.HeapListener> heapListeners = new ArrayList<>();
  private ScheduledFuture pollingScheduler;

  @NotNull private final VmServiceWrapper vmServiceWrapper;

  public HeapMonitor(@NotNull VmServiceWrapper vmServiceWrapper) {
    this.vmServiceWrapper = vmServiceWrapper;
  }

  public void addListener(@NotNull HeapMonitor.HeapListener listener) {
    heapListeners.add(listener);
  }

  public void removeListener(@NotNull HeapMonitor.HeapListener listener) {
    heapListeners.add(listener);
  }

  public boolean hasListeners() {
    return !heapListeners.isEmpty();
  }

  public void start() {
    pollingScheduler = executor.scheduleWithFixedDelay(this::poll, 100, POLL_PERIOD_IN_MS, TimeUnit.MILLISECONDS);
  }

  private int pollingClients = 0;

  public void addPollingClient() {
    pollingClients++;
  }

  public void removePollingClient() {
    pollingClients--;
  }

  private void poll() {
    if (pollingClients > 0) {
      collectMemoryUsage();
    }
  }

  private void collectMemoryUsage() {
    final List<IsolateRef> isolateRefs = vmServiceWrapper.getExistingIsolates();
    if (isolateRefs.isEmpty()) {
      return;
    }

    final List<MemoryUsage> memoryUsage = new ArrayList<>();

    final CountDownLatch latch = new CountDownLatch(isolateRefs.size());

    for (IsolateRef isolateRef : isolateRefs) {
      vmServiceWrapper.getVmService().getMemoryUsage(isolateRef.getId(), new GetMemoryUsageConsumer() {
        @Override
        public void received(MemoryUsage usage) {
          memoryUsage.add(usage);
          latch.countDown();
        }

        @Override
        public void received(Sentinel sentinel) {
          latch.countDown();
        }

        @Override
        public void onError(RPCError error) {
          // TODO(devoncarew): Remove rpcInternalError once https://github.com/dart-lang/webdev/issues/781
          // lands and rolls into flutter.
          final int rpcInternalError = -32603;
          final int rpcMethodNotFound = -32601;
          // {"jsonrpc":"2.0","error":{"code":-32603,"message":"UnimplementedError..."}}
          if (error.getCode() == rpcInternalError || error.getCode() == rpcMethodNotFound) {
            handleMemoryApiNotSupported();
          }

          latch.countDown();
        }
      });
    }

    try {
      latch.await();
    }
    catch (InterruptedException ignored) {
    }

    if (pollingScheduler != null) {
      heapListeners.forEach(listener -> listener.handleMemoryUsage(memoryUsage));
    }
  }

  private void handleMemoryApiNotSupported() {
    stop();
  }

  public void stop() {
    if (pollingScheduler != null) {
      pollingScheduler.cancel(false);
      pollingScheduler = null;
    }
  }
}
