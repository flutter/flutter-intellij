/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException;
import com.teamdev.jxbrowser.engine.RenderingMode;
import io.flutter.FlutterInitializer;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FileUtils;
import io.flutter.utils.JxBrowserUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JxBrowser provides Chromium to display web pages within IntelliJ. This class manages downloading the required files and adding them to
 * the class path.
 */
public class JxBrowserManager {
  private static JxBrowserManager manager;

  protected static final String DOWNLOAD_PATH =
    PathManager.getPluginsPath() + File.separatorChar + "flutter-intellij" + File.separatorChar + "jxbrowser";
  private static final AtomicReference<JxBrowserStatus> status = new AtomicReference<>(JxBrowserStatus.NOT_INSTALLED);
  private static final AtomicBoolean listeningForSetting = new AtomicBoolean(false);
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);
  private static CompletableFuture<JxBrowserStatus> installation = new CompletableFuture<>();
  public static final String ANALYTICS_CATEGORY = "jxbrowser";
  private static InstallationFailedReason latestFailureReason;

  private JxBrowserManager() {
  }

  @NotNull
  public static JxBrowserManager getInstance() {
    if (manager == null) {
      manager = new JxBrowserManager();
    }
    return manager;
  }

  @VisibleForTesting
  protected static void resetForTest() {
    status.set(JxBrowserStatus.NOT_INSTALLED);
  }

  public JxBrowserStatus getStatus() {
    return status.get();
  }

  public InstallationFailedReason getLatestFailureReason() {
    return latestFailureReason;
  }

  /**
   * Call {@link #setUp} before this function to ensure that an installation has started.
   */
  public JxBrowserStatus waitForInstallation(int seconds) throws TimeoutException {
    try {
      return installation.get(seconds, TimeUnit.SECONDS);
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.info("Waiting for JxBrowser to install threw an exception");
      return null;
    }
  }

  public void retryFromFailed(@NotNull Project project) {
    if (!status.compareAndSet(JxBrowserStatus.INSTALLATION_FAILED, JxBrowserStatus.NOT_INSTALLED)) {
      return;
    }
    LOG.info(project.getName() + ": Retrying JxBrowser installation");
    setUp(project);
  }

  private class SettingsListener implements FlutterSettings.Listener {
    final Project project;

    public SettingsListener(Project project) {
      this.project = project;
    }

    @Override
    public void settingsChanged() {
      final FlutterSettings settings = FlutterSettings.getInstance();

      // Set up JxBrowser files if the embedded inspector option has been turned on and the files aren't already loaded.
      if (settings.isEnableEmbeddedBrowsers() && getStatus().equals(JxBrowserStatus.NOT_INSTALLED)) {
        setUp(project);
      }
    }
  }

  private void setStatusFailed(InstallationFailedReason reason) {
    setStatusFailed(reason, null);
  }

  private void setStatusFailed(InstallationFailedReason reason, Long time) {
    StringBuilder eventName = new StringBuilder();
    eventName.append("installationFailed-");
    eventName.append(reason.failureType);
    if (reason.detail != null) {
      eventName.append("-");
      eventName.append(reason.detail);
    }

    if (time != null) {
      FlutterInitializer.getAnalytics().sendEventMetric(ANALYTICS_CATEGORY, eventName.toString(), time.intValue());
    } else {
      FlutterInitializer.getAnalytics().sendEvent(ANALYTICS_CATEGORY, eventName.toString());
    }

    latestFailureReason = reason;
    status.set(JxBrowserStatus.INSTALLATION_FAILED);
    installation.complete(JxBrowserStatus.INSTALLATION_FAILED);
  }

  public void listenForSettingChanges(@NotNull Project project) {
    if (!listeningForSetting.compareAndSet(false, true)) {
      // We can return early because another project already triggered the listener.
      return;
    }

    FlutterSettings.getInstance().addListener(new SettingsListener(project));
  }

  public void setUp(@NotNull Project project) {
    if (!status.compareAndSet(JxBrowserStatus.NOT_INSTALLED, JxBrowserStatus.INSTALLATION_IN_PROGRESS)) {
      // This check ensures that an IDE only downloads and installs JxBrowser once, even if multiple projects are open.
      // If already in progress, let calling point wait until success or failure (it may make sense to call setUp but proceed).
      // If already succeeded or failed, no need to continue.
      return;
    }

    // Retrieve key
    try {
      final String key = JxBrowserUtils.getJxBrowserKey();
      System.setProperty(JxBrowserUtils.LICENSE_PROPERTY_NAME, key);
    }
    catch (FileNotFoundException e) {
      LOG.info(project.getName() + ": Unable to find JxBrowser license key file", e);
      setStatusFailed(new InstallationFailedReason(FailureType.MISSING_KEY));
      return;
    }

    // Check that user is not on M1 mac.
    if (JxBrowserUtils.isM1Mac()) {
      LOG.info(project.getName() + ": Skipping downloads due to M1");
      setStatusFailed(new InstallationFailedReason(
              FailureType.SYSTEM_INCOMPATIBLE,
              "The embedded browser is not yet supported for Macs using the M1 chip."
      ));
      return;
    }

    // If installation future has not finished, we don't want to overwrite it. There could be other code listening for the previous attempt
    // to succeed or fail.
    // We expect to create a new CompletableFuture only if the previous installation attempt failed.
    if (installation.isDone()) {
      installation = new CompletableFuture<>();
    }

    LOG.info(project.getName() + ": Installing JxBrowser");

    final boolean directoryExists = FileUtils.getInstance().makeDirectory(DOWNLOAD_PATH);
    if (!directoryExists) {
      LOG.info(project.getName() + ": Unable to create directory for JxBrowser files");
      setStatusFailed(new InstallationFailedReason(FailureType.DIRECTORY_CREATION_FAILED));
      return;
    }

    final String platformFileName;
    try {
      platformFileName = JxBrowserUtils.getPlatformFileName();
    }
    catch (FileNotFoundException e) {
      LOG.info(project.getName() + ": Unable to find JxBrowser platform file for " + SystemInfo.getOsNameAndVersion());
      setStatusFailed(new InstallationFailedReason(FailureType.MISSING_PLATFORM_FILES, SystemInfo.getOsNameAndVersion()));
      return;
    }

    // Check whether the files already exist.
    final String[] fileNames = {platformFileName, JxBrowserUtils.getApiFileName(), JxBrowserUtils.getSwingFileName()};
    boolean allDownloaded = true;
    for (String fileName : fileNames) {
      if (!FileUtils.getInstance().fileExists(getFilePath(fileName))) {
        allDownloaded = false;
        break;
      }
    }

    if (allDownloaded) {
      LOG.info(project.getName() + ": JxBrowser platform files already exist, skipping download");
      loadClasses(fileNames);
      return;
    }

    // Delete any already existing files.
    // TODO(helin24): Handle if files cannot be deleted.
    for (String fileName : fileNames) {
      final String filePath = getFilePath(fileName);
      if (!FileUtils.getInstance().deleteFile(filePath)) {
        LOG.info(project.getName() + ": Existing file could not be deleted - " + filePath);
      }
    }

    downloadJxBrowser(project, fileNames);
  }

  protected void downloadJxBrowser(Project project, String[] fileNames) {
    // The FileDownloader API is used by other plugins - e.g.
    // https://github.com/JetBrains/intellij-community/blob/b09f8151e0d189d70363266c3bb6edb5f6bfeca4/plugins/markdown/src/org/intellij/plugins/markdown/ui/preview/javafx/JavaFXInstallator.java#L48
    final List<FileDownloader> fileDownloaders = new ArrayList<>();
    final DownloadableFileService service = DownloadableFileService.getInstance();
    for (String fileName : fileNames) {
      final DownloadableFileDescription
        description = service.createFileDescription(JxBrowserUtils.getDistributionLink(fileName), fileName);
      fileDownloaders.add(service.createDownloader(Collections.singletonList(description), fileName));
    }

    final Task.Backgroundable task = new Task.Backgroundable(project, "Downloading jxbrowser") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String currentFileName = null;
        final long startTime = System.currentTimeMillis();
        try {
          for (int i = 0; i < fileDownloaders.size(); i++) {
            final FileDownloader downloader = fileDownloaders.get(i);
            currentFileName = fileNames[i];
            final Pair<File, DownloadableFileDescription> download =
              ContainerUtil.getFirstItem(downloader.download(new File(DOWNLOAD_PATH)));
            final File file = download != null ? download.first : null;
            if (file != null) {
              LOG.info(project.getName() + ": JxBrowser file downloaded: " + file.getAbsolutePath());
            }
          }

          FlutterInitializer.getAnalytics().sendEvent(ANALYTICS_CATEGORY, "filesDownloaded");
          loadClasses(fileNames);
        }
        catch (IOException e) {
          final long elapsedTime = System.currentTimeMillis() - startTime;
          LOG.info(project.getName() + ": JxBrowser file downloaded failed: " + currentFileName);
          setStatusFailed(new InstallationFailedReason(FailureType.FILE_DOWNLOAD_FAILED, currentFileName + ":" + e.getMessage()), elapsedTime);
        }
      }
    };
    final BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private void loadClasses(String[] fileNames) {
    for (String fileName : fileNames) {
      final String fullPath = getFilePath(fileName);

      try {
        FileUtils.getInstance().loadClass(this.getClass().getClassLoader(), fullPath);
      } catch (Exception ex) {
        LOG.info("Failed to load JxBrowser file", ex);
        setStatusFailed(new InstallationFailedReason(FailureType.CLASS_LOAD_FAILED));
        return;

      }
      LOG.info("Loaded JxBrowser file successfully: " + fullPath);
    }
    try {
      final UnsupportedRenderingModeException test = new UnsupportedRenderingModeException(RenderingMode.HARDWARE_ACCELERATED);
    } catch (NoClassDefFoundError e) {
      LOG.info("Failed to find JxBrowser class");
      setStatusFailed(new InstallationFailedReason(FailureType.CLASS_NOT_FOUND));
      return;
    }
    FlutterInitializer.getAnalytics().sendEvent(ANALYTICS_CATEGORY, "installed");
    status.set(JxBrowserStatus.INSTALLED);
    installation.complete(JxBrowserStatus.INSTALLED);
  }

  private void loadClasses2021(String[] fileNames) {
    List<Path> paths = new ArrayList<>();

    try {
      for (String fileName: fileNames) {
        paths.add(Paths.get(getFilePath(fileName)));
      }
      FileUtils.getInstance().loadPaths(this.getClass().getClassLoader(), paths);
    } catch (Exception ex) {
      LOG.info("Failed to load JxBrowser file", ex);
      setStatusFailed(new InstallationFailedReason(FailureType.CLASS_LOAD_FAILED));
      return;
    }

    try {
      final UnsupportedRenderingModeException test = new UnsupportedRenderingModeException(RenderingMode.HARDWARE_ACCELERATED);
    } catch (NoClassDefFoundError e) {
      LOG.info("Failed to find JxBrowser class");
      setStatusFailed(new InstallationFailedReason(FailureType.CLASS_NOT_FOUND));
      return;
    }
    FlutterInitializer.getAnalytics().sendEvent(ANALYTICS_CATEGORY, "installed");
    status.set(JxBrowserStatus.INSTALLED);
    installation.complete(JxBrowserStatus.INSTALLED);
  }

  private String getFilePath(String fileName) {
    return DOWNLOAD_PATH + File.separatorChar + fileName;
  }
}

