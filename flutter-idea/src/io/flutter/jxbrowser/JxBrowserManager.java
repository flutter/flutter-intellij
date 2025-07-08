/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException;
import com.teamdev.jxbrowser.engine.RenderingMode;
import io.flutter.logging.PluginLogger;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FileUtils;
import io.flutter.utils.JxBrowserUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

  private static String getPluginLoaderDir() {
    try {
      final ApplicationInfo info = ApplicationInfo.getInstance();
      assert info != null;
      if (Objects.equals(info.getMajorVersion(), "2021")) {
        if (Objects.equals(info.getMinorVersion(), "3")) {
          return "flutter-idea";
        }
        else {
          return "flutter-intellij";
        }
      }
      else if (Objects.equals(info.getMajorVersion(), "2020")) {
        return "flutter-intellij";
      }
    }
    catch (NullPointerException ex) {
      // ignored; unit tests
    }
    return "flutter-idea";
  }

  @NotNull
  protected static final String DOWNLOAD_PATH =
    PathManager.getPluginsPath() + File.separatorChar + getPluginLoaderDir() + File.separatorChar + "jxbrowser";
  @NotNull
  private static final AtomicReference<JxBrowserStatus> status = new AtomicReference<>(JxBrowserStatus.NOT_INSTALLED);
  @NotNull
  private static final AtomicBoolean listeningForSetting = new AtomicBoolean(false);
  @NotNull
  private static final Logger LOG = PluginLogger.createLogger(JxBrowserManager.class);
  @NotNull
  public static CompletableFuture<JxBrowserStatus> installation = new CompletableFuture<>();
  @NotNull
  public static final String ANALYTICS_CATEGORY = "jxbrowser";
  private static InstallationFailedReason latestFailureReason;
  private final JxBrowserUtils jxBrowserUtils;
  private final FileUtils fileUtils;

  @VisibleForTesting
  protected JxBrowserManager(@NotNull JxBrowserUtils jxBrowserUtils, @NotNull FileUtils fileUtils) {
    this.jxBrowserUtils = jxBrowserUtils;
    this.fileUtils = fileUtils;
  }

  @NotNull
  public static JxBrowserManager getInstance() {
    if (manager == null) {
      //noinspection ConstantConditions
      manager = new JxBrowserManager(new JxBrowserUtils(), FileUtils.getInstance());
    }
    return manager;
  }

  @VisibleForTesting
  protected static void resetForTest() {
    status.set(JxBrowserStatus.NOT_INSTALLED);
  }

  @NotNull
  public JxBrowserStatus getStatus() {
    //noinspection ConstantConditions
    return status.get();
  }

  @Nullable
  public InstallationFailedReason getLatestFailureReason() {
    return latestFailureReason;
  }

  /**
   * Call {@link #setUp} before this function to ensure that an installation has started.
   */
  @Nullable
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
    setUp(project.getName());
  }

  private class SettingsListener implements FlutterSettings.Listener {

    // Instead of holding onto a project here, only hold onto the Project name.
    // See https://github.com/flutter/flutter-intellij/issues/7377
    @NotNull private String projectName;

    public SettingsListener(@NotNull Project project) {
      this.projectName = project.getName();
    }

    @Override
    public void settingsChanged() {
      final FlutterSettings settings = FlutterSettings.getInstance();

      // Set up JxBrowser files if the embedded inspector option has been turned on and the files aren't already loaded.
      if (getStatus().equals(JxBrowserStatus.NOT_INSTALLED)) {
        setUp(projectName);
      }
    }
  }

  private void setStatusFailed(@NotNull InstallationFailedReason reason) {
    setStatusFailed(reason, null);
  }

  private void setStatusFailed(@NotNull InstallationFailedReason reason, @Nullable Long time) {
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

  public void setUp(@NotNull String projectName) {
    if (jxBrowserUtils.skipInstallation()) {
      status.set(JxBrowserStatus.INSTALLATION_SKIPPED);
      return;
    }

    if (!jxBrowserUtils.skipInstallation() && Objects.equals(status.get(), JxBrowserStatus.INSTALLATION_SKIPPED)) {
      // This check returns status to NOT_INSTALLED so that JxBrowser can be downloaded and installed in cases where it is enabled after being disabled.
      status.compareAndSet(JxBrowserStatus.INSTALLATION_SKIPPED, JxBrowserStatus.NOT_INSTALLED);
    }

    if (!status.compareAndSet(JxBrowserStatus.NOT_INSTALLED, JxBrowserStatus.INSTALLATION_IN_PROGRESS)) {
      // This check ensures that an IDE only downloads and installs JxBrowser once, even if multiple projects are open.
      // If already in progress, let calling point wait until success or failure (it may make sense to call setUp but proceed).
      // If already succeeded or failed, no need to continue.
      return;
    }

    // Retrieve key
    try {
      final String key = jxBrowserUtils.getJxBrowserKey();
      System.setProperty(JxBrowserUtils.LICENSE_PROPERTY_NAME, key);
    }
    catch (FileNotFoundException e) {
      LOG.info(projectName + ": Unable to find JxBrowser license key file", e);
      setStatusFailed(new InstallationFailedReason(FailureType.MISSING_KEY));
      return;
    }

    // If installation future has not finished, we don't want to overwrite it. There could be other code listening for the previous attempt
    // to succeed or fail.
    // We expect to create a new CompletableFuture only if the previous installation attempt failed.
    if (installation.isDone()) {
      installation = new CompletableFuture<>();
    }

    LOG.info(projectName + ": Installing JxBrowser");

    final boolean directoryExists = fileUtils.makeDirectory(DOWNLOAD_PATH);
    if (!directoryExists) {
      LOG.info(projectName + ": Unable to create directory for JxBrowser files");
      setStatusFailed(new InstallationFailedReason(FailureType.DIRECTORY_CREATION_FAILED));
      return;
    }

    final String platformFileName;
    try {
      platformFileName = jxBrowserUtils.getPlatformFileName();
    }
    catch (FileNotFoundException e) {
      LOG.info(projectName + ": Unable to find JxBrowser platform file for " + SystemInfo.getOsNameAndVersion());
      setStatusFailed(new InstallationFailedReason(FailureType.MISSING_PLATFORM_FILES, SystemInfo.getOsNameAndVersion()));
      return;
    }

    // Check whether the files already exist.
    final String[] fileNames = {platformFileName, jxBrowserUtils.getApiFileName(), jxBrowserUtils.getSwingFileName()};
    boolean allDownloaded = true;
    for (String fileName : fileNames) {
      assert fileName != null;
      if (!fileUtils.fileExists(getFilePath(fileName))) {
        allDownloaded = false;
        break;
      }
    }

    if (allDownloaded) {
      LOG.info(projectName + ": JxBrowser platform files already exist, skipping download");
      loadClasses(fileNames);
      return;
    }

    // Delete any already existing files.
    // TODO(helin24): Handle if files cannot be deleted.
    for (String fileName : fileNames) {
      assert fileName != null;
      final String filePath = getFilePath(fileName);
      if (!fileUtils.deleteFile(filePath)) {
        LOG.info(projectName + ": Existing file could not be deleted - " + fileName);
      }
    }
    downloadJxBrowser(fileNames);
  }

  protected void downloadJxBrowser(@NotNull String @NotNull [] fileNames) {
    // The FileDownloader API is used by other plugins - e.g.
    // https://github.com/JetBrains/intellij-community/blob/b09f8151e0d189d70363266c3bb6edb5f6bfeca4/plugins/markdown/src/org/intellij/plugins/markdown/ui/preview/javafx/JavaFXInstallator.java#L48
    final List<FileDownloader> fileDownloaders = new ArrayList<>();
    final DownloadableFileService service = DownloadableFileService.getInstance();
    assert service != null;
    for (String fileName : fileNames) {
      final DownloadableFileDescription
        description = service.createFileDescription(jxBrowserUtils.getDistributionLink(fileName), fileName);
      fileDownloaders.add(service.createDownloader(Collections.singletonList(description), fileName));
    }

    // A project is needed to instantiate the Task, find a non-disposed Project using the ProjectManager
    ProjectManager projectManager = ProjectManager.getInstance();
    Project projectTmp = null;
    if (projectManager != null) {
      projectTmp = projectManager.getDefaultProject();
      if (projectTmp.isDisposed()) {
        Optional<Project> optionalProject = Arrays.stream(projectManager.getOpenProjects()).filter(p -> !p.isDisposed()).findFirst();
        if (optionalProject.isPresent()) {
          projectTmp = optionalProject.get();
        }
      }
    }
    final Project project = projectTmp;
    if (project != null && !project.isDisposed()) {
      //noinspection DialogTitleCapitalization
      final Task.Backgroundable task = new Task.Backgroundable(project, "Downloading JxBrowser") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          if (project.isDisposed()) {
            return;
          }
          String currentFileName = null;
          final long startTime = System.currentTimeMillis();
          try {
            for (int i = 0; i < fileDownloaders.size(); i++) {
              final FileDownloader downloader = fileDownloaders.get(i);
              assert downloader != null;
              currentFileName = fileNames[i];
              final Pair<File, DownloadableFileDescription> download =
                ContainerUtil.getFirstItem(downloader.download(new File(DOWNLOAD_PATH)));
              final File file = download != null ? download.first : null;
              if (file != null) {
                LOG.info(project.getName() + ": JxBrowser file downloaded: " + file.getName());
              }
            }

            loadClasses(fileNames);
          }
          catch (IOException e) {
            final long elapsedTime = System.currentTimeMillis() - startTime;
            LOG.info(project.getName() + ": JxBrowser file downloaded failed: " + currentFileName);
            setStatusFailed(new InstallationFailedReason(FailureType.FILE_DOWNLOAD_FAILED, currentFileName + ":" + e.getMessage()),
                            elapsedTime);
          }
        }
      };
      final BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
      processIndicator.setIndeterminate(false);
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
    }
  }

  private void loadClasses(@NotNull String @NotNull [] fileNames) {
    final List<Path> paths = new ArrayList<>();
    final ClassLoader current = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      try {
        for (String fileName : fileNames) {
          paths.add(Paths.get(getFilePath(fileName)));
        }
        //noinspection ConstantConditions
        fileUtils.loadPaths(this.getClass().getClassLoader(), paths);
      }
      catch (Exception ex) {
        LOG.info("Failed to load JxBrowser paths");
        setStatusFailed(new InstallationFailedReason(FailureType.CLASS_LOAD_FAILED));
        return;
      }
      LOG.info("Loaded JxBrowser files successfully");

      try {
        final Class<?> clazz = Class.forName("com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException");
        final Constructor<?> constructor = clazz.getConstructor(RenderingMode.class);
        constructor.newInstance(RenderingMode.HARDWARE_ACCELERATED);
        //noinspection ThrowableNotThrown
        final UnsupportedRenderingModeException test = new UnsupportedRenderingModeException(RenderingMode.HARDWARE_ACCELERATED);
      }
      catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
             InvocationTargetException e) {
        LOG.info("Failed to find JxBrowser class: ", e);
        setStatusFailed(new InstallationFailedReason(FailureType.CLASS_NOT_FOUND));
        return;
      }
    }
    finally {
      Thread.currentThread().setContextClassLoader(current);
    }
    status.set(JxBrowserStatus.INSTALLED);
    installation.complete(JxBrowserStatus.INSTALLED);
  }

  @NotNull
  private String getFilePath(@NotNull String fileName) {
    return DOWNLOAD_PATH + File.separatorChar + fileName;
  }
}

