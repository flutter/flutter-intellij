/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import com.google.gson.JsonObject;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.ExtensionData;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.LinkedList;
import java.util.List;

public class FlutterFramesMonitor {
  static final int maxFrames = 200;

  private final DisplayRefreshRateManager displayRefreshRateManager;

  private final EventDispatcher<Listener> eventDispatcher = EventDispatcher.create(Listener.class);

  private long lastEventFinished = 0;

  public interface Listener extends EventListener {
    void handleFrameEvent(FlutterFrameEvent event);
  }

  public class FlutterFrameEvent {
    public final int frameId;
    public final long startTimeMicros;
    public final long elapsedMicros;
    public final boolean frameSetStart;

    FlutterFrameEvent(ExtensionData data, long lastEventFinished) {
      final JsonObject json = data.getJson();
      frameId = json.get("number").getAsInt();
      startTimeMicros = json.get("startTime").getAsLong();
      elapsedMicros = json.get("elapsed").getAsLong();
      frameSetStart = (startTimeMicros - lastEventFinished) > (displayRefreshRateManager.getTargetMicrosPerFrame() * 2);
    }

    public long getFrameFinishedMicros() {
      return startTimeMicros + elapsedMicros;
    }

    public boolean isSlowFrame() {
      return elapsedMicros > displayRefreshRateManager.getTargetMicrosPerFrame();
    }

    public int hashCode() {
      return frameId;
    }

    public boolean equals(Object other) {
      return other instanceof FlutterFrameEvent && ((FlutterFrameEvent)other).frameId == frameId;
    }

    public String toString() {
      return "#" + frameId + " " + elapsedMicros + "µs";
    }
  }

  public List<FlutterFrameEvent> frames = new LinkedList<>();

  public FlutterFramesMonitor(@NotNull DisplayRefreshRateManager displayRefreshRateManager, @NotNull VmService vmService) {
    this.displayRefreshRateManager = displayRefreshRateManager;
    vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void received(String streamId, Event event) {
        onVmServiceReceived(streamId, event);
      }

      @Override
      public void connectionClosed() {
      }
    });
  }

  private void onVmServiceReceived(String streamId, Event event) {
    if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
      if (StringUtil.equals("Flutter.Frame", event.getExtensionKind())) {
        handleFlutterFrame(event);
      }
    }
  }

  public boolean hasFps() {
    return !frames.isEmpty();
  }

  /**
   * Return the most recent FPS value.
   */
  public double getFPS() {
    int frameCount = 0;
    int costCount = 0;

    synchronized (this) {
      for (FlutterFrameEvent frame : frames) {
        frameCount++;

        final int targetMicrosPerFrame = displayRefreshRateManager.getTargetMicrosPerFrame();
        long thisCost = frame.elapsedMicros / targetMicrosPerFrame;
        if (frame.elapsedMicros > (thisCost * targetMicrosPerFrame)) {
          thisCost++;
        }

        costCount += thisCost;

        if (frame.frameSetStart) {
          break;
        }
      }
    }

    if (costCount == 0) {
      return 0.0;
    }

    final double targetDisplayRefreshRate = displayRefreshRateManager.getCurrentDisplayRefreshRateRaw();
    return frameCount * targetDisplayRefreshRate / costCount;
  }

  public void addListener(Listener listener) {
    eventDispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    eventDispatcher.removeListener(listener);
  }

  private void handleFlutterFrame(Event event) {
    final FlutterFrameEvent frameEvent = new FlutterFrameEvent(event.getExtensionData(), lastEventFinished);
    lastEventFinished = frameEvent.getFrameFinishedMicros();

    synchronized (this) {
      frames.add(0, frameEvent);
      if (frames.size() > maxFrames) {
        frames.remove(frames.size() - 1);
      }
    }

    eventDispatcher.getMulticaster().handleFrameEvent(frameEvent);
  }
}
