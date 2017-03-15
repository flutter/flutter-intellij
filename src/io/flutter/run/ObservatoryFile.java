/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcessZ;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import org.dartlang.vm.service.element.Script;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A specific version of a Dart file, as downloaded from Observatory.
 * <p>
 * Corresponds to a
 * <a href="https://github.com/dart-lang/sdk/blob/master/runtime/vm/service/service.md#script">Script</a>
 * in the Observatory API. A new version will be generated after a hot reload (with a different script id).
 * <p>
 * See
 */
class ObservatoryFile {
  /**
   * Maps an observatory token id to its line and column.
   */
  @NotNull
  private final TIntObjectHashMap<Position> positionMap;

  /**
   * User-visible source code downloaded from Observatory.
   * <p>
   * The LightVirtualFile has no parent directory so its name will be something like /foo.dart.
   * Since it has no location, breakpoints can't be set in this file.
   * <p>
   * This will be null if not requested when the ObservatoryFile was constructed.
   */
  @Nullable
  private final LightVirtualFile snapshot;

  ObservatoryFile(@NotNull Script script, boolean wantSnapshot) {
    positionMap = createPositionMap(script.getTokenPosTable());
    snapshot = !wantSnapshot ? null : createSnapshot(script);
  }

  boolean hasSnapshot() {
    return snapshot != null;
  }

  /**
   * Given a token id, returns the source position to display to the user.
   * <p>
   * If no local file was provided, uses the snapshot if available. (However, in that
   * case, breakpoints won't work.)
   */
  @Nullable
  XSourcePosition createPosition(@Nullable VirtualFile local, int tokenPos) {
    final VirtualFile fileToUse = local == null ? snapshot : local;
    if (fileToUse == null) return null;

    final Position pos = positionMap.get(tokenPos);
    if (pos == null) {
      return XDebuggerUtil.getInstance().createPositionByOffset(fileToUse, 0);
    }
    return XDebuggerUtil.getInstance().createPosition(fileToUse, pos.line, pos.column);
  }

  /**
   * Unpacks a position token table into a map from position id to Position.
   * <p>
   * <p>See <a href="https://github.com/dart-lang/vm_service_drivers/blob/master/dart/tool/service.md#scrip">docs</a>.
   */
  @NotNull
  private static TIntObjectHashMap<Position> createPositionMap(@NotNull final List<List<Integer>> table) {
    final TIntObjectHashMap<Position> result = new TIntObjectHashMap<>();

    for (List<Integer> line : table) {
      // Each line consists of a line number followed by (tokenId, columnNumber) pairs.
      // Both lines and columns are one-based.
      final Iterator<Integer> items = line.iterator();

      // Convert line number from one-based to zero-based.
      final int lineNumber = Math.max(0, items.next() - 1);
      while (items.hasNext()) {
        final int tokenId = items.next();
        // Convert column from one-based to zero-based.
        final int column = Math.max(0, items.next() - 1);
        result.put(tokenId, new Position(lineNumber, column));
      }
    }
    return result;
  }

  private static LightVirtualFile createSnapshot(@NotNull Script script) {
    // LightVirtualFiles have no parent directory, so just use the filename.
    // TODO(skybrian) maybe add more of the path anyway, for display?
    final String filename = PathUtil.getFileName(script.getUri());
    final LightVirtualFile snapshot = new LightVirtualFile(filename, DartFileType.INSTANCE, script.getSource());
    snapshot.setWritable(false);
    return snapshot;
  }

  /**
   * A per-isolate cache of Observatory files.
   */
  static class Cache {
    @NotNull
    private final String isolateId;

    @NotNull
    private final DartVmServiceDebugProcessZ.ScriptProvider provider;

    /**
     * A cache containing each file downloaded from Observatory. The key is a script id.
     * Each version of a file is stored as a separate entry.
     */
    private final Map<String, ObservatoryFile> versions = new THashMap<>();

    Cache(@NotNull String isolateId, @NotNull DartVmServiceDebugProcessZ.ScriptProvider provider) {
      this.isolateId = isolateId;
      this.provider = provider;
    }

    /**
     * Returns an observatory file, optionally containing a snapshot.
     * <p>
     * Downloads it if not in the cache.
     * <p>
     * Returns null if not available.
     */
    @Nullable
    ObservatoryFile downloadOrGet(@NotNull String scriptId, boolean wantSnapshot) {
      final ObservatoryFile cached = this.versions.get(scriptId);
      if (cached != null && (cached.hasSnapshot() || !wantSnapshot)) {
        return cached;
      }

      final Script script = provider.downloadScript(isolateId, scriptId);
      if (script == null) return null;

      final ObservatoryFile downloaded = new ObservatoryFile(script, wantSnapshot);
      this.versions.put(scriptId, downloaded);
      return downloaded;
    }
  }

  private static class Position {
    final int line; // zero-based
    final int column; // zero-based

    Position(int line, int column) {
      this.line = line;
      this.column = column;
    }
  }
}
