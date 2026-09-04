// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.pubServer;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.util.PubspecYamlUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.builtInWebServer.ConsoleManager;

@Service(Service.Level.PROJECT)
public final class PubServerManager implements Disposable {
  private static final Logger LOG = PluginLogger.INSTANCE.createLogger(PubServerManager.class);

  private final Project project;
  private final ConsoleManager consoleManager = new ConsoleManager();

  private String myServedSdkVersion;

  private final LoadingCache<@NotNull VirtualFile, PubServerService> myServedDirToPubService;

  public static @NotNull PubServerManager getInstance(@NotNull Project project) {
    return project.getService(PubServerManager.class);
  }

  public PubServerManager(@NotNull Project project) {
    this.project = project;

    VirtualFileManager.getInstance()
      .addVirtualFileListener(new VirtualFileListener() {
                                @Override
                                public void beforePropertyChange(final @NotNull VirtualFilePropertyEvent event) {
                                  if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
                                    contentsChanged(event);
                                  }
                                }

                                @Override
                                public void beforeFileMovement(final @NotNull VirtualFileMoveEvent event) {
                                  contentsChanged(event);
                                }

                                @Override
                                public void fileDeleted(final @NotNull VirtualFileEvent event) {
                                  contentsChanged(event);
                                }

                                @Override
                                public void contentsChanged(final @NotNull VirtualFileEvent event) {
                                  final VirtualFile file = event.getFile();
                                  if (PubspecYamlUtil.PUBSPEC_YAML.equals(file.getName()) &&
                                      file.getFileSystem() == LocalFileSystem.getInstance()) {
                                    pubspecYamlChanged(file);
                                  }
                                }
                              },
                              this);
    myServedDirToPubService = Caffeine.newBuilder().build(key -> new PubServerService(this.project, consoleManager));
  }

  private void pubspecYamlChanged(final @NotNull VirtualFile file) {
    final VirtualFile mainDir = file.getParent();
    if (mainDir == null) return;

    for (VirtualFile subdir : mainDir.getChildren()) {
      if (!subdir.isDirectory()) continue;

      final PubServerService service = myServedDirToPubService.getIfPresent(subdir);
      if (service != null) {
        Disposer.dispose(service);
      }
    }
  }

  public void send(@NotNull Channel clientChannel,
                   @NotNull FullHttpRequest clientRequest,
                   @NotNull HttpHeaders extraHeaders,
                   @NotNull VirtualFile servedDir,
                   @NotNull String pathForPubServer) {
    final DartSdk sdk = DartSdk.getDartSdk(project);
    if (sdk != null && !sdk.getVersion().equals(myServedSdkVersion)) {
      stopAllPubServerProcesses();
      myServedSdkVersion = sdk.getVersion();
    }

    try {
      // servedDir - web or test, direct child of directory containing pubspec.yaml
      myServedDirToPubService.get(servedDir).sendToPubServer(clientChannel, clientRequest, extraHeaders, servedDir, pathForPubServer);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public boolean hasAlivePubServerProcesses() {
    for (PubServerService service : myServedDirToPubService.asMap().values()) {
      if (service.isPubServerProcessAlive()) return true;
    }
    return false;
  }

  @Override
  public void dispose() {
    stopAllPubServerProcesses();
  }

  public void stopAllPubServerProcesses() {
    for (PubServerService service : myServedDirToPubService.asMap().values()) {
      try {
        Disposer.dispose(service);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }
}
