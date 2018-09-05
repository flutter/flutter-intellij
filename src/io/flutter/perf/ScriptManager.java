/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.GetLibraryConsumer;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/// XXX probably not needed.
class ScriptManager {
  private static final long RESPONSE_WAIT_TIMEOUT = 3000;

  @NotNull private final VmService vmService;

  private final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

  private IsolateRef isolateRef;
  private final Map<String, Script> scriptMap = new HashMap<>();
  private final Map<String, TIntObjectHashMap<Pair<Integer, Integer>>> linesAndColumnsMap = new THashMap<>();

  public ScriptManager(@NotNull VmService vmService) {
    this.vmService = vmService;
  }

  public void reset() {
    scriptMap.clear();
  }

  public void setCurrentIsolate(IsolateRef isolateRef) {
    this.isolateRef = isolateRef;
  }

  @Nullable
  private Isolate getCurrentIsolate() {
    final Ref<Isolate> resultRef = Ref.create();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    vmService.getIsolate(isolateRef.getId(), new GetIsolateConsumer() {
      @Override
      public void received(Isolate isolate) {
        resultRef.set(isolate);
        semaphore.up();
      }

      @Override
      public void received(Sentinel sentinel) {
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    });
    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  @Nullable
  public ScriptRef getScriptRefFor(@NotNull VirtualFile file) {
    final Isolate isolate = getCurrentIsolate();
    if (isolate == null) {
      return null;
    }

    for (LibraryRef libraryRef : isolate.getLibraries()) {
      final String uri = libraryRef.getUri();

      if (uri.startsWith("file:")) {
        final VirtualFile libraryFile = virtualFileManager.findFileByUrl(uri);

        if (file.equals(libraryFile)) {
          final Library library = getLibrary(libraryRef);
          if (library != null) {
            if (!library.getScripts().isEmpty()) {
              // TODO(devoncarew): If more than one, should we return the newest script?
              return library.getScripts().get(0);
            }
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private Library getLibrary(LibraryRef libraryRef) {
    // TODO(devoncarew): Consider changing the signature to `CompletableFuture getLibrary(LibraryRef instance)`
    // (see also the EvalOnDartLibrary implementation).

    final Ref<Library> resultRef = Ref.create();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    vmService.getLibrary(isolateRef.getId(), libraryRef.getId(), new GetLibraryConsumer() {
      @Override
      public void received(Library library) {
        resultRef.set(library);
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    });
    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  public void populateFor(ScriptRef scriptRef) {
    if (!scriptMap.containsKey(scriptRef.getId())) {
      scriptMap.put(scriptRef.getId(), getScriptSync(scriptRef));
      linesAndColumnsMap.put(scriptRef.getId(), createTokenPosToLineAndColumnMap(scriptMap.get(scriptRef.getId())));
    }
  }

  public Pair<Integer, Integer> getLineColumnPosForTokenPos(@NotNull ScriptRef scriptRef, int tokenPos) {
    final TIntObjectHashMap<Pair<Integer, Integer>> map = linesAndColumnsMap.get(scriptRef.getId());
    return map == null ? null : map.get(tokenPos);
  }

  private Script getScriptSync(@NotNull final ScriptRef scriptRef) {
    final Ref<Script> resultRef = Ref.create();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    vmService.getObject(isolateRef.getId(), scriptRef.getId(), new GetObjectConsumer() {
      @Override
      public void received(Obj script) {
        resultRef.set((Script)script);
        semaphore.up();
      }

      @Override
      public void received(Sentinel response) {
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    });

    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  private static TIntObjectHashMap<Pair<Integer, Integer>> createTokenPosToLineAndColumnMap(@Nullable final Script script) {
    if (script == null) {
      return null;
    }

    // Each subarray consists of a line number followed by (tokenPos, columnNumber) pairs;
    // see https://github.com/dart-lang/vm_service_drivers/blob/master/dart/tool/service.md#script.
    final TIntObjectHashMap<Pair<Integer, Integer>> result = new TIntObjectHashMap<>();

    for (List<Integer> lineAndPairs : script.getTokenPosTable()) {
      final Iterator<Integer> iterator = lineAndPairs.iterator();
      final int line = Math.max(0, iterator.next() - 1);
      while (iterator.hasNext()) {
        final int tokenPos = iterator.next();
        final int column = Math.max(0, iterator.next() - 1);
        result.put(tokenPos, Pair.create(line, column));
      }
    }

    return result;
  }

  @Nullable
  public Script getScriptFor(@NotNull ScriptRef ref) {
    return scriptMap.get(ref.getId());
  }
}
