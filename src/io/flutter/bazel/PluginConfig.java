/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.PatternSyntaxException;

/**
 * An in-memory snapshot of the flutter.json file from a Bazel workspace.
 */
class PluginConfig {
  private final @NotNull Fields fields;

  private PluginConfig(@NotNull Fields fields) {
    this.fields = fields;
  }

  @Nullable
  String getDaemonScript() {
    return fields.daemonScript;
  }

  @Nullable
  String getDoctorScript() {
    return fields.doctorScript;
  }

  @Nullable
  String getLaunchScript() {
    return fields.launchScript;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof PluginConfig)) return false;
    final PluginConfig other = (PluginConfig)obj;
    return Objects.equal(fields, other.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(fields);
  }

  /**
   * Reads plugin configuration from a file, if possible.
   */
  public static @Nullable
  PluginConfig load(@NotNull VirtualFile file) {
    final Computable<PluginConfig> readAction = () -> {
      try {
        final InputStreamReader input = new InputStreamReader(file.getInputStream(), "UTF-8");
        final Fields fields = GSON.fromJson(input, Fields.class);
        return new PluginConfig(fields);
      }
      catch (FileNotFoundException e) {
        LOG.info("Flutter plugin didn't find flutter.json at " + file.getPath());
        return null;
      }
      catch (IOException e) {
        LOG.warn("Flutter plugin failed to load config file at " + file.getPath(), e);
        return null;
      }
      catch (JsonSyntaxException e) {
        LOG.warn("Flutter plugin failed to parse JSON in config file at " + file.getPath());
        return null;
      }
      catch (PatternSyntaxException e) {
        LOG.warn("Flutter plugin failed to parse directory pattern (" + e.getPattern() + ") in config file at " + file.getPath());
        return null;
      }
    };

    return ApplicationManager.getApplication().runReadAction(readAction);
  }

  /**
   * The JSON fields in a PluginConfig, as loaded from disk.
   */
  @SuppressWarnings("unused")
  private static class Fields {
    /**
     * The script to run to start 'flutter daemon'.
     */
    @SerializedName("daemonScript")
    private String daemonScript;

    /**
     * The script to run to start 'flutter doctor'.
     */
    @SerializedName("doctorScript")
    private String doctorScript;

    /**
     *
     */
    @SerializedName("launchScript")
    private String launchScript;

    Fields() {
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Fields)) return false;
      final Fields other = (Fields)obj;
      return Objects.equal(daemonScript, other.daemonScript)
             && Objects.equal(doctorScript, other.doctorScript)
             && Objects.equal(launchScript, other.launchScript);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(daemonScript, doctorScript, launchScript);
    }
  }

  private static final Gson GSON = new Gson();
  private static final Logger LOG = Logger.getInstance(PluginConfig.class);
}
