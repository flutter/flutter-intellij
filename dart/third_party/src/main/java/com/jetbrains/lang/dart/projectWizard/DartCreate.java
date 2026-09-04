// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.projectWizard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles running `dart create` to generate new Dart projects from templates.
 */
public class DartCreate {

  /**
   * Describes a `dart create` template.
   */
  public static class DartCreateTemplate {
    public final @NotNull @NonNls String myId;
    public final @NotNull @NlsSafe String myLabel;
    public final @NotNull @NlsSafe String myDescription;
    public final @Nullable @NonNls String myEntrypoint;

    public DartCreateTemplate(@NotNull @NonNls String id,
                              @NotNull @NlsSafe String label,
                              @NotNull @NlsSafe String description,
                              @Nullable @NonNls String entrypoint) {
      myId = id;
      myLabel = label;
      myDescription = description;
      myEntrypoint = entrypoint;
    }

    @Override
    public String toString() {
      return StringUtil.join("[", myId, ",", myLabel, ",", myDescription, ",", myEntrypoint, "]");
    }
  }

  private static final Logger LOG = PluginLogger.INSTANCE.createLogger(DartCreate.class);
  private static final List<DartCreateTemplate> EMPTY = new ArrayList<>();

  private static ProcessOutput runDartCreate(@NotNull String sdkRoot,
                                             @Nullable String workingDirectory,
                                             int timeoutInSeconds,
                                             String... parameters) throws ExecutionException {
    final GeneralCommandLine command = new GeneralCommandLine()
      .withExePath(DartSdkUtil.getDartExePath(sdkRoot))
      .withWorkDirectory(workingDirectory);

    command.addParameter("create");
    command.addParameters(parameters);

    Analytics.updateEnvironment(command);

    return new CapturingProcessHandler(command).runProcess(timeoutInSeconds * 1000, false);
  }

  public void generateInto(final @NotNull String sdkRoot,
                           final @NotNull VirtualFile projectDirectory,
                           final @NotNull String templateId) throws ExecutionException {
    ProcessOutput output = runDartCreate(sdkRoot, projectDirectory.getParent().getPath(), 30, "--force", "--no-pub", "--template",
                                           templateId, projectDirectory.getName());

    if (output.getExitCode() != 0) {
      throw new ExecutionException(output.getStderr());
    }
  }

  public List<DartCreateTemplate> getAvailableTemplates(final @NotNull String sdkRoot) {
    try {
      ProcessOutput output = runDartCreate(sdkRoot, null, 10, "--list-templates");

      int exitCode = output.getExitCode();

      if (exitCode != 0) {
        return EMPTY;
      }

      // [{"name":"consoleapp", "label":"Console App", "description":"A minimal command-line application."}, {"name": ..., }]
      final JsonArray templatesArray = JsonParser.parseString(output.getStdout()).getAsJsonArray();
      final List<DartCreateTemplate> result = new ArrayList<>();
      for (final JsonElement templateElement : templatesArray) {
        final JsonObject templateObject = templateElement.getAsJsonObject();

        result.add(new DartCreateTemplate(
            templateObject.get("name").getAsString(),
            templateObject.get("label").getAsString(),
            templateObject.get("description").getAsString(),
            templateObject.has("entrypoint") ?
                templateObject.get("entrypoint").getAsString() : null));
      }

      return result;
    }
    catch (Exception e) {
      LOG.info(e);
    }

    return EMPTY;
  }
}
