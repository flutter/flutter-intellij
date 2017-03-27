/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.PathUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcessZ;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import gnu.trove.THashMap;
import io.flutter.dart.DartPlugin;
import org.dartlang.vm.service.element.LibraryRef;
import org.dartlang.vm.service.element.ScriptRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts positions between Dart files in Observatory and local Dart files.
 * <p>
 * Used when setting breakpoints, stepping through code, and so on while debugging.
 */
public class PositionMapper implements DartVmServiceDebugProcessZ.PositionMapper {
  @NotNull
  private final Project project;

  // TODO(skybrian) for Bazel this should be a list of source roots.
  /**
   * The directory containing the Flutter application's source code.
   * <p>
   * For pub-based projects, this should be the directory containing pubspec.yaml.
   */
  @NotNull
  private final VirtualFile sourceRoot;

  @NotNull
  private final DartUrlResolver resolver;

  /**
   * Used to ask the Dart analysis server to convert between Dart URI's and local absolute paths.
   */
  @Nullable
  private final Analyzer analyzer;

  /**
   * Callback to download a Dart file from Observatory.
   * <p>
   * Initialized when the debugger connects.
   */
  @Nullable
  private DartVmServiceDebugProcessZ.ScriptProvider scriptProvider;

  /**
   * The "devfs" base uri reported by the flutter process on startup.
   * <p>
   * Initialized when the debugger connects.
   */
  @Nullable
  private String remoteBaseUri;

  /**
   * A prefix to be removed from a remote path before looking for a corresponding file under the local source root.
   * <p>
   * Initialized shortly after connecting.
   */
  @Nullable
  private String remoteSourceRoot;

  // TODO(skybrian) clear the cache at hot restart? (Old cache entries seem unlikely to be used again.)
  /**
   * A cache containing each file version downloaded from Observatory. The key is an isolate id.
   */
  private final Map<String, ObservatoryFile.Cache> fileCache = new THashMap<>();

  public PositionMapper(@NotNull Project project,
                        @NotNull VirtualFile sourceRoot,
                        @NotNull DartUrlResolver resolver,
                        @Nullable Analyzer analyzer) {

    this.project = project;
    this.sourceRoot = sourceRoot;
    this.resolver = resolver;
    this.analyzer = analyzer;
  }

  public void onConnect(@NotNull DartVmServiceDebugProcessZ.ScriptProvider provider, @Nullable String remoteBaseUri) {
    if (this.scriptProvider != null) {
      throw new IllegalStateException("already connected");
    }
    this.scriptProvider = provider;
    this.remoteBaseUri = remoteBaseUri;
  }

  /**
   * Just after connecting, the debugger downloads the list of Dart libraries from Observatory and reports it here.
   */
  public void onLibrariesDownloaded(@NotNull final Iterable<LibraryRef> libraries) {
    // TODO(skybrian) what should we do if this gets called multiple times?
    // This happens when there is more than one isolate.
    // Currently it overwrites the previous value.

    // Calculate the remote source root.
    for (LibraryRef library : libraries) {
      final String remoteUri = library.getUri();
      if (remoteUri.startsWith(DartUrlResolver.DART_PREFIX)) continue;
      if (remoteUri.startsWith(DartUrlResolver.PACKAGE_PREFIX)) continue;
      remoteSourceRoot = findRemoteSourceRoot(remoteUri);
      if (remoteSourceRoot != null) return;
    }
  }

  /**
   * Attempts to find a directory in Observatory corresponding to the local sourceRoot.
   * <p>
   * The strategy is to find a matching local file (under sourceRoot), and remove the common suffix.
   * <p>
   * Returns null if there isn't a unique result.
   */
  private String findRemoteSourceRoot(String remotePath) {
    // Find files with the same filename (matching the suffix after the last slash).
    final PsiFile[] localFilesWithSameName = ApplicationManager.getApplication().runReadAction((Computable<PsiFile[]>)() -> {
      final String remoteFileName = PathUtil.getFileName(remotePath);
      final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(project, sourceRoot, true);
      return FilenameIndex.getFilesByName(project, remoteFileName, scope);
    });

    String match = null;
    for (PsiFile psiFile : localFilesWithSameName) {
      final VirtualFile local = DartResolveUtil.getRealVirtualFile(psiFile);
      if (local == null) continue;

      assert local.getPath().startsWith(sourceRoot.getPath() + "/");
      final String relativeLocal = local.getPath().substring(sourceRoot.getPath().length()); // starts with slash
      if (remotePath.endsWith(relativeLocal)) {
        if (match != null) {
          return null; // found multiple matches
        }
        match = remotePath.substring(0, remotePath.length() - relativeLocal.length());
      }
    }

    return match;
  }

  /**
   * Returns all possible Observatory URI's corresponding to a local file.
   * <p>
   * We don't know where the file will be so we set breakpoints in a lot of places.
   * (The URI may change after a hot restart.)
   */
  @NotNull
  public Collection<String> getBreakpointUris(@NotNull final VirtualFile file) {
    final Set<String> results = new HashSet<>();
    final String uriByIde = resolver.getDartUrlForFile(file);

    // If dart:, short circuit the results.
    if (uriByIde.startsWith(DartUrlResolver.DART_PREFIX)) {
      results.add(uriByIde);
      return results;
    }

    // file:
    if (uriByIde.startsWith(DartUrlResolver.FILE_PREFIX)) {
      results.add(threeSlashize(uriByIde));
    }
    else {
      results.add(uriByIde);
      results.add(threeSlashize(new File(file.getPath()).toURI().toString()));
    }

    // straight path - used by some VM embedders
    results.add(file.getPath());

    // package: (if applicable)
    if (analyzer != null) {
      final String uriByServer = analyzer.getUri(file.getPath());
      if (uriByServer != null) {
        results.add(uriByServer);
      }
    }

    final String path = file.getPath();
    final String root = sourceRoot.getPath();

    if (path.startsWith(root)) {
      // snapshot prefix (if applicable)
      if (remoteSourceRoot != null) {
        results.add(remoteSourceRoot + path.substring(root.length()));
      }

      // remote prefix (if applicable)
      if (remoteBaseUri != null) {
        results.add(remoteBaseUri + path.substring(root.length()));
      }
    }

    return results;
  }

  /**
   * Returns the local position (to display to the user) corresponding to a token position in Observatory.
   */
  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final ScriptRef scriptRef, int tokenPos) {
    if (scriptProvider == null) {
      LOG.warn("attempted to get source position before connected to observatory");
      return null;
    }

    final VirtualFile local = findLocalFile(scriptRef);

    final ObservatoryFile.Cache cache =
      fileCache.computeIfAbsent(isolateId, (id) -> new ObservatoryFile.Cache(id, scriptProvider));

    final ObservatoryFile remote = cache.downloadOrGet(scriptRef.getId(), local == null);
    if (remote == null) return null;

    return remote.createPosition(local, tokenPos);
  }

  @VisibleForTesting
  @Nullable
  String getRemoteSourceRoot() {
    return remoteSourceRoot;
  }

  /**
   * Attempt to find a local Dart file corresponding to a script in Observatory.
   */
  @Nullable
  private VirtualFile findLocalFile(@NotNull ScriptRef scriptRef) {
    return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
      // This can be a remote file or URI.
      final String remote = scriptRef.getUri();

      if (remoteSourceRoot != null && remote.startsWith(remoteSourceRoot)) {
        final String rootUri = StringUtil.trimEnd(resolver.getDartUrlForFile(sourceRoot), '/');
        final String suffix = remote.substring(remoteSourceRoot.length());
        return resolver.findFileByDartUrl(rootUri + suffix);
      }

      if (remoteBaseUri != null && remote.startsWith(remoteBaseUri)) {
        final String rootUri = StringUtil.trimEnd(resolver.getDartUrlForFile(sourceRoot), '/');
        final String suffix = remote.substring(remoteBaseUri.length());
        return resolver.findFileByDartUrl(rootUri + suffix);
      }

      final String remoteUri;
      if (remote.startsWith("/")) {
        // Convert a file path to a file: uri.
        remoteUri = new File(remote).toURI().toString();
      }
      else {
        remoteUri = remote;
      }

      // See if the analysis server can resolve the URI.
      if (analyzer != null && !isDartPatchUri(remoteUri)) {
        final String path = analyzer.getAbsolutePath(remoteUri);
        if (path != null) {
          return LocalFileSystem.getInstance().findFileByPath(path);
        }
      }

      // Otherwise, assume no mapping is needed and see if we can resolve it locally.
      return resolver.findFileByDartUrl(remoteUri);
    });
  }

  @NotNull
  private static String threeSlashize(@NotNull final String uri) {
    if (!uri.startsWith("file:")) return uri;
    if (uri.startsWith("file:///")) return uri;
    if (uri.startsWith("file://")) return "file:///" + uri.substring("file://".length());
    if (uri.startsWith("file:/")) return "file:///" + uri.substring("file:/".length());
    if (uri.startsWith("file:")) return "file:///" + uri.substring("file:".length());
    return uri;
  }

  private static boolean isDartPatchUri(@NotNull final String uri) {
    // dart:_builtin or dart:core-patch/core_patch.dart
    return uri.startsWith("dart:_") || uri.startsWith("dart:") && uri.contains("-patch/");
  }

  public void shutdown() {
    if (analyzer != null) {
      analyzer.close();
    }
  }

  private static final Logger LOG = Logger.getInstance(PositionMapper.class.getName());

  /**
   * Wraps a Dart analysis server and execution id for doing URI resolution for a particular Flutter app.
   * <p>
   * (Can be mocked out for unit tests.)
   */
  public interface Analyzer {
    @Nullable
    String getAbsolutePath(@NotNull String dartUri);

    @Nullable
    String getUri(@NotNull String absolutePath);

    void close();

    /**
     * Sets up the analysis server to resolve URI's for a Flutter app, if possible.
     *
     * @param sourceLocation the file containing the app's main() method, or a directory containing it.
     */
    @Nullable
    static Analyzer create(@NotNull Project project, @NotNull VirtualFile sourceLocation) {
      final DartAnalysisServerService service = DartPlugin.getInstance().getAnalysisService(project);
      if (!service.serverReadyForRequest(project)) {
        // TODO(skybrian) make this required to debug at all? It seems bad for breakpoints to be flaky.
        LOG.warn("Dart analysis server is not running. Some breakpoints may not work.");
        return null;
      }

      final String contextId = service.execution_createContext(sourceLocation.getPath());
      if (contextId == null) {
        LOG.warn("Failed to get execution context from analysis server. Some breakpoints may not work.");
        return null;
      }

      return new Analyzer() {
        @Override
        @Nullable
        public String getAbsolutePath(@NotNull String dartUri) {
          return service.execution_mapUri(contextId, null, dartUri);
        }

        @Override
        @Nullable
        public String getUri(@NotNull String absolutePath) {
          return service.execution_mapUri(contextId, absolutePath, null);
        }

        @Override
        public void close() {
          service.execution_deleteContext(contextId);
        }
      };
    }
  }
}
