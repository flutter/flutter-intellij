/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import org.jetbrains.annotations.NotNull;

public class FlutterSdkChannel {

  public enum ID {

    // Do not change this order.
    UNKNOWN("unknown"), STABLE("stable"), BETA("beta"), DEV("dev"), MASTER("master");

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
}
