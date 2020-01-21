/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.NotificationManager;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.element.RPCError;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DisplayRefreshRateManager {
  private static final Logger LOG = Logger.getInstance(DisplayRefreshRateManager.class);
  private static final String INVALID_DISPLAY_REFRESH_RATE = "INVALID_DISPLAY_REFRESH_RATE";

  public static final double defaultRefreshRate = 60.0;

  private final VMServiceManager vmServiceManager;
  private final VmService vmService;
  private final EventStream<Double> displayRefreshRateStream;

  DisplayRefreshRateManager(VMServiceManager vmServiceManager, VmService vmService) {
    this.vmServiceManager = vmServiceManager;
    this.vmService = vmService;
    displayRefreshRateStream = new EventStream<>();
  }

  public void queryRefreshRate() {
    // This needs to happen on the UI thread.
    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().invokeLater(() -> {
      getDisplayRefreshRate().thenAcceptAsync(displayRefreshRateStream::setValue);
    });
  }

  public int getTargetMicrosPerFrame() {
    Double fps = getCurrentDisplayRefreshRateRaw();
    if (fps == null) {
      fps = defaultRefreshRate;
    }
    return (int)Math.round((Math.floor(1000000.0f / fps)));
  }

  /**
   * Returns a StreamSubscription providing the queried display refresh rate.
   * <p>
   * The current value of the subscription can be null occasionally during initial application startup and for a brief time when doing a
   * hot restart.
   */
  public StreamSubscription<Double> getCurrentDisplayRefreshRate(Consumer<Double> onValue, boolean onUIThread) {
    return displayRefreshRateStream.listen(onValue, onUIThread);
  }

  /**
   * Return the current display refresh rate, if any.
   * <p>
   * Note that this may not be immediately populated at app startup for Flutter apps. In that case, this will return
   * the default value (defaultRefreshRate). Clients that wish to be notified when the refresh rate is discovered
   * should prefer the StreamSubscription variant of this method (getCurrentDisplayRefreshRate()).
   */
  public Double getCurrentDisplayRefreshRateRaw() {
    synchronized (displayRefreshRateStream) {
      Double fps = displayRefreshRateStream.getValue();
      if (fps == null) {
        fps = defaultRefreshRate;
      }
      return fps;
    }
  }

  private CompletableFuture<Double> getDisplayRefreshRate() {
    final double unknownRefreshRate = 0.0;

    final String flutterViewId = vmServiceManager.getFlutterViewId().exceptionally(exception -> {
      // We often see "java.lang.RuntimeException: Method not found" from here; perhaps a race condition?
      LOG.warn(exception.getMessage());
      return null;
    }).join();

    if (flutterViewId == null) {
      // Fail gracefully by returning the default.
      return CompletableFuture.completedFuture(defaultRefreshRate);
    }

    return invokeGetDisplayRefreshRate(flutterViewId);
  }

  private CompletableFuture<Double> invokeGetDisplayRefreshRate(String flutterViewId) {
    final CompletableFuture<Double> ret = new CompletableFuture<>();

    final JsonObject params = new JsonObject();
    params.addProperty("viewId", flutterViewId);

    vmService.callServiceExtension(
      vmServiceManager.getCurrentFlutterIsolateRaw().getId(), ServiceExtensions.displayRefreshRate, params,
      new ServiceExtensionConsumer() {
        @Override
        public void onError(RPCError error) {
          ret.completeExceptionally(new RuntimeException(error.getMessage()));
        }

        @Override
        public void received(JsonObject object) {
          final String fpsField = "fps";

          if (object == null || !object.has(fpsField)) {
            ret.complete(null);
          }
          else {
            final double fps = object.get(fpsField).getAsDouble();
            // Defend against invalid refresh rate for Flutter Desktop devices (0.0).
            if (invalidFps(fps)) {
              NotificationManager.showWarning(
                "Flutter device frame rate invalid",
                "Device returned a target frame rate of " + fps + " FPS." + " Using 60 FPS instead.",
                INVALID_DISPLAY_REFRESH_RATE,
                true
              );
              ret.complete(defaultRefreshRate);
            } else {
              ret.complete(fps);
            }
          }
        }
      }
    );
    return ret;
  }

  private boolean invalidFps(double fps) {
    // 24 FPS is the lowest frame rate that can be considered "smooth".
    return fps < 24.0;
  }
}
