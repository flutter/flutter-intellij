/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link AndroidEmulator}.
 */
public class AndroidEmulatorTest {

  public static final class TrueEmulatorSettings {
    private static final TrueEmulatorSettings INSTANCE = new TrueEmulatorSettings();

    @SuppressWarnings("unused")
    public static TrueEmulatorSettings getInstance() {
      return INSTANCE;
    }

    @SuppressWarnings("unused")
    public boolean getLaunchInToolWindow() {
      return true;
    }
  }

  public static final class FalseEmulatorSettings {
    private static final FalseEmulatorSettings INSTANCE = new FalseEmulatorSettings();

    @SuppressWarnings("unused")
    public static FalseEmulatorSettings getInstance() {
      return INSTANCE;
    }

    @SuppressWarnings("unused")
    public boolean getLaunchInToolWindow() {
      return false;
    }
  }

  public static final class NullInstanceEmulatorSettings {
    @SuppressWarnings("unused")
    public static NullInstanceEmulatorSettings getInstance() {
      return null;
    }

    @SuppressWarnings("unused")
    public boolean getLaunchInToolWindow() {
      return true;
    }
  }

  public static final class MissingOptionMethodSettings {
    @SuppressWarnings("unused")
    public static MissingOptionMethodSettings getInstance() {
      return new MissingOptionMethodSettings();
    }
  }

  public static final class MissingGetInstanceSettings {
    @SuppressWarnings("unused")
    public boolean getLaunchInToolWindow() {
      return true;
    }
  }

  @Test
  public void getLaunchInToolWindow_returnsTrueWhenEnabled() {
    assertTrue(AndroidEmulator.getLaunchInToolWindow(TrueEmulatorSettings.class));
  }

  @Test
  public void getLaunchInToolWindow_returnsFalseWhenDisabled() {
    assertFalse(AndroidEmulator.getLaunchInToolWindow(FalseEmulatorSettings.class));
  }

  @Test
  public void getLaunchInToolWindow_returnsFalseWhenInstanceIsNull() {
    assertFalse(AndroidEmulator.getLaunchInToolWindow(NullInstanceEmulatorSettings.class));
  }

  @Test
  public void getLaunchInToolWindow_returnsFalseWhenOptionMethodMissing() {
    assertFalse(AndroidEmulator.getLaunchInToolWindow(MissingOptionMethodSettings.class));
  }

  @Test
  public void getLaunchInToolWindow_returnsFalseWhenGetInstanceMissing() {
    assertFalse(AndroidEmulator.getLaunchInToolWindow(MissingGetInstanceSettings.class));
  }
}
