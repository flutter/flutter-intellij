/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.OutputListener;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.module.FlutterProjectType;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FlutterSampleManager {
  // TODO(pq): remove after testing.
  // See https://github.com/flutter/flutter-intellij/issues/3330.
  private static final boolean DISABLE_SAMPLES = false;

  private static final String SNIPPETS_REMOTE_INDEX_URL = "https://docs.flutter.io/snippets/index.json";

  private static final Logger LOG = Logger.getInstance(FlutterSampleManager.class);
  @NotNull
  private final FlutterSdk sdk;

  private List<FlutterSample> flutterSamples;

  public FlutterSampleManager(@NotNull FlutterSdk sdk) {
    this.sdk = sdk;
  }

  public List<FlutterSample> getSamples() {
    if (DISABLE_SAMPLES) {
      return Collections.emptyList();
    }

    if (flutterSamples != null) {
      return flutterSamples;
    }

    try {
      final File tempDir = Files.createTempDirectory("flutter-samples-index").toFile();
      tempDir.deleteOnExit();
      final File tempFile = new File(tempDir, "index.json");

      try {
        final GeneralCommandLine commandLine = sdk.flutterListSamples(tempFile).createGeneralCommandLine(null);
        LOG.info(commandLine.toString());
        final OSProcessHandler process = new OSProcessHandler(commandLine);
        process.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            try {
              final byte[] bytes = Files.readAllBytes(tempFile.toPath());
              final String content = new String(bytes);
              flutterSamples = FlutterSampleManager.readSamples(content);
            }
            catch (IOException e) {
              LOG.warn(e);
              // On IOException, short circuit checking again.
              flutterSamples = Collections.emptyList();
            }
          }
        });

        process.startNotify();

        final int timeoutInMs = 2000;
        // TODO(pq): is this wait right?
        if (process.waitFor(timeoutInMs)) {
          LOG.info("Flutter sample listing took more than " + timeoutInMs + "ms");
          // Don't cache here so that we can try again later.
        }
      }
      catch (ExecutionException e) {
        LOG.warn(e);
        // On ExecutionException, short circuit checking again.
        flutterSamples = Collections.emptyList();
      }
    }
    catch (IOException e) {
      LOG.warn(e);
      // On IOException, short circuit checking again.
      flutterSamples = Collections.emptyList();
    }

    return flutterSamples != null ? flutterSamples : Collections.emptyList();
  }

  // Called at project initialization.
  public static void initialize(Project project) {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk != null) {
      // Trigger a sample listing.
      sdk.getSamples();
    }
  }

  public static List<FlutterSample> readSamples(String indexFileContents) {
    final List<FlutterSample> samples = new ArrayList<>();
    final JsonArray json = new JsonParser().parse(indexFileContents).getAsJsonArray();
    for (JsonElement element : json) {
      final JsonObject sample = element.getAsJsonObject();
      samples.add(new FlutterSample(sample.getAsJsonPrimitive("element").getAsString(),
                                    sample.getAsJsonPrimitive("library").getAsString(),
                                    sample.getAsJsonPrimitive("id").getAsString(),
                                    sample.getAsJsonPrimitive("file").getAsString(),
                                    sample.getAsJsonPrimitive("sourcePath").getAsString(),
                                    sample.getAsJsonPrimitive("description").getAsString()));
    }

    // Sort by display label.
    samples.sort(Comparator.comparing(FlutterSample::getDisplayLabel));
    return samples;
  }

  private static JsonArray readSampleIndex(final URL sampleUrl) throws IOException {
    final BufferedInputStream in = new BufferedInputStream(sampleUrl.openStream());
    final StringBuilder contents = new StringBuilder();
    final byte[] bytes = new byte[1024];
    int bytesRead;
    while ((bytesRead = in.read(bytes)) != -1) {
      contents.append(new String(bytes, 0, bytesRead));
    }

    return new JsonParser().parse(contents.toString()).getAsJsonArray();
  }

  private static JsonArray readSampleIndex() {
    // Try fetching snippets index remotely, and fall back to local cache.
    try {
      return readSampleIndex(new URL(SNIPPETS_REMOTE_INDEX_URL));
    }
    catch (IOException ignored) {
      try {
        return readSampleIndex(FlutterSampleManager.class.getResource("index.json"));
      }
      catch (IOException e) {
        FlutterUtils.warn(LOG, e);
      }
    }
    return new JsonArray();
  }

  public static String createSampleProject(@NotNull FlutterSample sample, @NotNull Project project) {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return "unable to find Flutter SDK";
    }

    final File projectRoot = new File(ProjectUtil.getBaseDir());
    final String projectNamePrefix = deriveValidPackageName(sample.getElement());
    final String projectDir = FileUtil.createSequentFileName(projectRoot, projectNamePrefix + "_sample", "");

    final File dir = new File(projectRoot, projectDir);
    if (!dir.mkdir()) {
      return "unable to create project root:  " + dir.getPath();
    }

    final VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    if (baseDir == null) {
      return "unable to find project root (" + dir.getPath() + ") on refresh";
    }

    final OutputListener outputListener = new OutputListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        // TODO(pq): consider showing progress in the status line.
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        // TODO(pq): handle event.getExitCode().
      }
    };


    final PubRoot root =
      FlutterModuleBuilder.runFlutterCreateWithProgress(baseDir, sdk, project, outputListener, getCreateSettings(sample));
    final ProjectOpenProcessor openProcessor = ProjectOpenProcessor.getImportProvider(baseDir);
    if (openProcessor == null) {
      return "unable to find a processor to finish opening the project: " + baseDir.getPath();
    }

    openProcessor.doOpenProject(baseDir, null, true);
    return null;
  }

  private static String deriveValidPackageName(String name) {
    return name.split("\\.")[0].toLowerCase();
  }

  private static FlutterCreateAdditionalSettings getCreateSettings(@NotNull FlutterSample sample) {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(sample.getElement() + " Sample Project")
      .setType(FlutterProjectType.APP)
      .setKotlin(false)
      .setOrg(FlutterBundle.message("flutter.module.create.settings.org.default_text"))
      .setSwift(false)
      .setSampleContent(sample)
      .setOffline(false)
      .build();
  }
}
