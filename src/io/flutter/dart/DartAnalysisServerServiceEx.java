/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.common.collect.ImmutableList;
import com.google.dart.server.generated.AnalysisServer;
import com.google.dart.server.internal.remote.RequestSink;
import com.google.dart.server.internal.remote.ResponseStream;
import com.google.gson.JsonObject;
import com.intellij.util.ReflectionUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.utils.ThreadUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Wrapper around the standard {@link DartAnalysisServerService} that adds ability to send arbitrary JSON requests and listen for all JSON
 * responses.
 */
public class DartAnalysisServerServiceEx {
  public final DartAnalysisServerService base;

  private final AnalysisServer analysisServer;
  private final RequestSink requestSink;
  private final List<DartAnalysisServerServiceExResponseListener> listeners = new ArrayList<>();

  public DartAnalysisServerServiceEx(DartAnalysisServerService base, AnalysisServer analysisServer) {
    this.base = base;
    this.analysisServer = analysisServer;
    this.requestSink = ReflectionUtil.getField(analysisServer.getClass(), analysisServer, RequestSink.class, "requestSink");
  }

  public void addListener(DartAnalysisServerServiceExResponseListener listener) {
    synchronized (listeners) {
      if (!listeners.contains(listener)) {
        listeners.add(listener);
      }
    }
  }

  public void removeListener(DartAnalysisServerServiceExResponseListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  @Nullable
  public String generateUniqueId() {
    try {
      final Method generateUniqueId = ReflectionUtil.getMethod(analysisServer.getClass(), "generateUniqueId");
      if (generateUniqueId != null) {
        return (String)generateUniqueId.invoke(analysisServer);
      }
    }
    catch (IllegalAccessException | InvocationTargetException ignored) {
    }
    return null;
  }

  public void sendRequest(JsonObject request) {
    requestSink.add(request);
  }

  private class ResponseStreamEx implements ResponseStream {
    final ResponseStream base;

    ResponseStreamEx(ResponseStream base) {
      this.base = base;
    }

    @Override
    public void lastRequestProcessed() {
      base.lastRequestProcessed();
    }

    @Override
    public JsonObject take() throws Exception {
      final JsonObject json = base.take();
      synchronized (listeners) {
        for (DartAnalysisServerServiceExResponseListener listener : ImmutableList.copyOf(listeners)) {
          listener.onResponse(json);
        }
      }
      return json;
    }
  }

  private static final WeakHashMap<AnalysisServer, DartAnalysisServerServiceEx> serverToServiceEx = new WeakHashMap<>();

  /**
   * TODO(scheglov) Remove when we get listening for arbitrary responses in <code>DartAnalysisServerService</code>.
   */
  public static DartAnalysisServerServiceEx get(DartAnalysisServerService base) {
    if (base == null) {
      return null;
    }

    final AnalysisServer analysisServer =
      ReflectionUtil.getField(DartAnalysisServerService.class, base, AnalysisServer.class, "myServer");
    if (analysisServer == null) {
      return null;
    }

    {
      final DartAnalysisServerServiceEx existingEx = serverToServiceEx.get(analysisServer);
      if (existingEx != null) {
        return existingEx;
      }
    }

    final ResponseStream serverResponseStream =
      ReflectionUtil.getField(analysisServer.getClass(), analysisServer, ResponseStream.class, "responseStream");
    if (serverResponseStream == null) {
      return null;
    }

    // RemoteAnalysisServerImpl starts a new ServerResponseReaderThread that reads and process JSON responses.
    // Each such thread that has a field "ResponseStream stream". The value of this field should be the same as the value of the field
    // "RemoteAnalysisServerImpl.responseStream" in the corresponding instance of RemoteAnalysisServerImpl. We replace this value with the
    // ResponseStream implementation that sends every read JSON to DartAnalysisServerServiceExResponseListener(s).
    Thread responseReaderThread = null;
    final List<Thread> threads = ThreadUtil.getCurrentGroupThreads();
    for (Thread thread : threads) {
      if (thread.getClass().getSimpleName().equals("ServerResponseReaderThread")) {
        final ResponseStream threadStream = ReflectionUtil.getField(thread.getClass(), thread, ResponseStream.class, "stream");
        if (threadStream == serverResponseStream) {
          responseReaderThread = thread;
          break;
        }
      }
    }
    if (responseReaderThread == null) {
      return null;
    }

    final DartAnalysisServerServiceEx serviceEx = new DartAnalysisServerServiceEx(base, analysisServer);
    final ResponseStream myResponseStream = serviceEx.new ResponseStreamEx(serverResponseStream);

    ReflectionUtil.setField(responseReaderThread.getClass(), responseReaderThread, ResponseStream.class, "stream", myResponseStream);

    serverToServiceEx.put(analysisServer, serviceEx);
    return serviceEx;
  }
}
