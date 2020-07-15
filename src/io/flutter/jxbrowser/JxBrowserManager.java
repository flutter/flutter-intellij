/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

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
import io.flutter.utils.FileUtils;
import io.flutter.utils.JxBrowserUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

enum JxBrowserStatus {
  NOT_INSTALLED,
  INSTALLATION_IN_PROGRESS,
  INSTALLED,
  INSTALLATION_FAILED,
}

// JxBrowser provides Chromium to display web pages within IntelliJ. This class manages downloading the required files and adding them to
// the class path.
public class JxBrowserManager {
  private static JxBrowserManager manager;
  private static final String DOWNLOAD_PATH = FileUtils.platformPath();
  private static final AtomicReference<JxBrowserStatus> status = new AtomicReference<>(JxBrowserStatus.NOT_INSTALLED);
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);
  // We will be gating JxBrowser features until all of the features are landed.
  private static final boolean ENABLE_JXBROWSER = false;

  private JxBrowserManager() {}

  public static JxBrowserManager get() {
    if (manager == null) {
      return new JxBrowserManager();
    }
    return manager;
  }

  public boolean isInstalled() {
    return status.get().equals(JxBrowserStatus.INSTALLED);
  }

  public void setUp(Project project) {
    if (!ENABLE_JXBROWSER) {
      return;
    }

    if (!status.compareAndSet(JxBrowserStatus.NOT_INSTALLED, JxBrowserStatus.INSTALLATION_IN_PROGRESS)) {
      // This check ensures that an IDE only downloads and installs JxBrowser once, even if multiple projects are open.
      // If already in progress, let calling point wait until success or failure (it may make sense to call setUp but proceed).
      // If already succeeded or failed, no need to continue.
      return;
    }

    LOG.info(project.getName() + ": Installing JxBrowser");

    final File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    // Check for file or loading file
    final String[] fileNames = {JxBrowserUtils.getPlatformFileName(), JxBrowserUtils.getApiFileName(), JxBrowserUtils.getSwingFileName()};
    boolean allDownloaded = true;
    final List<File> files = new ArrayList<>();
    for (String fileName : fileNames) {
      final File file = new File(DOWNLOAD_PATH + File.separator + fileName);
      files.add(file);
      if (!file.exists()) {
        allDownloaded = false;
        break;
      }
    }

    if (allDownloaded) {
      LOG.info(project.getName() + ": JxBrowser platform file already exists, skipping download");
      loadClasses(files);
      return;
    }

    final File tempLoadingFile = new File(DOWNLOAD_PATH + File.separator + JxBrowserUtils.getLoadingFileName());
    boolean created = false;
    try {
      created = tempLoadingFile.createNewFile();
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    if (!created) {
      // This means another IDE already created this file and has started downloading.
      // Wait for download to finish and then try loading again.
      LOG.info(project.getName() + ": Waiting for JxBrowser file to download");
      int attempts = 0;
      while (attempts < 100) {
        if (tempLoadingFile.exists()) {
          try {
            Thread.sleep(1000);
          }
          catch (InterruptedException e) {
            e.printStackTrace();
          }
          attempts += 1;
        }
        else {
          LOG.info(project.getName() + ": JxBrowser file downloaded, attempting to load");
          loadClasses(files);
          return;
        }
      }
      // If jxbrowser was not downloaded within 100 sec.
      LOG.info(project.getName() + ": JxBrowser downloaded timed out");
      status.set(JxBrowserStatus.INSTALLATION_FAILED);
    }

    // Delete any already existing files.
    // TODO: Handle if files cannot be deleted.
    for (File file : files) {
      if (file.exists()) {
        if (!file.delete()) {
          LOG.info(project.getName() + ": Existing file could not be deleted - " + file.getAbsolutePath());
        }
      }
    }

    downloadJxBrowser(project, fileNames, tempLoadingFile);
  }

  private void downloadJxBrowser(Project project, String[] fileNames, File tempLoadingFile) {
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

          tempLoadingFile.delete();
          loadClasses(files);
        }
        catch (IOException e) {
          LOG.info(project.getName() + ": JxBrowser file downloaded failed: " + currentFileName);
          e.printStackTrace();
          status.set(JxBrowserStatus.INSTALLATION_FAILED);
          tempLoadingFile.delete();
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
    }
    catch (Exception e) {
      LOG.info("Failed to load JxBrowser files");
      e.printStackTrace();
      status.set(JxBrowserStatus.INSTALLATION_FAILED);
    }
  }
}
