/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.FlutterMessages;
import io.flutter.utils.EventStream;
import io.flutter.utils.OpenApiUtils;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.ServiceExtensionConsumer;
import org.dartlang.vm.service.element.RPCError;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class DisplayRefreshRateManager {
  private static final @NotNull Logger LOG = Logger.getInstance(DisplayRefreshRateManager.class);
  private static boolean notificationDisplayedAlready = false;

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
    OpenApiUtils.safeInvokeLater(() -> {
      getDisplayRefreshRate().thenAcceptAsync(displayRefreshRateStream::setValue);
    });
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
    final CompletableFuture<Double> displayRefreshRate = new CompletableFuture<>();
    vmServiceManager.getInspectorViewId().whenComplete((String id, Throwable throwable) -> {
      if (throwable != null) {
        // We often see "java.lang.RuntimeException: Method not found" from here; perhaps a race condition?
        LOG.warn(throwable.getMessage());
        // Fail gracefully by returning the default.
        displayRefreshRate.complete(defaultRefreshRate);
      }
      else {
        invokeGetDisplayRefreshRate(id).whenComplete((Double refreshRate, Throwable t) -> {
          if (t != null) {
            LOG.warn(t.getMessage());
            // Fail gracefully by returning the default.
            displayRefreshRate.complete(defaultRefreshRate);
          }
          else {
            displayRefreshRate.complete(refreshRate);
          }
        });
      }
    });
    return displayRefreshRate;
  }

  private CompletableFuture<Double> invokeGetDisplayRefreshRate(String inspectorViewId) {
    final CompletableFuture<Double> ret = new CompletableFuture<>();

    final JsonObject params = new JsonObject();
    params.addProperty("viewId", inspectorViewId);

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
              if (!notificationDisplayedAlready) {
                FlutterMessages.showWarning("Flutter device frame rate invalid",
                                            "Device returned a target frame rate of " + fps + " FPS." + " Using 60 FPS instead.",
                                            null);
                notificationDisplayedAlready = true;
              }
              ret.complete(defaultRefreshRate);
            }
            else {
              ret.complete(fps);
            }
          }
        }
      }
    );
    return ret;
  }

  private boolean invalidFps(double fps) {
    // 24 FPS is the lowest frame rate that can be considered smooth.
    return fps < 24.0;
  }
}
