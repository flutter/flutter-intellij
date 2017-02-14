/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * An in-memory snapshot of the flutter.json file from a Bazel workspace.
 */
public class PluginConfig {
  private final @NotNull Fields fields;

  private PluginConfig(@NotNull Fields fields) {
    this.fields = fields;
  }

  public @Nullable String getFlutterDaemonScript() {
    return fields.flutterDaemonScript;
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
  public static @Nullable PluginConfig load(@NotNull VirtualFile file) {
    final Computable<PluginConfig> readAction = () -> {
      try {
        final InputStreamReader input = new InputStreamReader(file.getInputStream(), "UTF-8");
        final Fields fields = GSON.fromJson(input, Fields.class);
        return new PluginConfig(fields);
      } catch (IOException e) {
        LOG.warn("failed to load flutter plugin config", e);
        return null;
      }
    };
    return ApplicationManager.getApplication().runReadAction(readAction);
  }

  /**
   * The JSON fields in a PluginConfig, as loaded from disk.
   */
  private static class Fields {
    @SerializedName("start_flutter_daemon")
    @SuppressWarnings("unused")
    private String flutterDaemonScript;

    Fields() {}

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Fields)) return false;
      final Fields other = (Fields)obj;
      return Objects.equal(flutterDaemonScript, other.flutterDaemonScript);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(flutterDaemonScript);
    }
  }

  private static final Gson GSON = new Gson();
  private static final Logger LOG = Logger.getInstance(PluginConfig.class);
}
