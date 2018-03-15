/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.lang.dart.ide.runner.server.vmService.IsolatesInfo;
import io.flutter.run.FlutterDebugProcess;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.element.Isolate;
import org.dartlang.vm.service.element.Obj;
import org.dartlang.vm.service.element.RPCError;
import org.dartlang.vm.service.element.Sentinel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO(pq): handle GC events
// TODO(pq): improve error handling
public class HeapMonitor {

  private static final Logger LOG = Logger.getInstance(HeapMonitor.class);

  private static final int POLL_PERIOD_IN_MS = 1000;

  public interface HeapListener {
    void update(List<IsolateObject> isolates);
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
    HeapSpace(@NotNull JsonObject json) {
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
  }

  public static class IsolateObject extends HeapObject {
    IsolateObject(@NotNull JsonObject json) {
      super(json);
    }

    public Iterable<HeapSpace> getHeaps() {
      final List<HeapSpace> heaps = new ArrayList<>();
      for (Map.Entry<String, JsonElement> entry : getEntries("_heaps")) {
        heaps.add(new HeapSpace(entry.getValue().getAsJsonObject()));
      }
      return heaps;
    }
  }

  public static class HeapSample {
    final int bytes;
    final boolean isGC;

    public HeapSample(int bytes, boolean isGC) {
      this.bytes = bytes;
      this.isGC = isGC;
    }

    public int getBytes() {
      return bytes;
    }

    @Override
    public String toString() {
      return "bytes: " + bytes + (isGC ? " (GC)" : "");
    }
  }

  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
  private final List<HeapMonitor.HeapListener> heapListeners = new ArrayList<>();

  @NotNull
  private final VmService vmService;
  @NotNull
  private final FlutterDebugProcess debugProcess;

  public HeapMonitor(@NotNull VmService vmService, @NotNull FlutterDebugProcess debugProcess) {
    this.vmService = vmService;
    this.debugProcess = debugProcess;
  }

  public void addListener(@NotNull HeapMonitor.HeapListener listener) {
    heapListeners.add(listener);
  }

  public void removeListener(@NotNull HeapMonitor.HeapListener listener) {
    heapListeners.add(listener);
  }

  void start() {
    executor.scheduleAtFixedRate(this::poll, 0, POLL_PERIOD_IN_MS, TimeUnit.MILLISECONDS);
  }

  private void poll() {

    final Collection<IsolatesInfo.IsolateInfo> isolateInfos = debugProcess.getIsolateInfos();
    // Stash count so we can know when we've processed them all.
    final int isolateCount = isolateInfos.size();

    final List<IsolateObject> isolates = new ArrayList<>();
    for (IsolatesInfo.IsolateInfo info : isolateInfos) {
      vmService.getIsolate(info.getIsolateId(), new GetIsolateConsumer() {
        @Override
        public void received(Isolate isolateResponse) {
          isolates.add(new IsolateObject(isolateResponse.getJson()));

          // Only update when we're done collecting from all isolates.
          if (isolates.size() == isolateCount) {
            notifyListeners(isolates);
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

  private void notifyListeners(List<IsolateObject> isolates) {
    heapListeners.forEach(listener -> listener.update(isolates));
  }

  void stop() {
    try {
      if (!executor.isShutdown()) {
        executor.shutdown();
      }
    }
    catch (SecurityException e) {
      // TODO(pq): ignore?
      LOG.warn(e);
    }
  }
}
