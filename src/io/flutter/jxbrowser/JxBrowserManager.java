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
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import io.flutter.utils.FileUtils;
import io.flutter.utils.JxBrowserUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

enum JxBrowserStatus {
  NOT_INSTALLED,
  INSTALLATION_IN_PROGRESS,
  INSTALLED,
  INSTALLATION_FAILED,
}


public class JxBrowserManager {
  private static JxBrowserManager manager;
  private static final String DOWNLOAD_PATH = FileUtils.platformPath();
  private static final AtomicReference<JxBrowserStatus> status = new AtomicReference<>(JxBrowserStatus.NOT_INSTALLED);
  private static final Logger LOG = Logger.getInstance(JxBrowserManager.class);

  private JxBrowserManager() {}

  public static JxBrowserManager get() {
    if (manager == null) {
      return new JxBrowserManager();
    }
    return manager;
  }

  public void setUp(Project project) {
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
    final String jxbrowserFileName = JxBrowserUtils.getPlatformFileName();
    final File jxbrowserPlatformFile = new File(DOWNLOAD_PATH + File.separator + jxbrowserFileName);
    if (jxbrowserPlatformFile.exists()) {
      LOG.info(project.getName() + ": JxBrowser platform file already exists, skipping download");
      loadClasses(jxbrowserPlatformFile);
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
          loadClasses(jxbrowserPlatformFile);
          return;
        }
      }
      // If jxbrowser was not downloaded within 100 sec.
      LOG.info(project.getName() + ": JxBrowser downloaded timed out");
      status.set(JxBrowserStatus.INSTALLATION_FAILED);
    }
    downloadJxBrowser(project, jxbrowserFileName, tempLoadingFile);
  }

  private void downloadJxBrowser(Project project, String jxbrowserFileName, File tempLoadingFile) {
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription
      description = service.createFileDescription(JxBrowserUtils.getDistributionLink(jxbrowserFileName), jxbrowserFileName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), jxbrowserFileName);

    Task.Backgroundable task = new Task.Backgroundable(project, "Downloading jxbrowser") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(new File(DOWNLOAD_PATH));
          Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
          File file = first != null ? first.first : null;
          if (file != null) {
            LOG.info(project.getName() + ": JxBrowser file downloaded: " + file.getAbsolutePath());
          }
          tempLoadingFile.delete();
          loadClasses(file);
        }
        catch (IOException e) {
          LOG.info(project.getName() + ": JxBrowser file downloaded failed: " + jxbrowserFileName);
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

  private void loadClasses(File file) {
    final URLClassLoader classLoader = (URLClassLoader)ClassLoader.getSystemClassLoader();
    try {
      final URL url = file.toURI().toURL();
      final Method method = ReflectionUtil.getDeclaredMethod(URLClassLoader.class, "addURL", URL.class);
      method.invoke(classLoader, url);
      LOG.info("Loaded JxBrowser file successfully: " + url.toString());
      status.set(JxBrowserStatus.INSTALLED);
    }
    catch (Exception e) {
      LOG.info("Failed to load JxBrowser file: " + file.getAbsolutePath());
      e.printStackTrace();
      status.set(JxBrowserStatus.INSTALLATION_FAILED);
    }
  }

}
