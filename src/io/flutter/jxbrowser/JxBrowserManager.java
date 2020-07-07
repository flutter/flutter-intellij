/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

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
  DOWNLOADING,
  INSTALLED,
  INSTALLATION_FAILED,
}


public class JxBrowserManager {
  private static JxBrowserManager manager;
  private static final String DOWNLOAD_PATH = FileUtils.platformPath();

  private AtomicReference<JxBrowserStatus> status;

  private JxBrowserManager() {
    this.status = new AtomicReference<>(JxBrowserStatus.NOT_INSTALLED);
  }

  public static JxBrowserManager get() {
    if (manager == null) {
      return new JxBrowserManager();
    }
    return manager;
  }

  public void setUp(Project project) {
    verifyJxBrowser(project);
  }

  private void verifyJxBrowser(Project project) {
    final File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    String jxbrowserFileName = JxBrowserUtils.getPlatformFileName();
    final File jxbrowserPlatformFile = new File(DOWNLOAD_PATH + File.separator + jxbrowserFileName);
    if (jxbrowserPlatformFile.exists()) {
      // Skip downloading
      System.out.println("JxBrowser platform file already exists");
      loadClasses(jxbrowserPlatformFile);
      return;
    }

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
            System.out.println("File downloaded: " + file.getAbsolutePath());
          }
          loadClasses(file);
        }
        catch (IOException e) {
          System.out.println("Unable to download file");
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private void loadClasses(File file) {
    System.out.println("Loading classes from file: " + file.getAbsolutePath());
    final URLClassLoader classLoader = (URLClassLoader)ClassLoader.getSystemClassLoader();
    try {
      final URL url = file.toURI().toURL();
      final Method method = ReflectionUtil.getDeclaredMethod(URLClassLoader.class, "addURL", URL.class);
      method.invoke(classLoader, url);
      System.out.println("Loaded classes from url: " + url.toString());
    }
    catch (Exception e) {
      System.out.println("Unable to load URL: " + file.getAbsolutePath());
      e.printStackTrace();
    }
  }

}
