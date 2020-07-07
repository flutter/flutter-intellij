/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import icons.FlutterIcons;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.FileUtils;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.JxBrowserUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;

/**
 * Runs startup actions just after a project is opened, before it's indexed.
 *
 * @see FlutterInitializer for actions that run later.
 */
public class ProjectOpenActivity implements StartupActivity, DumbAware {
  public static final ProjectType FLUTTER_PROJECT_TYPE = new ProjectType("io.flutter");
  private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class);
  private static final String DOWNLOAD_PATH = FileUtils.platformPath();

  public ProjectOpenActivity() {
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
    DownloadableFileDescription description = service.createFileDescription(JxBrowserUtils.getDistributionLink(jxbrowserFileName), jxbrowserFileName);
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
      final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      method.setAccessible(true);
      method.invoke(classLoader, url);
      System.out.println("Loaded classes from url: " + url.toString());
    }
    catch (Exception e) {
      System.out.println("Unable to load URL: " + file.getAbsolutePath());
      e.printStackTrace();
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    verifyJxBrowser(project);

    // TODO(messick): Remove 'FlutterUtils.isAndroidStudio()' after Android Q sources are published.
    if (FlutterUtils.isAndroidStudio() && AndroidUtils.isAndroidProject(project)) {
      AndroidUtils.addGradleListeners(project);
    }
    if (!FlutterModuleUtils.declaresFlutter(project)) {
      return;
    }

    final FlutterSdk sdk = FlutterSdk.getIncomplete(project);
    if (sdk == null) {
      // We can't do anything without a Flutter SDK.
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      sdk.queryFlutterConfig("android-studio-dir", false);
    });
    if (FlutterUtils.isAndroidStudio() && !FLUTTER_PROJECT_TYPE.equals(ProjectTypeService.getProjectType(project))) {
      if (!AndroidUtils.isAndroidProject(project)) {
        ProjectTypeService.setProjectType(project, FLUTTER_PROJECT_TYPE);
      }
    }

    // If this project is intended as a bazel project, don't run the pub alerts.
    if (WorkspaceCache.getInstance(project).isBazel()) {
      return;
    }

    for (PubRoot pubRoot : PubRoots.forProject(project)) {
      if (!pubRoot.hasUpToDatePackages()) {
        Notifications.Bus.notify(new PackagesOutOfDateNotification(project, pubRoot));
      }
    }
  }

  private static class PackagesOutOfDateNotification extends Notification {
    @NotNull private final Project myProject;
    @NotNull private final PubRoot myRoot;

    public PackagesOutOfDateNotification(@NotNull Project project, @NotNull PubRoot root) {
      super("Flutter Packages", FlutterIcons.Flutter, "Flutter pub get.",
            null, "The pubspec.yaml file has been modified since " +
                  "the last time 'flutter pub get' was run.",
            NotificationType.INFORMATION, null);

      myProject = project;
      myRoot = root;

      addAction(new AnAction("Run 'flutter pub get'") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          expire();

          final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
          if (sdk == null) {
            Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
            return;
          }

          if (sdk.startPubGet(root, project) == null) {
            Messages.showErrorDialog("Unable to run 'flutter pub get'", "Error");
          }
        }
      });
    }
  }
}
