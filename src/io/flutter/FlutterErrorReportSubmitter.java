/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter;

import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class FlutterErrorReportSubmitter extends ErrorReportSubmitter {
  private static final Logger LOG = Logger.getInstance(FlutterErrorReportSubmitter.class);

  @NotNull
  @Override
  public String getReportActionText() {
    return "Create Flutter Bug Report";
  }

  @SuppressWarnings("deprecation")
  @Override
  public void submitAsync(@NotNull IdeaLoggingEvent[] events,
                          @Nullable String additionalInfo,
                          @NotNull Component parentComponent,
                          @NotNull Consumer<SubmittedReportInfo> consumer) {
    if (events.length == 0) {
      consumer.consume(new SubmittedReportInfo(
        null,
        null,
        SubmittedReportInfo.SubmissionStatus.FAILED));
      return;
    }

    final DataContext dataContext = DataManager.getInstance().getDataContext(parentComponent);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    final StringBuilder builder = new StringBuilder();

    builder.append("Please file this bug report at https://github.com/flutter/flutter-intellij/issues/new.\n");
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
    final IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId("io.flutter"));
    //noinspection ConstantConditions
    builder.append("Flutter plugin `").append(descriptor.getVersion()).append("`\n");
    builder.append("\n");
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

        FlutterInitializer.getAnalytics().sendException(event.getThrowable(), false);
      }
    }

    final String text = builder.toString().trim() + "\n";

    // Create scratch file.
    final ScratchRootType scratchRoot = ScratchRootType.getInstance();
    final VirtualFile file = scratchRoot.createScratchFile(
      project, "bug-report.md", Language.ANY, text);

    if (file != null) {
      // Open the file.
      new OpenFileDescriptor(project, file).navigate(true);
    }
    else {
      consumer.consume(new SubmittedReportInfo(
        null,
        null,
        SubmittedReportInfo.SubmissionStatus.FAILED));
    }

    consumer.consume(new SubmittedReportInfo(
      null,
      "",
      SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
  }

  @SuppressWarnings("deprecation")
  @Override
  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent) {
    // obsolete API
    return new SubmittedReportInfo(null, "0", SubmittedReportInfo.SubmissionStatus.FAILED);
  }

  private String getFlutterVersion(final FlutterSdk sdk) {
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

  private static byte[] readFully(InputStream in) throws IOException {
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
