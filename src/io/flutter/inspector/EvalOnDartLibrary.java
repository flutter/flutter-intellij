/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.Producer;
import com.intellij.xdebugger.XSourcePosition;
import io.flutter.server.vmService.DartVmServiceDebugProcess;
import io.flutter.server.vmService.VMServiceManager;
import io.flutter.utils.StreamSubscription;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.EvaluateConsumer;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Invoke methods from a specified Dart library using the observatory protocol.
 */
public class EvalOnDartLibrary implements Disposable {
  private final StreamSubscription<IsolateRef> subscription;
  private String isolateId;
  private final VmService vmService;
  @SuppressWarnings("FieldCanBeLocal") private final VMServiceManager vmServiceManager;
  private final String libraryName;
  CompletableFuture<LibraryRef> libraryRef;
  private final Alarm myRequestsScheduler;
  private static final Logger LOG = Logger.getInstance(EvalOnDartLibrary.class);

  /**
   * For robustness we ensure at most one pending request is issued at a time.
   */
  private CompletableFuture<?> allPendingRequestsDone;
  private final Object pendingRequestLock = new Object();

  /**
   * Public so that other related classes such as InspectorService can ensure their
   * requests are in a consistent order with requests which eliminates otherwise
   * surprising timing bugs such as if a request to dispose an
   * InspectorService.ObjectGroup was issued after a request to read properties
   * from an object in a group but the request to dispose the object group
   * occurred first.
   * <p>
   * The design is we have at most 1 pending request at a time. This sacrifices
   * some throughput with the advantage of predictable semantics and the benefit
   * that we are able to skip large numbers of requests if they happen to be
   * from groups of objects that should no longer be kept alive.
   * <p>
   * The optional ObjectGroup specified by isAlive, indicates whether the
   * request is still relevant or should be cancelled. This is an optimization
   * for the Inspector to avoid overloading the service with stale requests if
   * the user is quickly navigating through the UI generating lots of stale
   * requests to view specific details subtrees.
   */
  public <T> CompletableFuture<T> addRequest(InspectorService.ObjectGroup isAlive, Producer<CompletableFuture<T>> request) {
    if (isAlive != null && isAlive.isDisposed()) {
      return CompletableFuture.completedFuture(null);
    }

    // Future that completes when the request has finished.
    final CompletableFuture<T> response = new CompletableFuture<>();
    // This is an optimization to avoid sending stale requests across the wire.
    final Runnable wrappedRequest = () -> {
      if (isAlive != null && isAlive.isDisposed()) {
        response.complete(null);
        return;
      }
      final CompletableFuture<T> future = request.produce();
      future.whenCompleteAsync((v, t) -> {
        if (t != null) {
          response.completeExceptionally(t);
        }
        else {
          response.complete(v);
        }
      });
    };
    synchronized (pendingRequestLock) {
      if (allPendingRequestsDone == null || allPendingRequestsDone.isDone()) {
        allPendingRequestsDone = response;
        myRequestsScheduler.addRequest(wrappedRequest, 0);
      }
      else {
        final CompletableFuture<?> previousDone = allPendingRequestsDone;
        allPendingRequestsDone = response;
        // Actually schedule this request only after the previous request completes.
        previousDone.whenCompleteAsync((v, error) -> {
          myRequestsScheduler.addRequest(wrappedRequest, 0);
        });
      }
    }
    return response;
  }

  public EvalOnDartLibrary(String libraryName, VmService vmService, VMServiceManager vmServiceManager) {
    this.libraryName = libraryName;
    this.vmService = vmService;
    this.vmServiceManager = vmServiceManager;
    this.myRequestsScheduler = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    libraryRef = new CompletableFuture<>();

    subscription = vmServiceManager.getCurrentFlutterIsolate((isolate) -> {
      if (libraryRef.isDone()) {
        libraryRef = new CompletableFuture<>();
      }

      if (isolate != null) {
        initialize(isolate.getId());
      }
    }, true);
  }

  public String getIsolateId() {
    return isolateId;
  }

  public void dispose() {
    myRequestsScheduler.dispose();
    subscription.dispose();
    // TODO(jacobr): complete all pending futures as cancelled?
  }

  public CompletableFuture<InstanceRef> eval(String expression, Map<String, String> scope, InspectorService.ObjectGroup isAlive) {
    return addRequest(isAlive, () -> {
      final CompletableFuture<InstanceRef> future = new CompletableFuture<>();
      libraryRef.thenAcceptAsync((LibraryRef ref) -> {
        vmService.evaluate(
          getIsolateId(), ref.getId(), expression, scope,
          new EvaluateConsumer() {
            @Override
            public void onError(RPCError error) {
              future.completeExceptionally(new RuntimeException(error.getMessage()));
            }

            @Override
            public void received(ErrorRef response) {
              future.completeExceptionally(new RuntimeException(response.toString()));
            }

            @Override
            public void received(InstanceRef response) {
              future.complete(response);
            }

            @Override
            public void received(Sentinel response) {
              future.completeExceptionally(new RuntimeException(response.toString()));
            }
          }
        );
      });
      return future;
    });
  }

  @SuppressWarnings("unchecked")
  public <T extends Obj> CompletableFuture<T> getObjHelper(ObjRef instance, InspectorService.ObjectGroup isAlive) {
    return addRequest(isAlive, () -> {
      final CompletableFuture<T> future = new CompletableFuture<>();
      vmService.getObject(
        getIsolateId(), instance.getId(), new GetObjectConsumer() {
          @Override
          public void onError(RPCError error) {
            future.completeExceptionally(new RuntimeException(error.toString()));
          }

          @Override
          public void received(Obj response) {
            future.complete((T)response);
          }

          @Override
          public void received(Sentinel response) {
            future.completeExceptionally(new RuntimeException(response.toString()));
          }
        }
      );
      return future;
    });
  }

  @NotNull
  public CompletableFuture<XSourcePosition> getSourcePosition(DartVmServiceDebugProcess debugProcess,
                                                              ScriptRef script,
                                                              int tokenPos,
                                                              InspectorService.ObjectGroup isAlive) {
    return addRequest(isAlive, () -> CompletableFuture.completedFuture(debugProcess.getSourcePosition(isolateId, script, tokenPos)));
  }

  public CompletableFuture<Instance> getInstance(InstanceRef instance, InspectorService.ObjectGroup isAlive) {
    return getObjHelper(instance, isAlive);
  }

  public CompletableFuture<Library> getLibrary(LibraryRef instance, InspectorService.ObjectGroup isAlive) {
    return getObjHelper(instance, isAlive);
  }

  public CompletableFuture<ClassObj> getClass(ClassRef instance, InspectorService.ObjectGroup isAlive) {
    return getObjHelper(instance, isAlive);
  }

  public CompletableFuture<Func> getFunc(FuncRef instance, InspectorService.ObjectGroup isAlive) {
    return getObjHelper(instance, isAlive);
  }

  public CompletableFuture<Instance> getInstance(CompletableFuture<InstanceRef> instanceFuture, InspectorService.ObjectGroup isAlive) {
    return instanceFuture.thenComposeAsync((instance) -> getInstance(instance, isAlive));
  }

  private JsonObject convertMapToJsonObject(Map<String, String> map) {
    final JsonObject obj = new JsonObject();
    for (String key : map.keySet()) {
      obj.addProperty(key, map.get(key));
    }
    return obj;
  }

  private void initialize(String isolateId) {
    this.isolateId = isolateId;

    vmService.getIsolate(isolateId, new GetIsolateConsumer() {
      @Override
      public void received(Isolate response) {
        for (LibraryRef library : response.getLibraries()) {
          if (library.getUri().equals(libraryName)) {
            libraryRef.complete(library);
            return;
          }
        }
        libraryRef.completeExceptionally(new RuntimeException("Library " + libraryName + " not found."));
      }

      @Override
      public void received(Sentinel response) {
        libraryRef.completeExceptionally(new RuntimeException(response.toString()));
      }

      @Override
      public void onError(RPCError error) {
        libraryRef.completeExceptionally(new RuntimeException(error.toString()));
      }
    });
  }
}
