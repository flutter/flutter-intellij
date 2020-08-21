/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.lang.UrlClassLoader;
import io.flutter.utils.JxBrowserUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

// JxBrowser provides Chromium to display web pages within IntelliJ. This class manages downloading the required files and adding them to
// the class path.
public class JxBrowserManager {
  private static JxBrowserManager manager;
  private static final String DOWNLOAD_PATH = PathManager.getPluginsPath() + File.separatorChar + "flutter-intellij" + File.separatorChar + "jxbrowser";
  private static final AtomicReference<JxBrowserStatus> status = new AtomicReference<>(JxBrowserStatus.NOT_INSTALLED);
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);
  // We will be gating JxBrowser features until all of the features are landed.
  // To test JxBrowser, set this to true and also add license key to VM options (-Djxbrowser.license.key=<key>).
  public static final boolean ENABLE_JX_BROWSER = false;
  private static CompletableFuture<JxBrowserStatus> installation = new CompletableFuture<>();

  private JxBrowserManager() {}

  public static JxBrowserManager getInstance() {
    if (manager == null) {
      return new JxBrowserManager();
    }
    return manager;
  }

  public JxBrowserStatus getStatus() {
    return status.get();
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

  public void retryFromFailed(Project project) {
    if (!status.compareAndSet(JxBrowserStatus.INSTALLATION_FAILED, JxBrowserStatus.NOT_INSTALLED)) {
      return;
    }
    LOG.info(project.getName() + ": Retrying JxBrowser installation");
    setUp(project);
  }

  private void setStatusFailed() {
    status.set(JxBrowserStatus.INSTALLATION_FAILED);
    installation.complete(JxBrowserStatus.INSTALLATION_FAILED);
  }

  public void setUp(Project project) {
    if (!status.compareAndSet(JxBrowserStatus.NOT_INSTALLED, JxBrowserStatus.INSTALLATION_IN_PROGRESS)) {
      // This check ensures that an IDE only downloads and installs JxBrowser once, even if multiple projects are open.
      // If already in progress, let calling point wait until success or failure (it may make sense to call setUp but proceed).
      // If already succeeded or failed, no need to continue.
      return;
    }

    // If installation future has not finished, we don't want to overwrite it. There could be other code listening for the previous attempt
    // to succeed or fail.
    // We expect to create a new CompletableFuture only if the previous installation attempt failed.
    if (installation.isDone()) {
      installation = new CompletableFuture<>();
    }

    LOG.info(project.getName() + ": Installing JxBrowser");

    final File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      if (!directory.mkdirs()) {
        LOG.info(project.getName() + ": Unable to create directory for JxBrowser files");
        setStatusFailed();
        return;
      }
    }

    final String platformFileName;
    try {
      platformFileName = JxBrowserUtils.getPlatformFileName();
    }
    catch (FileNotFoundException e) {
      LOG.info(project.getName() + ": Unable to find JxBrowser platform file");
      e.printStackTrace();
      setStatusFailed();
      return;
    }

    // Check whether the files already exist.
    final String[] fileNames = {platformFileName, JxBrowserUtils.getApiFileName(), JxBrowserUtils.getSwingFileName()};
    boolean allDownloaded = true;
    final List<File> files = new ArrayList<>();
    for (String fileName : fileNames) {
      final File file = new File(DOWNLOAD_PATH + File.separator + fileName);
      files.add(file);
      if (!file.exists()) {
        allDownloaded = false;
      }
    }

    if (allDownloaded) {
      LOG.info(project.getName() + ": JxBrowser platform files already exist, skipping download");
      loadClasses(files);
      return;
    }

    // Delete any already existing files.
    // TODO(helin24): Handle if files cannot be deleted.
    for (File file : files) {
      if (file.exists()) {
        if (!file.delete()) {
          LOG.info(project.getName() + ": Existing file could not be deleted - " + file.getAbsolutePath());
        }
      }
    }

    downloadJxBrowser(project, fileNames);
  }

  private void downloadJxBrowser(Project project, String[] fileNames) {
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
        try {
          final List<File> files = new ArrayList<>();
          for (int i = 0; i < fileDownloaders.size(); i++) {
            final FileDownloader downloader = fileDownloaders.get(i);
            currentFileName = fileNames[i];
            final Pair<File, DownloadableFileDescription> download =
              ContainerUtil.getFirstItem(downloader.download(new File(DOWNLOAD_PATH)));
            final File file = download != null ? download.first : null;
            if (file != null) {
              files.add(file);
              LOG.info(project.getName() + ": JxBrowser file downloaded: " + file.getAbsolutePath());
            }
          }

          loadClasses(files);
        }
        catch (IOException e) {
          LOG.info(project.getName() + ": JxBrowser file downloaded failed: " + currentFileName);
          e.printStackTrace();
          setStatusFailed();
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private void loadClasses(List<File> files) {
    final UrlClassLoader classLoader = (UrlClassLoader) this.getClass().getClassLoader();
    try {
      for (File file : files) {
        final URL url = file.toURI().toURL();
        classLoader.addURL(url);
        LOG.info("Loaded JxBrowser file successfully: " + url.toString());
      }

      status.set(JxBrowserStatus.INSTALLED);
      installation.complete(JxBrowserStatus.INSTALLED);
    }
    catch (MalformedURLException e) {
      LOG.info("Failed to load JxBrowser files");
      setStatusFailed();
    }
  }
}
