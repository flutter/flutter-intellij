/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.animation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CurvesTest {
  @Test
  public void curveFlippedControl() {
    final Curve ease = Curves.EASE;
    final Curve flippedEase = ease.getFlipped();
    assertTrue(flippedEase.transform(0.0) < 0.001);
    assertTrue(flippedEase.transform(0.5) < ease.transform(0.5));
    assertTrue(flippedEase.transform(1.0) > 0.999);
  }

  @Test
  public void tresholdHasAThreshold() {
    final Curve step = new Threshold(0.25);
    assertEquals(step.transform(0.0), 0.0, 0.0001);
    assertEquals(step.transform(0.24), 0.0, 0.0001);
    assertEquals(step.transform(0.25), 1.0, 0.0001);
    assertEquals(step.transform(0.26), 1.0, 0.0001);
    assertEquals(step.transform(1.0), 1.0, 0.0001);
  }

  boolean inInclusiveRange(double value, double start, double end) {
    return value >= start && value <= end;
  }

  void expectStaysInBounds(Curve curve) {
    assertTrue(inInclusiveRange(curve.transform(0.0), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.1), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.2), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.3), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.4), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.5), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.6), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.7), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.8), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(0.9), 0.0, 1.0));
    assertTrue(inInclusiveRange(curve.transform(1.0), 0.0, 1.0));
  }

  @Test
  public void bounceStaysInBounds() {
    expectStaysInBounds(Curves.BOUNCE_IN);
    expectStaysInBounds(Curves.BOUNCE_OUT);
    expectStaysInBounds(Curves.BOUNCE_IN_OUT);
  }

  List<Double> estimateBounds(Curve curve) {
    final List<Double> values = new ArrayList<>();

    values.add(curve.transform(0.0));
    values.add(curve.transform(0.1));
    values.add(curve.transform(0.2));
    values.add(curve.transform(0.3));
    values.add(curve.transform(0.4));
    values.add(curve.transform(0.5));
    values.add(curve.transform(0.6));
    values.add(curve.transform(0.7));
    values.add(curve.transform(0.8));
    values.add(curve.transform(0.9));
    values.add(curve.transform(1.0));

    double max = values.get(0);
    double min = values.get(0);
    for (double value : values) {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
    final List<Double> ret = new ArrayList<>();
    ret.add(min);
    ret.add(max);
    return ret;
  }

  @Test
  public void ellasticOvershootsItsBounds() {
    List<Double> bounds;
    bounds = estimateBounds(Curves.ELASTIC_IN);
    assertTrue(bounds.get(0) < 0.0);
    assertTrue(bounds.get(1) <= 1.0);
    bounds = estimateBounds(Curves.ELASTIC_OUT);
    assertTrue(bounds.get(0) >= 0.0);
    assertTrue(bounds.get(1) >= 1.0);
    bounds = estimateBounds(Curves.ELASTIC_IN_OUT);
    assertTrue(bounds.get(0) < 0.0);
    assertTrue(bounds.get(1) > 1.0);
  }

  @Test
  public void decelerateDoesSo() {
    final List<Double> bounds = estimateBounds(Curves.DECELERATE);
    assertTrue(bounds.get(0) >= 0.0);
    assertTrue(bounds.get(1) <= 1.0);

    final double d1 = Curves.DECELERATE.transform(0.2) - Curves.DECELERATE.transform(0.0);
    final double d2 = Curves.DECELERATE.transform(1.0) - Curves.DECELERATE.transform(0.8);
    assertTrue(d2 < d1);
  }

  // TODO(jacobr): port this test from Dart.
  /*
  testInvalidTransformParameterShouldAssert() {
    expect(() = > const SawTooth(2).transform(-0.0001), throwsAssertionError);
    expect(() = > const SawTooth(2).transform(1.0001), throwsAssertionError);

    expect(() = > const Interval(0.0, 1.0).transform(-0.0001), throwsAssertionError);
    expect(() = > const Interval(0.0, 1.0).transform(1.0001), throwsAssertionError);

    expect(() = > const Threshold(0.5).transform(-0.0001), throwsAssertionError);
    expect(() = > const Threshold(0.5).transform(1.0001), throwsAssertionError);

    expect(() = > const ElasticInCurve().transform(-0.0001), throwsAssertionError);
    expect(() = > const ElasticInCurve().transform(1.0001), throwsAssertionError);

    expect(() = > const ElasticOutCurve().transform(-0.0001), throwsAssertionError);
    expect(() = > const ElasticOutCurve().transform(1.0001), throwsAssertionError);

    expect(() = > const Cubic(0.42, 0.0, 0.58, 1.0).transform(-0.0001), throwsAssertionError);
    expect(() = > const Cubic(0.42, 0.0, 0.58, 1.0).transform(1.0001), throwsAssertionError);

    expect(() = > Curves.decelerate.transform(-0.0001), throwsAssertionError);
    expect(() = > Curves.decelerate.transform(1.0001), throwsAssertionError);

    expect(() = > Curves.bounceIn.transform(-0.0001), throwsAssertionError);
    expect(() = > Curves.bounceIn.transform(1.0001), throwsAssertionError);

    expect(() = > Curves.bounceOut.transform(-0.0001), throwsAssertionError);
    expect(() = > Curves.bounceOut.transform(1.0001), throwsAssertionError);

    expect(() = > Curves.bounceInOut.transform(-0.0001), throwsAssertionError);
    expect(() = > Curves.bounceInOut.transform(1.0001), throwsAssertionError);
  }
  */
}
