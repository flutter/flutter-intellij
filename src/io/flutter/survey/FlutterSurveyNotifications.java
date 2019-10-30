/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.survey;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.DartFileType;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FlutterSurveyNotifications {
  private static final int NOTIFICATION_DELAY_IN_SECS = 3;

  private static final String FLUTTER_LAST_SURVEY_CHECK_KEY = "FLUTTER_LAST_SURVEY_CHECK_KEY";
  private static final long CHECK_INTERVAL_IN_MS = TimeUnit.HOURS.toMillis(40);

  private static final String SURVEY_ACTION_TEXT = "Take survey";
  private static final String SURVEY_TITLE = "Help improve Flutter! Take our Q4 survey.";

  private static final String ANALYTICS_OPT_IN_DETAILS =
    "By clicking on this link you agree to share feature usage along with the survey responses.";

  private static final String SURVEY_TAKEN = "io.flutter.survey.2019.q4.alreadyTaken";
  private static final String SURVEY_URL = "https://google.qualtrics.com/jfe/form/SV_5BhR2R8DZIEE6dn?Source=IntelliJ";

  private static long SURVEY_START_MS_EPOCH;
  private static long SURVEY_END_MS_EPOCH;

  static {
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    dateFormat.setTimeZone(TimeZone.getTimeZone("PST"));
    try {
      SURVEY_START_MS_EPOCH = dateFormat.parse("2019/11/22 9:00").getTime();
      SURVEY_END_MS_EPOCH = dateFormat.parse("2019/12/01 18:00").getTime();
    }
    catch (ParseException e) {
      // Shouldn't happen.
    }
  }

  private static boolean isSurveyOpen() {
    final long now = System.currentTimeMillis();
    return now >= SURVEY_START_MS_EPOCH && now <= SURVEY_END_MS_EPOCH;
  }

  interface FlutterSurveyNotifier {
    void prompt();
  }

  public static void init(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
        check(file);
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        final VirtualFile file = event.getNewFile();
        if (file != null) {
          check(file);
        }
      }

      private void check(@NotNull VirtualFile file) {
        if (PubRoot.isPubspec(file) || file.getFileType() == DartFileType.INSTANCE) {
          new FlutterSurveyNotifications(project).checkForDisplaySurvey();
        }
      }
    });
  }

  @NotNull final Project myProject;

  FlutterSurveyNotifications(@NotNull Project project) {
    this.myProject = project;
  }

  private void checkForDisplaySurvey() {
    // Limit display to the survey window.
    if (!isSurveyOpen()) return;

    final PropertiesComponent properties = PropertiesComponent.getInstance();

    // Don't prompt more often than every 40 hours.
    final long lastCheckedMillis = properties.getOrInitLong(FLUTTER_LAST_SURVEY_CHECK_KEY, 0);
    if (System.currentTimeMillis() - lastCheckedMillis < CHECK_INTERVAL_IN_MS) return;

    properties.setValue(FLUTTER_LAST_SURVEY_CHECK_KEY, String.valueOf(System.currentTimeMillis()));

    // Or, if the survey has already been taken.
    if (properties.getBoolean(SURVEY_TAKEN)) return;

    final boolean reportAnalytics = FlutterInitializer.getCanReportAnalytics();
    final String notificationContents = reportAnalytics ?
                                        ANALYTICS_OPT_IN_DETAILS : null;

    final Notification notification = new Notification(
      FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
      FlutterIcons.Flutter,
      SURVEY_TITLE,
      null,
      notificationContents,
      NotificationType.INFORMATION,
      null
    );

    notification.addAction(new AnAction(SURVEY_ACTION_TEXT) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        properties.setValue(SURVEY_TAKEN, true);
        notification.expire();

        String url = SURVEY_URL;
        // Add a client ID if analytics have been opted into.
        if (reportAnalytics) {
          final String clientId = FlutterInitializer.getAnalytics().getClientId();
          url += ("&ClientID=" + clientId);
        }

        BrowserUtil.browse(url);
      }
    });
    notification.addAction(new AnAction("No thanks") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        properties.setValue(SURVEY_TAKEN, true);
        notification.expire();
      }
    });

    // Display the prompt after a short delay.
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.schedule(() -> {
      if (!myProject.isDisposed()) {
        Notifications.Bus.notify(notification, myProject);
      }
    }, NOTIFICATION_DELAY_IN_SECS, TimeUnit.SECONDS);
    scheduler.shutdown();
  }
}
