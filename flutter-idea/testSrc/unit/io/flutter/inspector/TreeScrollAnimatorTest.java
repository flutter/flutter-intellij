/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import io.flutter.inspector.TreeScrollAnimator.Interval;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;

public class TreeScrollAnimatorTest {

  // TODO(jacobr): add some end to end tests of TreeScrollAnimator.
  @Test
  public void nonOverlappingIntervals() {
    {
      boolean invalidInput = false;
      try {
        // The required outside of the ideal interval.
        TreeScrollAnimator.clampInterval(
          new Interval(0, 10),
          new Interval(5, 15),
          8);
      }
      catch (IllegalArgumentException e) {
        invalidInput = true;
      }
      assertTrue(invalidInput);
    }

    {
      boolean invalidInput = false;
      try {
        // The required outside of the ideal interval.
        TreeScrollAnimator.clampInterval(
          new Interval(5, 10),
          new Interval(0, 12),
          8);
      }
      catch (IllegalArgumentException e) {
        invalidInput = true;
      }
      assertTrue(invalidInput);
    }
  }

  @Test
  public void negativeIntervals() {
    {
      boolean invalidInput = false;
      try {
        TreeScrollAnimator.clampInterval(
          new Interval(0, -3),
          new Interval(0, -1),
          8);
      }
      catch (IllegalArgumentException e) {
        invalidInput = true;
      }
      assertTrue(invalidInput);
    }

    {
      boolean invalidInput = false;
      try {
        TreeScrollAnimator.clampInterval(
          new Interval(0, 2),
          new Interval(0, 2),
          -5);
      }
      catch (IllegalArgumentException e) {
        invalidInput = true;
      }
      assertTrue(invalidInput);
    }
  }

  @Test
  public void idealIntervalFits() {
    final Interval required = new Interval(10, 20);
    final Interval ideal = new Interval(5, 100);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 100), ideal);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 150), ideal);
  }

  @Test
  public void requiredIntervalBarelyFits() {
    final Interval required = new Interval(10, 20);
    final Interval ideal = new Interval(5, 100);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 20), required);
  }

  @Test
  public void equalRequiredAndIdealIntervals() {
    final Interval required = new Interval(10, 20);
    final Interval ideal = new Interval(10, 20);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 30), ideal);
  }

  @Test
  public void requiredAtStartOfIdeal() {
    final Interval required = new Interval(10, 20);
    final Interval ideal = new Interval(10, 200);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 100), new Interval(10, 100));
  }

  @Test
  public void requiredAtEndOfIdeal() {
    final Interval required = new Interval(180, 20);
    final Interval ideal = new Interval(10, 190);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 80), new Interval(120, 80));
  }

  @Test
  public void requiredInMiddleOfIdeal() {
    final Interval required = new Interval(200, 100);
    final Interval ideal = new Interval(100, 300);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 200), new Interval(150, 200));
  }

  @Test
  public void requiredNearStartOfIdeal() {
    final Interval required = new Interval(120, 100);
    final Interval ideal = new Interval(100, 300);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 200), new Interval(110, 200));
  }

  @Test
  public void requiredNearEndOfIdeal() {
    final Interval required = new Interval(280, 100);
    final Interval ideal = new Interval(100, 300);
    assertEquals(TreeScrollAnimator.clampInterval(required, ideal, 200), new Interval(190, 200));
  }

  @Test
  public void intervalEqualityTest() {
    assertNotEquals(new Interval(0, 10), new Interval(0, 11));
    assertNotEquals(new Interval(5, 10), new Interval(6, 10));
    assertEquals(new Interval(5, 10), new Interval(5, 10));
  }
}