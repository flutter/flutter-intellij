/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static io.flutter.run.daemon.DaemonApi.COMPLETION_EXCEPTION_PREFIX;

// Not sure how others debug this but here's one technique.
// Edit com.intellij.ide.plugins.PluginManagerCore.
// Add this line at the top of getPluginByClassName():
// if (true) return getPlugins()[getPlugins().length-1].getPluginId(); // DEBUG do not merge
public class FlutterErrorReportSubmitter extends ErrorReportSubmitter {
  private static final Logger LOG = Logger.getInstance(FlutterErrorReportSubmitter.class);
  private static final String[] KNOWN_ERRORS = new String[]{"Bad state: No element"};

  @NotNull
  @Override
  public String getReportActionText() {
    return "Create Flutter Bug Report";
  }

  //@Override
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
                        @Nullable String additionalInfo,
                        @NotNull Component parentComponent,
                        @NotNull Consumer<? super SubmittedReportInfo> consumer) {
    if (events.length == 0) {
      // Don't remove the cast until a later version of Android Studio.
      fail(((Consumer<SubmittedReportInfo>)consumer));
      return false;
    }

    String stackTrace = null;
    String errorMessage = null;

    for (IdeaLoggingEvent event : events) {
      String stackTraceText = event.getThrowableText();
      if (stackTraceText.startsWith(COMPLETION_EXCEPTION_PREFIX)) {
        stackTraceText = stackTraceText.substring(COMPLETION_EXCEPTION_PREFIX.length());
        if (stackTraceText.startsWith(DaemonApi.FLUTTER_ERROR_PREFIX)) {
          final String message = stackTraceText.substring(DaemonApi.FLUTTER_ERROR_PREFIX.length());
          final int start = message.indexOf(": ") + 2;
          if (start == 0) continue;
          int end = message.indexOf('\n');
          if (end < 0) end = message.length();
          final String error = message.substring(start, end);
          stackTrace = message.substring(end + 1);
          for (String err : KNOWN_ERRORS) {
            if (error.contains(err)) {
              if (end != message.length()) {
                // Dart stack trace included so extract it and set the issue target to the Flutter repo.
                errorMessage = err;
                final int endOfDartStack = stackTrace.indexOf("\\n\"\n");
                if (endOfDartStack > 0) {
                  // Get only the part between quotes. If the format is wrong just use the whole thing.
                  stackTrace = stackTrace.substring(1, endOfDartStack);
                }
                break;
              }
            }
          }
        }
      }
    }

    final DataContext dataContext = DataManager.getInstance().getDataContext(parentComponent);
    final Project project = PROJECT.getData(dataContext);
    if (project == null) {
      // Don't remove the cast until a later version of Android Studio.
      fail(((Consumer<SubmittedReportInfo>)consumer));
      return false;
    }

    final StringBuilder builder = new StringBuilder();

    builder.append("Please file this bug report at ");
    builder.append("https://github.com/flutter/flutter-intellij/issues/new");
    builder.append(".\n");
    builder.append("\n");
    builder.append("---\n");
    builder.append("\n");

    builder.append("## What happened\n");
    builder.append("\n");
    if (additionalInfo != null) {
      builder.append(additionalInfo.trim()).append("\n");
    }
    else {
      builder.append("(please describe what you were doing when this exception occurred)\n");
    }
    builder.append("\n");

    builder.append("## Version information\n");
    builder.append("\n");

    // IntelliJ version
    final ApplicationInfo info = ApplicationInfo.getInstance();
    builder.append(info.getVersionName()).append(" `").append(info.getFullVersion()).append("`");

    final PluginId pid = FlutterUtils.getPluginId();
    final IdeaPluginDescriptor flutterPlugin = PluginManagerCore.getPlugin(pid);
    //noinspection ConstantConditions
    builder.append(" • Flutter plugin `").append(pid.getIdString()).append(' ').append(flutterPlugin.getVersion()).append("`");

    final IdeaPluginDescriptor dartPlugin = PluginManagerCore.getPlugin(PluginId.getId("Dart"));
    if (dartPlugin != null) {
      builder.append(" • Dart plugin `").append(dartPlugin.getVersion()).append("`");
    }
    builder.append("\n\n");

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      builder.append("No Flutter sdk configured.\n");
    }
    else {
      final String flutterVersion = getFlutterVersion(sdk);
      if (flutterVersion != null) {
        builder.append(flutterVersion.trim()).append("\n");
      }
      else {
        builder.append("Error getting Flutter sdk information.\n");
      }
    }
    builder.append("\n");

    if (stackTrace == null) {
      for (IdeaLoggingEvent event : events) {
        builder.append("## Exception\n");
        builder.append("\n");
        builder.append(event.getMessage()).append("\n");
        builder.append("\n");

        if (event.getThrowable() != null) {
          builder.append("```\n");
          builder.append(event.getThrowableText().trim()).append("\n");
          builder.append("```\n");
          builder.append("\n");
        }
      }
    }
    else {
      builder.append("## Exception\n");
      builder.append("\n");
      builder.append(errorMessage).append("\n");
      builder.append("\n");
      builder.append("```\n");
      builder.append(stackTrace.replaceAll("\\\\n", "\n")).append("\n");
      builder.append("```\n");
      builder.append("\n");
    }

    for (IdeaLoggingEvent event : events) {
      FlutterInitializer.getAnalytics().sendException(event.getThrowableText(), false);
    }

    final String text = builder.toString().trim() + "\n";

    // Create scratch file.
    final ScratchRootType scratchRoot = ScratchRootType.getInstance();
    final VirtualFile file = scratchRoot.createScratchFile(project, "bug-report.md", PlainTextLanguage.INSTANCE, text);

    if (file == null) {
      // Don't remove the cast until a later version of Android Studio.
      fail(((Consumer<SubmittedReportInfo>)consumer));
      return false;
    }

    // Open the file.
    new OpenFileDescriptor(project, file).navigate(true);

    consumer.consume(new SubmittedReportInfo(
      null,
      "",
      SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));

    return true;
  }

  private static String getFlutterVersion(final FlutterSdk sdk) {
    try {
      final String flutterPath = sdk.getHomePath() + "/bin/flutter";
      final ProcessBuilder builder = new ProcessBuilder(flutterPath, "--version");
      final Process process = builder.start();
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        return null;
      }
      return new String(readFully(process.getInputStream()), StandardCharsets.UTF_8);
    }
    catch (IOException | InterruptedException ioe) {
      return null;
    }
  }

  private static void fail(@NotNull Consumer<SubmittedReportInfo> consumer) {
    consumer.consume(new SubmittedReportInfo(
      null,
      null,
      SubmittedReportInfo.SubmissionStatus.FAILED));
  }

  private static byte[] readFully(InputStream in) throws IOException {
    //noinspection resource
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final byte[] temp = new byte[4096];
    int count = in.read(temp);
    while (count > 0) {
      out.write(temp, 0, count);
      count = in.read(temp);
    }
    return out.toByteArray();
  }
}
