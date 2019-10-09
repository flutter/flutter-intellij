/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils.math;

import org.junit.Test;

import java.util.ArrayList;

import static io.flutter.utils.math.TestUtils.*;
import static org.junit.Assert.*;

/**
 * This code is ported from the Dart vector_math package.
 */
@SuppressWarnings("ConstantConditions")
public class Matrix4Test {
  @Test
  public void testMatrix4InstacingFromFloat32List() {
    final double[] float32List = new double[]{
      1.0,
      2.0,
      3.0,
      4.0,
      5.0,
      6.0,
      7.0,
      8.0,
      9.0,
      10.0,
      11.0,
      12.0,
      13.0,
      14.0,
      15.0,
      16.0
    };
    final Matrix4 input = new Matrix4(float32List);
    final Matrix4 inputB = new Matrix4(float32List);
    assertEquals(input, inputB);

    assertEquals(input.getStorage()[0], 1.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[1], 2.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[2], 3.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[3], 4.0, TestUtils.errorThreshold);

    assertEquals(input.getStorage()[4], 5.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[5], 6.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[6], 7.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[7], 8.0, TestUtils.errorThreshold);

    assertEquals(input.getStorage()[8], 9.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[9], 10.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[10], 11.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[11], 12.0, TestUtils.errorThreshold);

    assertEquals(input.getStorage()[12], 13.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[13], 14.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[14], 15.0, TestUtils.errorThreshold);
    assertEquals(input.getStorage()[15], 16.0, TestUtils.errorThreshold);
  }

  @Test
  public void testMatrix4Transpose() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> expectedOutput = new ArrayList<>();
    inputA.add(parseMatrix4(
      "0.337719409821377   0.780252068321138   0.096454525168389   0.575208595078466\n" +
      "0.900053846417662   0.389738836961253   0.131973292606335   0.059779542947156\n" +
      "0.369246781120215   0.241691285913833   0.942050590775485   0.234779913372406\n" +
      "0.111202755293787   0.403912145588115   0.956134540229802   0.353158571222071"));
    expectedOutput.add(inputA.get(0).transposed());

    for (int i = 0; i < inputA.size(); i++) {
      inputA.get(i).transpose();
      relativeTest(inputA.get(i), expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4VectorMultiplication() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Vector4> inputB = new ArrayList<>();
    final ArrayList<Vector4> expectedOutput = new ArrayList<>();

    inputA.add(parseMatrix4(
      "0.337719409821377   0.780252068321138   0.096454525168389   0.575208595078466\n" +
      "0.900053846417662   0.389738836961253   0.131973292606335   0.059779542947156\n" +
      "0.369246781120215   0.241691285913833   0.942050590775485   0.234779913372406\n" +
      "0.111202755293787   0.403912145588115   0.956134540229802   0.353158571222071"));
    inputB.add(new Vector4(0.821194040197959,
                           0.015403437651555,
                           0.043023801657808,
                           0.168990029462704));
    expectedOutput.add(new Vector4(0.390706088480722,
                                   0.760902311900085,
                                   0.387152194918898,
                                   0.198357495624973));

    assert (inputA.size() == inputB.size());
    assert (expectedOutput.size() == inputB.size());

    for (int i = 0; i < inputA.size(); i++) {
      final Vector4 output = inputA.get(i).operatorMultiply(inputB.get(i));
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4Multiplication() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> inputB = new ArrayList<>();
    final ArrayList<Matrix4> expectedOutput = new ArrayList<>();

    inputA.add(parseMatrix4(
      "0.587044704531417   0.230488160211558   0.170708047147859   0.923379642103244\n" +
      "0.207742292733028   0.844308792695389   0.227664297816554   0.430207391329584\n" +
      "0.301246330279491   0.194764289567049   0.435698684103899   0.184816320124136\n" +
      "0.470923348517591   0.225921780972399   0.311102286650413   0.904880968679893"));
    inputB.add(parseMatrix4(
      "0.979748378356085   0.408719846112552   0.711215780433683   0.318778301925882\n" +
      "0.438869973126103   0.594896074008614   0.221746734017240   0.424166759713807\n" +
      "0.111119223440599   0.262211747780845   0.117417650855806   0.507858284661118\n" +
      "0.258064695912067   0.602843089382083   0.296675873218327   0.085515797090044"));
    expectedOutput.add(parseMatrix4(
      "0.933571062150012   0.978468014433530   0.762614053950618   0.450561572247979\n" +
      "0.710396171182635   0.906228190244263   0.489336274658484   0.576762187862375\n" +
      "0.476730868989407   0.464650419830879   0.363428748133464   0.415721232510293\n" +
      "0.828623949506267   0.953951612073692   0.690010785130483   0.481326146122225"));

    assert (inputA.size() == inputB.size());
    assert (expectedOutput.size() == inputB.size());

    for (int i = 0; i < inputA.size(); i++) {
      final Matrix4 output = inputA.get(i).operatorMultiply(inputB.get(i));
      //print('${inputA[i].cols}x${inputA[i].rows} * ${inputB[i].cols}x${inputB[i].rows} = ${output.cols}x${output.rows}');
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4Adjoint() {
    final ArrayList<Matrix4> input = new ArrayList<>();
    final ArrayList<Matrix4> expectedOutput = new ArrayList<>();

    input.add(parseMatrix4(
      "0.934010684229183   0.011902069501241   0.311215042044805   0.262971284540144\n" +
      "0.129906208473730   0.337122644398882   0.528533135506213   0.654079098476782\n" +
      "0.568823660872193   0.162182308193243   0.165648729499781   0.689214503140008\n" +
      "0.469390641058206   0.794284540683907   0.601981941401637   0.748151592823709"));
    expectedOutput.add(parseMatrix4(
      "0.104914550911225  -0.120218628213523   0.026180662741638   0.044107217835411\n" +
      "-0.081375770192194  -0.233925009984709  -0.022194776259965   0.253560794325371\n" +
      "0.155967414263983   0.300399085119975  -0.261648453454468  -0.076412061081351\n" +
      "-0.104925204524921   0.082065846290507   0.217666653572481  -0.077704028180558"));
    input.add(parseMatrix4("1     0     0     0\n" +
                           "0     1     0     0\n" +
                           "0     0     1     0\n" +
                           "0     0     0     1"));
    expectedOutput.add(parseMatrix4("1     0     0     0\n" +
                                    "0     1     0     0\n" +
                                    "0     0     1     0\n" +
                                    "0     0     0     1"));

    input.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    expectedOutput.add(parseMatrix4(
      "-0.100386867815513   0.076681891597503  -0.049082198794982  -0.021689260610181\n" +
      "-0.279454715225440  -0.269081505356250   0.114433412778961   0.133858687769130\n" +
      "0.218879650360982   0.073892735462981   0.069073300555062  -0.132069899391626\n" +
      "0.183633794399577   0.146113141160308  -0.156100829983306  -0.064859465665816"));

    assert (input.size() == expectedOutput.size());

    for (int i = 0; i < input.size(); i++) {
      final Matrix4 output = input.get(i).clone();
      output.scaleAdjoint(1.0);
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4Determinant() {
    final ArrayList<Matrix4> input = new ArrayList<>();
    final ArrayList<Double> expectedOutput = new ArrayList<>();
    input.add(parseMatrix4(
      "0.046171390631154   0.317099480060861   0.381558457093008   0.489764395788231\n" +
      "0.097131781235848   0.950222048838355   0.765516788149002   0.445586200710899\n" +
      "0.823457828327293   0.034446080502909   0.795199901137063   0.646313010111265\n" +
      "0.694828622975817   0.438744359656398   0.186872604554379   0.709364830858073"));
    expectedOutput.add(-0.199908980087990);

    input.add(parseMatrix4(
      "  -2.336158020850647   0.358791716162913   0.571930324052307   0.866477090273158\n" +
      "-1.190335868711951   1.132044609886021  -0.693048859451418   0.742195189800671\n" +
      "0.015919048685702   0.552417702663606   1.020805610524362  -1.288062497216858\n" +
      "3.020318574990609  -1.197139524685751  -0.400475005629390   0.441263145991252"));
    expectedOutput.add(-5.002276533849802);

    input.add(parseMatrix4(
      "0.934010684229183   0.011902069501241   0.311215042044805   0.262971284540144\n" +
      "0.129906208473730   0.337122644398882   0.528533135506213   0.654079098476782\n" +
      "0.568823660872193   0.162182308193243   0.165648729499781   0.689214503140008\n" +
      "0.469390641058206   0.794284540683907   0.601981941401637   0.748151592823709"));
    expectedOutput.add(0.117969860982876);
    assert (input.size() == expectedOutput.size());

    for (int i = 0; i < input.size(); i++) {
      final double output = input.get(i).determinant();
      //print('${input[i].cols}x${input[i].rows} = $output');
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4SelfTransposeMultiply() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> inputB = new ArrayList<>();
    final ArrayList<Matrix4> expectedOutput = new ArrayList<>();

    inputA.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    inputB.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    expectedOutput.add(parseMatrix4(
      "1.096629343508065   1.170948826011164   0.975285713492989   1.047596917860438\n" +
      "1.170948826011164   1.987289692246011   1.393079247172284   1.945966332001094\n" +
      "0.975285713492989   1.393079247172284   1.138698195167051   1.266161729169725\n" +
      "1.047596917860438   1.945966332001094   1.266161729169725   2.023122749969790"));

    assert (inputA.size() == inputB.size());
    assert (inputB.size() == expectedOutput.size());

    for (int i = 0; i < inputA.size(); i++) {
      final Matrix4 output = inputA.get(i).clone();
      output.transposeMultiply(inputB.get(i));
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4SelfMultiply() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> inputB = new ArrayList<>();
    final ArrayList<Matrix4> expectedOutput = new ArrayList<>();

    inputA.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    inputB.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    expectedOutput.add(parseMatrix4(
      "0.237893273152584   0.241190507375353   0.115471053480014   0.188086069635435\n" +
      "0.916103942227480   1.704973929800637   1.164721763902784   1.675285658272358\n" +
      "0.919182849383279   1.351023203753565   1.053750106199745   1.215382950294249\n" +
      "1.508657696357159   2.344965008135463   1.450552688877760   2.316940716769603"));

    assert (inputA.size() == inputB.size());
    assert (inputB.size() == expectedOutput.size());

    for (int i = 0; i < inputA.size(); i++) {
      final Matrix4 output = inputA.get(i).clone();
      output.multiply(inputB.get(i));
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4SelfMultiplyTranspose() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> inputB = new ArrayList<>();
    final ArrayList<Matrix4> expectedOutput = new ArrayList<>();

    inputA.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    inputB.add(parseMatrix4(
      "0.450541598502498   0.152378018969223   0.078175528753184   0.004634224134067\n" +
      "0.083821377996933   0.825816977489547   0.442678269775446   0.774910464711502\n" +
      "0.228976968716819   0.538342435260057   0.106652770180584   0.817303220653433\n" +
      "0.913337361501670   0.996134716626885   0.961898080855054   0.868694705363510"));
    expectedOutput.add(parseMatrix4(
      "0.232339681975335   0.201799089276976   0.197320406329789   0.642508126615338\n" +
      "0.201799089276976   1.485449982570056   1.144315170085286   1.998154153033270\n" +
      "0.197320406329789   1.144315170085286   1.021602397682138   1.557970885061235\n" +
      "0.642508126615338   1.998154153033270   1.557970885061235   3.506347918663387"));

    assert (inputA.size() == inputB.size());
    assert (inputB.size() == expectedOutput.size());

    for (int i = 0; i < inputA.size(); i++) {
      final Matrix4 output = inputA.get(i).clone();
      output.multiplyTranspose(inputB.get(i));
      relativeTest(output, expectedOutput.get(i));
    }
  }

  @Test
  public void testMatrix4Translation() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> inputB = new ArrayList<>();
    final ArrayList<Matrix4> output1 = new ArrayList<>();
    final ArrayList<Matrix4> output2 = new ArrayList<>();

    inputA.add(Matrix4.identity());
    inputB.add(Matrix4.translationValues(1.0, 3.0, 5.7));
    output1.add(inputA.get(0).operatorMultiply(inputB.get(0)));
    final Matrix4 tmpMatrix = Matrix4.identity();
    tmpMatrix.translate(1.0, 3.0, 5.7);
    output2.add(tmpMatrix);

    assert (inputA.size() == inputB.size());
    assert (output1.size() == output2.size());

    for (int i = 0; i < inputA.size(); i++) {
      relativeTest(output1.get(i), output2.get(i));
    }
  }

  @Test
  public void testMatrix4Scale() {
    final ArrayList<Matrix4> inputA = new ArrayList<>();
    final ArrayList<Matrix4> inputB = new ArrayList<>();
    final ArrayList<Matrix4> output1 = new ArrayList<>();
    final ArrayList<Matrix4> output2 = new ArrayList<>();

    inputA.add(Matrix4.identity());
    inputB.add(Matrix4.diagonal3Values(1.0, 3.0, 5.7));
    output1.add(inputA.get(0).operatorMultiply(inputB.get(0)));
    final Matrix4 tmpMatrix = Matrix4.identity();
    tmpMatrix.scale(1.0, 3.0, 5.7);

    output2.add(tmpMatrix);

    assert (inputA.size() == inputB.size());
    assert (output1.size() == output2.size());

    for (int i = 0; i < inputA.size(); i++) {
      relativeTest(output1.get(i), output2.get(i));
    }
  }

  @Test
  public void testMatrix4Column() {
    final Matrix4 I = Matrix4.identity();
    I.setZero();
    assertEquals(I.get(0), 0.0, TestUtils.errorThreshold);
    final Vector4 c0 = new Vector4(1.0, 2.0, 3.0, 4.0);
    I.setColumn(0, c0);
    assertEquals(I.get(0), 1.0, TestUtils.errorThreshold);
    c0.setX(4.0);
    assertEquals(I.get(0), 1.0, TestUtils.errorThreshold);
    assertEquals(c0.getX(), 4.0, TestUtils.errorThreshold);
  }

  @Test
  public void testMatrix4Inversion() {
    final Matrix4 m = new Matrix4(1.0, 0.0, 2.0, 2.0, 0.0, 2.0, 1.0, 0.0, 0.0, 1.0, 0.0,
                                  1.0, 1.0, 2.0, 1.0, 4.0);
    final Matrix4 result = Matrix4.identity();
    result.setZero();
    final double det = result.copyInverse(m);
    assertEquals(det, 2.0, TestUtils.errorThreshold);
    assertEquals(result.entry(0, 0), -2.0, TestUtils.errorThreshold);
    assertEquals(result.entry(1, 0), 1.0, TestUtils.errorThreshold);
    assertEquals(result.entry(2, 0), -8.0, TestUtils.errorThreshold);
    assertEquals(result.entry(3, 0), 3.0, TestUtils.errorThreshold);
    assertEquals(result.entry(0, 1), -0.5, TestUtils.errorThreshold);
    assertEquals(result.entry(1, 1), 0.5, TestUtils.errorThreshold);
    assertEquals(result.entry(2, 1), -1.0, TestUtils.errorThreshold);
    assertEquals(result.entry(3, 1), 0.5, TestUtils.errorThreshold);
    assertEquals(result.entry(0, 2), 1.0, TestUtils.errorThreshold);
    assertEquals(result.entry(1, 2), 0.0, TestUtils.errorThreshold);
    assertEquals(result.entry(2, 2), 2.0, TestUtils.errorThreshold);
    assertEquals(result.entry(3, 2), -1.0, TestUtils.errorThreshold);
    assertEquals(result.entry(0, 3), 0.5, TestUtils.errorThreshold);
    assertEquals(result.entry(1, 3), -0.5, TestUtils.errorThreshold);
    assertEquals(result.entry(2, 3), 2.0, TestUtils.errorThreshold);
    assertEquals(result.entry(3, 3), -0.5, TestUtils.errorThreshold);
  }

  @Test
  public void testMatrix4Dot() {
    final Matrix4 matrix = new Matrix4(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0,
                                       9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0);

    final Vector4 v = new Vector4(1.0, 2.0, 3.0, 4.0);

    assertEquals(matrix.dotRow(0, v), 90.0, TestUtils.errorThreshold);
    assertEquals(matrix.dotRow(1, v), 100.0, TestUtils.errorThreshold);
    assertEquals(matrix.dotRow(2, v), 110.0, TestUtils.errorThreshold);
    assertEquals(matrix.dotColumn(0, v), 30.0, TestUtils.errorThreshold);
    assertEquals(matrix.dotColumn(1, v), 70.0, TestUtils.errorThreshold);
    assertEquals(matrix.dotColumn(2, v), 110.0, TestUtils.errorThreshold);
  }

  @Test()
  public void testMatrix4Equals() {
    assertEquals(Matrix4.identity(), Matrix4.identity());
    assertNotEquals(Matrix4.zero(), Matrix4.identity());
    assertNotEquals(Matrix4.zero(), 5);
    assertEquals(
      Matrix4.identity().hashCode(), Matrix4.identity().hashCode());
  }

  @Test()
  public void testMatrix4InvertConstructor() {
    boolean exception = false;
    try {
      Matrix4.inverted(Matrix4.zero());
      fail(); // don't hit here.
    }
    catch (IllegalArgumentException e) {
      exception = true;
    }
    assertTrue(exception);

    assertEquals(Matrix4.inverted(Matrix4.identity()),
                 Matrix4.identity());
  }

  @Test()
  public void testMatrix4tryInvert() {
    assertNull(Matrix4.tryInvert(Matrix4.zero()));
    assertEquals(Matrix4.tryInvert(Matrix4.identity()),
                 Matrix4.identity());
  }

  @Test()
  public void testMatrix4SkewConstructor() {
    final Matrix4 m = Matrix4.skew(0.0, 1.57);
    final Matrix4 m2 = Matrix4.skewY(1.57);

    assertEquals(m.entry(0, 0), 1.0, errorThreshold);
    assertEquals(m.entry(1, 1), 1.0, errorThreshold);
    assertEquals(m.entry(2, 2), 1.0, errorThreshold);
    assertEquals(m.entry(3, 3), 1.0, errorThreshold);
    relativeTest(m.entry(1, 0), Math.tan(1.57));
    assertEquals(m.entry(0, 1), 0.0, errorThreshold);

    assertEquals(m2, m);

    final Matrix4 n = Matrix4.skew(1.57, 0.0);
    final Matrix4 n2 = Matrix4.skewX(1.57);

    assertEquals(n.entry(0, 0), 1.0, errorThreshold);
    assertEquals(n.entry(1, 1), 1.0, errorThreshold);
    assertEquals(n.entry(2, 2), 1.0, errorThreshold);
    assertEquals(n.entry(3, 3), 1.0, errorThreshold);
    assertEquals(n.entry(1, 0), 0.0, errorThreshold);
    relativeTest(m.entry(1, 0), Math.tan(1.57));

    assertEquals(n2, n);
  }

  @Test()
  public void testLeftTranslate() {
    // Our test point.
    final Vector3 p = new Vector3(0.5, 0.0, 0.0);

    // Scale 2x matrix.
    Matrix4 m = Matrix4.diagonal3Values(2.0, 2.0, 2.0);
    // After scaling, translate along the X axis.
    m.leftTranslate(1.0);

    // Apply the transformation to p. This will move (0.5, 0, 0) to (2.0, 0, 0).
    // Scale: 0.5 -> 1.0.
    // Translate: 1.0 -> 2.0
    Vector3 result = m.transformed3(p);
    assertEquals(result.getX(), 2.0, errorThreshold);
    assertEquals(result.getY(), 0.0, errorThreshold);
    assertEquals(result.getZ(), 0.0, errorThreshold);

    // Scale 2x matrix.
    m = Matrix4.diagonal3Values(2.0, 2.0, 2.0);
    // Before scaling, translate along the X axis.
    m.translate(1.0);

    // Apply the transformation to p. This will move (0.5, 0, 0) to (3.0, 0, 0).
    // Translate: 0.5 -> 1.5.
    // Scale: 1.5 -> 3.0.
    result = m.transformed3(p);
    assertEquals(result.getX(), 3.0, errorThreshold);
    assertEquals(result.getY(), 0.0, errorThreshold);
    assertEquals(result.getZ(), 0.0, errorThreshold);
  }

  @Test()
  public void testMatrixClassifiers() {
    assertFalse(Matrix4.zero().isIdentity());
    assertTrue(Matrix4.zero().isZero());
    assertTrue(Matrix4.identity().isIdentity());
    assertFalse(Matrix4.identity().isZero());
  }
}
