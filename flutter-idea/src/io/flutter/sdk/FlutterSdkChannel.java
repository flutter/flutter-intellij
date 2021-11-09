/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@NonNls
public class FlutterSdkChannel {

  public enum ID {

    // Do not change this order. An unknown branch is assumed to be off master.
    STABLE("stable"), BETA("beta"), DEV("dev"), MASTER("master"), UNKNOWN("unknown");

    private final String name;

    ID(String name) {
      this.name = name;
    }

    public String toString() {
      return name;
    }

    @NotNull
    public static ID fromText(String name) {
      switch (name) {
        case "master":
          return MASTER;
        case "dev":
          return DEV;
        case "beta":
          return BETA;
        case "stable":
          return STABLE;
        default:
          return UNKNOWN;
      }
    }
  }

  @NotNull
  private final ID channel;

  @NotNull
  public static FlutterSdkChannel fromText(@NotNull String text) {
    return new FlutterSdkChannel(ID.fromText(text));
  }

  private FlutterSdkChannel(@NotNull ID channel) {
    this.channel = channel;
  }

  @NotNull
  public ID getID() {
    return channel;
  }

  public String toString() {
    return "channel " + channel.toString();
  }

  @NotNull
  public static String parseChannel(@NotNull String text) {
    String[] lines = text.split("\n"); // TODO(messick) check windows
    for (String line : lines) {
      if (line.startsWith("*")) {
        return line.substring(2);
      }
    }
    return "unknown";
  }
}

