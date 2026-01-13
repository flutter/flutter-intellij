/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
import com.jetbrains.lang.dart.util.DartResolveUtil;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.dart.DartPlugin;
import io.flutter.logging.PluginLogger;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.OpenApiUtils;
import io.flutter.vmService.DartVmServiceDebugProcess;
import org.dartlang.vm.service.element.LibraryRef;
import org.dartlang.vm.service.element.Script;
import org.dartlang.vm.service.element.ScriptRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Converts positions between Dart files in Observatory and local Dart files.
 * <p>
 * Used when setting breakpoints, stepping through code, and so on while debugging.
 */
public class FlutterPositionMapper implements DartVmServiceDebugProcess.PositionMapper {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(FlutterPositionMapper.class);

  /**
   * This Project can't be non-null as we set it to null {@link #shutdown()} so the Project isn't held onto.
   * <p>
   * See https://github.com/flutter/flutter-intellij/issues/7380
   */
  @Nullable
  private Project project;

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
  private DartVmServiceDebugProcess.ScriptProvider scriptProvider;

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
  private final Map<String, ObservatoryFile.Cache> fileCache = new HashMap<>();

  public FlutterPositionMapper(@NotNull Project project,
                               @NotNull VirtualFile sourceRoot,
                               @NotNull DartUrlResolver resolver,
                               @Nullable Analyzer analyzer) {
    this.project = project;
    this.sourceRoot = sourceRoot;
    this.resolver = resolver;
    this.analyzer = analyzer;
  }

  @NotNull
  public Project getProject() {
    // Project may be null after shutdown but clients have no business accessing it after that so it's reasonable to fail here.
    return Objects.requireNonNull(project);
  }

  public void onConnect(@NotNull DartVmServiceDebugProcess.ScriptProvider provider, @Nullable String remoteBaseUri) {
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
      if (remoteSourceRoot != null) {
        if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
          LOG.info("Calculated remoteSourceRoot: " + remoteSourceRoot + " from " + remoteUri);
        }
        return;
      }
    }
    if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
      LOG.info("Could not calculate remoteSourceRoot");
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
    if (project == null || project.isDisposed()) return null;

    // Find files with the same filename (matching the suffix after the last slash).
    final PsiFile[] localFilesWithSameName = OpenApiUtils.safeRunReadAction(() -> {
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
    if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
      LOG.info("getBreakpointUris: uriByIde=" + uriByIde + " for file=" + file.getPath());
    }

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

    // package: (if applicable)
    if (analyzer != null) {
      final String uriByServer = analyzer.getUri(file.getPath());
      if (uriByServer != null) {
        results.add(uriByServer);
      }
      if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
        LOG.info("getBreakpointUris: uriByServer=" + uriByServer);
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

    if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
      LOG.info("getBreakpointUris for " + file.getPath() + ": " + results);
    }
    return results;
  }

  /**
   * Returns the local position (to display to the user) corresponding to a token position in Observatory.
   */
  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId,
                                           @NotNull final ScriptRef scriptRef,
                                           int tokenPos,
                                           CompletableFuture<String> fileFuture) {
    return getSourcePosition(isolateId, scriptRef.getId(), scriptRef.getUri(), tokenPos, fileFuture);
  }

  /**
   * Returns the local position (to display to the user) corresponding to a token position in Observatory.
   */
  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final Script script, int tokenPos) {
    return getSourcePosition(isolateId, script.getId(), script.getUri(), tokenPos);
  }

  private XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final String scriptId,
                                            @NotNull final String scriptUri, int tokenPos) {
    return getSourcePosition(isolateId, scriptId, scriptUri, tokenPos, null);
  }

  /**
   * Returns the local position (to display to the user) corresponding to a token position in Observatory.
   */
  @Nullable
  private XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final String scriptId,
                                            @NotNull final String scriptUri, int tokenPos, CompletableFuture<String> fileFuture) {
    if (scriptProvider == null) {
      LOG.warn("attempted to get source position before connected to observatory");
      return null;
    }

    final VirtualFile local = findLocalFile(scriptUri, fileFuture);

    final ObservatoryFile.Cache cache =
      fileCache.computeIfAbsent(isolateId, (id) -> new ObservatoryFile.Cache(id, scriptProvider));

    final ObservatoryFile remote = cache.downloadOrGet(scriptId, local == null);
    if (remote == null) return null;

    return remote.createPosition(local, tokenPos);
  }

  @VisibleForTesting
  @Nullable
  String getRemoteSourceRoot() {
    return remoteSourceRoot;
  }

  @Nullable
  protected VirtualFile findLocalFile(@NotNull String uri) {
    final VirtualFile file = findLocalFile(uri, null);
    if (file == null) {
      if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
        LOG.info("findLocalFile: could not find local file for " + uri);
      }
    }
    return file;
  }

  /**
   * Attempt to find a local Dart file corresponding to a script in Observatory.
   */
  @Nullable
  protected VirtualFile findLocalFile(@NotNull String uri, CompletableFuture<String> fileFuture) {
    return OpenApiUtils.safeRunReadAction(() -> {
      // This can be a remote file or URI.
      if (remoteSourceRoot != null && uri.startsWith(remoteSourceRoot)) {
        final String rootUri = StringUtil.trimEnd(resolver.getDartUrlForFile(sourceRoot), '/');
        final String suffix = uri.substring(remoteSourceRoot.length());
        return resolver.findFileByDartUrl(rootUri + suffix);
      }

      if (remoteBaseUri != null && uri.startsWith(remoteBaseUri)) {
        final String rootUri = StringUtil.trimEnd(resolver.getDartUrlForFile(sourceRoot), '/');
        final String suffix = uri.substring(remoteBaseUri.length());
        return resolver.findFileByDartUrl(rootUri + suffix);
      }

      final String remoteUri;
      if (uri.startsWith("/")) {
        // Convert a file path to a file: uri.
        remoteUri = new File(uri).toURI().toString();
      }
      else {
        remoteUri = uri;
      }

      // See if the analysis server can resolve the URI.
      if (analyzer != null && !isDartPatchUri(remoteUri)) {
        final String path = analyzer.getAbsolutePath(remoteUri);
        if (path != null) {
          if (path.startsWith("file://")) {
            LocalFileSystem.getInstance().findFileByPath(path.substring(7));
          }
          else {
            LocalFileSystem.getInstance().findFileByPath(path);
          }
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
    project = null;
  }

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
      final DartPlugin dartPluginInstance = DartPlugin.getInstance();
      final DartAnalysisServerService dartAnalysisServerService = dartPluginInstance.getAnalysisService(project);
      if (dartAnalysisServerService == null) {
        return null;
      }

      if (!dartAnalysisServerService.serverReadyForRequest()) {
        LOG.warn("Dart analysis server is not running. Some breakpoints may not work.");
        return null;
      }

      final String contextId = dartAnalysisServerService.execution_createContext(sourceLocation.getPath());
      if (contextId == null) {
        LOG.warn("Failed to get execution context from analysis server. Some breakpoints may not work.");
        return null;
      }

      return new Analyzer() {
        @Override
        @Nullable
        public String getAbsolutePath(@NotNull String dartUri) {
          return dartAnalysisServerService.execution_mapUri(contextId, dartUri);
        }

        @Override
        @Nullable
        public String getUri(@NotNull String absolutePath) {
          return dartAnalysisServerService.execution_mapUri(contextId, absolutePath);
        }

        @Override
        public void close() {
          dartAnalysisServerService.execution_deleteContext(contextId);
        }
      };
    }
  }
}
