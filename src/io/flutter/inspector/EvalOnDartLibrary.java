/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess;
import com.jetbrains.lang.dart.ide.runner.server.vmService.IsolatesInfo;
import io.flutter.run.FlutterDebugProcess;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.*;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Invoke methods from a specified Dart library using the observatory protocol.
 */
public class EvalOnDartLibrary implements Disposable {
  private String isolateId;
  private final VmService vmService;
  private final String libraryName;
  final CompletableFuture<LibraryRef> libraryRef;
  private final Alarm myRequestsScheduler;
  private static final Logger LOG = Logger.getInstance(EvalOnDartLibrary.class);

  public EvalOnDartLibrary(String libraryName, FlutterDebugProcess debugProcess, VmService vmService) {
    this.vmService = vmService;
    final Collection<IsolatesInfo.IsolateInfo> isolates = debugProcess.getIsolateInfos();
    this.libraryName = libraryName;
    this.myRequestsScheduler = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    libraryRef = new CompletableFuture<>();

    if (isolates.isEmpty()) {
      // Due to a race condition that would take fixing the Dart plugin to fix,
      // it is possible the debugProcess doesn't yet show any isolate infos
      // even though the isolates have started so we get the isolate info
      // ourselves in that case.
      vmService.getVM(new VMConsumer() {
        @Override
        public void received(VM vm) {
          final ElementList<IsolateRef> isolatesList = vm.getIsolates();
          // If Flutter applications support multiple isolates we need to add
          // logic to determine which isolate is running Flutter UI code.
          assert(isolatesList.size() == 1);
          isolateId = isolatesList.get(0).getId();
          initialize();
        }

        @Override
        public void onError(RPCError error) {
          libraryRef.completeExceptionally(new RuntimeException(error.toString()));
        }
      });
    }
    else {
      // If Flutter applications support multiple isolates we need to add
      // logic to determine which isolate is running Flutter UI code.
      assert(isolates.size() == 1);
      isolateId = Iterables.get(isolates, 0).getIsolateId();
      initialize();
    }
  }

  public String getIsolateId() {
    return isolateId;
  }

  public void dispose() {
    myRequestsScheduler.dispose();
    // TODO(jacobr): complete all pending futures as cancelled?
  }

  /**
   * Workaround until the version of the VmService 'eval' method supports
   * the scope argument.
   */
  private void callVmServiceRequest(VmService vmService, String methodName, JsonObject params, EvaluateConsumer consumer) {
    try {
      final Method method = ReflectionUtil
        .getDeclaredMethod(Class.forName("org.dartlang.vm.service.VmServiceBase"), "request", String.class, JsonObject.class,
                           Consumer.class);
      if (method == null) {
        throw new RuntimeException("Cannot find method 'request'");
      }
      method.setAccessible(true);
      method.invoke(vmService, methodName, params, consumer);
    }
    catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
      throw new RuntimeException((e.toString()));
    }
  }

  public CompletableFuture<InstanceRef> eval(String expression, Map<String, String> scope) {
    final CompletableFuture<InstanceRef> future = new CompletableFuture<>();
    //noinspection CodeBlock2Expr
    myRequestsScheduler.addRequest(() -> {
      //noinspection CodeBlock2Expr
      libraryRef.thenAcceptAsync((LibraryRef ref) -> {
        evaluateHelper(
          getIsolateId(), ref.getId(), expression, scope,
          new EvaluateConsumer() {
            @Override
            public void onError(RPCError error) {
              LOG.error(error);
              future.completeExceptionally(new RuntimeException(error.toString()));
            }

            @Override
            public void received(ErrorRef response) {
              LOG.error("Error evaluating expression:\n" + response.getMessage());
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
    }, 0);
    return future;
  }

  @SuppressWarnings("unchecked")
  public <T extends Obj> CompletableFuture<T> getObjHelper(ObjRef instance) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    //noinspection CodeBlock2Expr
    myRequestsScheduler.addRequest(() -> {
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
    }, 0);
    return future;
  }

  @NotNull
  public CompletableFuture<XSourcePosition> getSourcePosition(DartVmServiceDebugProcess debugProcess,  ScriptRef script, int tokenPos) {
    final CompletableFuture<XSourcePosition> future = new CompletableFuture<>();
    myRequestsScheduler.addRequest(() -> future.complete(debugProcess.getSourcePosition(isolateId, script, tokenPos)), 0);
    return future;
  }

  public CompletableFuture<Instance> getInstance(InstanceRef instance) {
    return getObjHelper(instance);
  }

  public CompletableFuture<Library> getLibrary(LibraryRef instance) {
    return getObjHelper(instance);
  }

  public CompletableFuture<ClassObj> getClass(ClassRef instance) {
    return getObjHelper(instance);
  }

  public CompletableFuture<Func> getFunc(FuncRef instance) {
    return getObjHelper(instance);
  }

  public CompletableFuture<Instance> getInstance(CompletableFuture<InstanceRef> instanceFuture) {
    return instanceFuture.thenComposeAsync(this::getInstance);
  }

  private void evaluateHelper(String isolateId, String targetId, String expression, Map<String, String> scope, EvaluateConsumer consumer) {
    final JsonObject params = new JsonObject();
    params.addProperty("isolateId", isolateId);
    params.addProperty("targetId", targetId);
    params.addProperty("expression", expression);
    if (scope != null) params.add("scope", convertMapToJsonObject(scope));
    callVmServiceRequest(vmService, "evaluate", params, consumer);
  }

  private JsonObject convertMapToJsonObject(Map<String, String> map) {
    final JsonObject obj = new JsonObject();
    for (String key : map.keySet()) {
      obj.addProperty(key, map.get(key));
    }
    return obj;
  }

  private void initialize() {
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
