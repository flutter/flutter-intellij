/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.server.vmService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.run.FlutterDebugProcess;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// TODO(pq): improve error handling
public class HeapMonitor {
  private static final Logger LOG = Logger.getInstance(HeapMonitor.class);

  private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  private static final int POLL_PERIOD_IN_MS = 1000;

  public interface HeapListener {
    void handleIsolatesInfo(VM vm, List<IsolateObject> isolates);

    void handleGCEvent(IsolateRef iIsolateRef, HeapSpace newHeapSpace, HeapSpace oldHeapSpace);
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
      return json.get(memberName).getAsJsonObject();
    }

    Set<Map.Entry<String, JsonElement>> getEntries(String memberName) {
      return getAsJsonObject(memberName).entrySet();
    }
  }

  public static class HeapSpace extends HeapObject {
    public HeapSpace(@NotNull JsonObject json) {
      super(json);
    }

    public int getUsed() {
      return getAsInt("used");
    }

    public int getCapacity() {
      return getAsInt("capacity");
    }

    public int getExternal() {
      return getAsInt("external");
    }

    public double getTime() {
      return json.get("time").getAsDouble();
    }
  }

  public static class IsolateObject extends HeapObject {
    IsolateObject(@NotNull JsonObject json) {
      super(json);
    }

    public List<HeapSpace> getHeaps() {
      final List<HeapSpace> heaps = new ArrayList<>();
      for (Map.Entry<String, JsonElement> entry : getEntries("_heaps")) {
        heaps.add(new HeapSpace(entry.getValue().getAsJsonObject()));
      }
      return heaps;
    }
  }

  public static class HeapSample {
    final int bytes;
    final int external;
    final boolean isGC;

    public long getSampleTime() {
      return sampleTime;
    }

    public final long sampleTime;

    public HeapSample(int bytes, int external, boolean isGC) {
      this.bytes = bytes;
      this.external = external;
      this.isGC = isGC;

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
      return "bytes: " + bytes + (isGC ? " (GC)" : "");
    }
  }

  private final List<HeapMonitor.HeapListener> heapListeners = new ArrayList<>();
  private ScheduledFuture pollingScheduler;

  @NotNull
  private final VmService vmService;

  private boolean isPolling;

  public HeapMonitor(@NotNull VmService vmService, @NotNull FlutterDebugProcess debugProcess) {
    this.vmService = vmService;
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
    isPolling = true;
    pollingScheduler = executor.scheduleAtFixedRate(this::poll, 0, POLL_PERIOD_IN_MS, TimeUnit.MILLISECONDS);
  }

  public void pausePolling() {
    isPolling = false;
  }

  public void resumePolling() {
    isPolling = true;
  }

  private void poll() {
    if (!isPolling) {
      return;
    }

    vmService.getVM(new VMConsumer() {
      @Override
      public void received(VM vm) {
        collectIsolateInfo(vm);
      }

      @Override
      public void onError(RPCError error) {
      }
    });
  }

  private void collectIsolateInfo(VM vm) {
    final ElementList<IsolateRef> isolateRefs = vm.getIsolates();

    // Stash count so we can know when we've processed them all.
    final int isolateCount = isolateRefs.size();

    final List<IsolateObject> isolates = new ArrayList<>();

    for (IsolateRef isolateRef : isolateRefs) {
      vmService.getIsolate(isolateRef.getId(), new GetIsolateConsumer() {
        @Override
        public void received(Isolate isolateResponse) {
          isolates.add(new IsolateObject(isolateResponse.getJson()));

          // Only update when we're done collecting from all isolates.
          if (isolates.size() == isolateCount) {
            notifyListeners(vm, isolates);
          }
        }

        @Override
        public void received(Sentinel sentinel) {
          // Ignored.
        }

        @Override
        public void onError(RPCError error) {
          // TODO(pq): handle?
        }
      });
    }
  }

  public void handleGCEvent(IsolateRef isolateRef, HeapSpace newHeapSpace, HeapSpace oldHeapSpace) {
    heapListeners.forEach(listener -> listener.handleGCEvent(isolateRef, newHeapSpace, oldHeapSpace));
  }

  private void notifyListeners(VM vm, List<IsolateObject> isolates) {
    heapListeners.forEach(listener -> listener.handleIsolatesInfo(vm, isolates));
  }

  public void stop() {
    if (pollingScheduler != null) {
      pollingScheduler.cancel(false);
      pollingScheduler = null;
    }
  }
}
