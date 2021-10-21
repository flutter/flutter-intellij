/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import io.flutter.dart.DartSyntax;
import io.flutter.sdk.FlutterSdk;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("ALL")
public class FlutterIconLineMarkerTest extends io.flutter.ide.FlutterCodeInsightFixtureTestCase {

  private FlutterSdk getSdk() {
    final FlutterSdk mockSdk = mock(FlutterSdk.class);
    when(mockSdk.getHomePath()).thenReturn("testData/sdk");
    return mockSdk;
  }

  //@Test
  public void xtestLocatesIconsReference() throws Exception {
    final PsiElement testIdentifier = setUpDartElement("main() { Icons.access_alarm; }", "Icons", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
    assertNotNull(element);
  }

  //@Test
  public void xtestLocatesIconCtor() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { IconData(0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "IconData");
    assertNotNull(element);
  }

  //@Test
  public void xtestLocatesCupertinoIconCtor() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { IconData(0xe190, fontFamily: 'CupertinoIcons'); }", "IconData", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "IconData");
    assertNotNull(element);
  }

  //@Test
  public void xtestLocatesConstIconCtor() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { const IconData(0xe190, fontFamily: 'MaterialIcons'); }", "IconData", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
    assertNotNull(element);
  }

  //@Test
  public void xtestLocatesCupertinoIconsReference() throws Exception {
    final PsiElement testIdentifier = setUpDartElement("main() { CupertinoIcons.book; }", "CupertinoIcons", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
    assertNotNull(element);
  }

  //@Test
  public void xtestLocatesCupertinoIconsReferenceWithComment() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { CupertinoIcons . /* a book */ book; }", "CupertinoIcons", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartReferenceExpression element = DartSyntax.findEnclosingReferenceExpression(testIdentifier);
    assertNotNull(element);
  }

  @Test
  public void locatesIconCtorWithWhitespace() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { IconData ( 0xe190 ); }", "IconData", LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartCallExpression element = DartSyntax.findEnclosingFunctionCall(testIdentifier, "IconData");
    assertNotNull(element);
  }

  @Test
  public void locatesConstIconCtorWithLineEndComment() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { const IconData // comment\n ( 0xe190, fontFamily: 'MaterialIcons'); }", "IconData",
                       LeafPsiElement.class);
    final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    assertNotNull(marker);
    final DartNewExpression element = DartSyntax.findEnclosingNewExpression(testIdentifier);
    assertNotNull(element);
  }

  @Test
  public void allowsNullIconData() throws Exception {
    final PsiElement testIdentifier =
      setUpDartElement("main() { final x = IconData(null); }", "IconData", LeafPsiElement.class);
    try {
      final LineMarkerInfo<?> marker = new FlutterIconLineMarkerProvider().getLineMarkerInfo(testIdentifier, getSdk());
    }
    catch (NumberFormatException ex) {
      fail(ex.getMessage());
    }
  }
}
