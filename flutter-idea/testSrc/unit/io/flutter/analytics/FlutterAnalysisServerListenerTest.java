/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.fixes.DartQuickFix;
import com.jetbrains.lang.dart.fixes.DartQuickFixSet;
import io.flutter.FlutterInitializer;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.dartlang.analysis.server.protocol.AnalysisError;
import org.dartlang.analysis.server.protocol.AnalysisStatus;
import org.dartlang.analysis.server.protocol.PubStatus;
import org.dartlang.analysis.server.protocol.RequestError;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.flutter.analytics.FlutterAnalysisServerListener.*;
import static org.junit.Assert.*;

@SuppressWarnings({"LocalCanBeFinal"})
public class FlutterAnalysisServerListenerTest {
  private static final String fileContents = "void main() {\n" +
                                             "  group('group 1', () {\n" +
                                             "    test('test 1', () {});\n" +
                                             "  });\n" +
                                             "}";

  @Rule
  public final @NotNull CodeInsightProjectFixture projectFixture = Testing.makeCodeInsightModule();

  private @NotNull CodeInsightTestFixture innerFixture;
  private @NotNull Project project;
  private @NotNull PsiFile mainFile;
  private @NotNull MockAnalyticsTransport transport;
  private @NotNull Analytics analytics;
  private @NotNull FlutterAnalysisServerListener fasl;

  @SuppressWarnings("ConstantConditions")
  @Before
  public void setUp() {
    assert projectFixture.getInner() != null;
    innerFixture = projectFixture.getInner();
    assert innerFixture != null;
    project = innerFixture.getProject();
    assert project != null;
    mainFile = innerFixture.addFileToProject("lib/main.dart", fileContents);
    transport = new MockAnalyticsTransport();
    analytics = new Analytics("123e4567-e89b-12d3-a456-426655440000", "1.0", "IntelliJ CE", "2021.3.2");
    analytics.setTransport(transport);
    analytics.setCanSend(true);
    FlutterInitializer.setAnalytics(analytics);
    fasl = FlutterAnalysisServerListener.getInstance(project);
  }

  @After
  public void tearDown() {
    fasl.dispose();
  }

  @Test
  public void requestError() throws Exception {
    RequestError error = new RequestError("101", "error", "trace");
    fasl.requestError(error);
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertNotNull(map.get("exd"));
    assertTrue(map.get("exd").endsWith("trace"));
    assertTrue(map.get("exd").startsWith("R 101"));
  }

  @Test
  public void requestErrorNoCode() throws Exception {
    RequestError error = new RequestError(null, "error", "trace");
    fasl.requestError(error);
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertNotNull(map.get("exd"));
    assertTrue(map.get("exd").endsWith("trace"));
    assertTrue(map.get("exd").startsWith("R error"));
  }

  @Test
  public void serverError() throws Exception {
    fasl.serverError(true, "message", "trace");
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertNotNull(map.get("exd"));
    assertEquals("1", map.get("'exf'")); // extra quotes since 2016
    assertTrue(map.get("exd").endsWith("trace"));
    assertTrue(map.get("exd").contains("message"));
  }

  @Test
  public void acceptedCompletion() throws Exception {
    Editor editor = editor();
    Testing.runOnDispatchThread(() -> {
      editor.getSelectionModel().setSelection(18, 18);
      fasl.setLookupSelectionHandler();
      LookupImpl lookup = new LookupImpl(project, editor, new LookupArranger.DefaultArranger());
      LookupItem item = new LookupItem(LookupItem.TYPE_TEXT_ATTR, "gr");
      lookup.addItem(item, PrefixMatcher.ALWAYS_TRUE);
      lookup.addLookupListener(fasl.lookupSelectionHandler);
      LookupEvent lookupEvent = new LookupEvent(lookup, item, 'o');
      fasl.lookupSelectionHandler.itemSelected(lookupEvent);
    });
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(ACCEPTED_COMPLETION, map.get("ec"));
    assertEquals("gr", map.get("ea"));
    assertEquals("0", map.get("ev"));
  }

  @Test
  public void lookupCanceled() throws Exception {
    Editor editor = editor();
    Testing.runOnDispatchThread(() -> {
      editor.getSelectionModel().setSelection(18, 18);
      fasl.setLookupSelectionHandler();
      LookupImpl lookup = new LookupImpl(project, editor, new LookupArranger.DefaultArranger());
      LookupItem item = new LookupItem(LookupItem.TYPE_TEXT_ATTR, "gr");
      lookup.addItem(item, PrefixMatcher.ALWAYS_TRUE);
      lookup.addLookupListener(fasl.lookupSelectionHandler);
      LookupEvent lookupEvent = new LookupEvent(lookup, true);
      fasl.lookupSelectionHandler.lookupCanceled(lookupEvent);
      assertEquals(1, transport.sentValues.size());
      Map<String, String> map = transport.sentValues.get(0);
      assertEquals(REJECTED_COMPLETION, map.get("ec"));
      assertEquals(UNKNOWN_LOOKUP_STRING, map.get("ea"));
    });
  }

  @Test
  public void computedErrors() throws Exception {
    editor(); // Ensure file is open.
    String path = mainFile.getVirtualFile().getPath();
    List<AnalysisError> list = new ArrayList<>();
    list.add(new AnalysisError("ERROR", "", null, "", "", "101", "", null, false));
    fasl.computedErrors(path, list);
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertNotNull(map.get("ev"));
    assertEquals(DURATION, map.get("ea"));
    assertEquals(INITIAL_COMPUTE_ERRORS_TIME, map.get("ec"));
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void serverStatus() throws Exception {
    fasl.serverStatus(new AnalysisStatus(false, null), new PubStatus(false));
    assertEquals(4, transport.sentValues.size());
    checkStatus(transport.sentValues.get(0), ERRORS, "1");
    checkStatus(transport.sentValues.get(1), WARNINGS, "1");
    checkStatus(transport.sentValues.get(2), HINTS, "1");
    checkStatus(transport.sentValues.get(3), LINTS, "1");
  }

  private void checkStatus(@NotNull Map<String, String> map, String label, String value) {
    assertEquals("analysisServerStatus", map.get("ec"));
    assertEquals(label, map.get("ea"));
    assertEquals(value, map.get("ev"));
  }

  @Test
  public void quickFix() throws Exception {
    Editor editor = editor();
    DartAnalysisServerService analysisServer = DartAnalysisServerService.getInstance(project);
    PsiManager manager = PsiManager.getInstance(project);
    DartQuickFixSet quickFixSet = new DartQuickFixSet(manager, mainFile.getVirtualFile(), 18, null);
    DartQuickFix fix = new DartQuickFix(quickFixSet, 0);
    analysisServer.fireBeforeQuickFixInvoked(fix, editor, mainFile);
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(QUICK_FIX, map.get("ec"));
    assertEquals("", map.get("ea"));
    assertEquals("0", map.get("ev"));
  }

  @Test
  public void computedSearchResults() throws Exception {
    fasl.requestListener.onRequest("{\"method\":\"" + FIND_REFERENCES + "\",\"id\":\"2\"}");
    fasl.responseListener.onResponse("{\"event\":\"none\",\"id\":\"2\"}");
    fasl.computedSearchResults("2", new ArrayList<>(), true);
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(ROUND_TRIP_TIME, map.get("utc"));
    assertEquals(FIND_REFERENCES, map.get("utv"));
    assertNotNull(map.get("utt")); // Not checking a computed value, duration.
  }

  @Test
  public void computedCompletion() throws Exception {
    fasl.requestListener.onRequest("{\"method\":\"test\",\"id\":\"2\"}");
    fasl.responseListener.onResponse("{\"event\":\"none\",\"id\":\"2\"}");
    List none = new ArrayList();
    fasl.computedCompletion("2", 0, 0, none, none, none, none, true, "");
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(ROUND_TRIP_TIME, map.get("utc"));
    assertEquals("test", map.get("utv"));
    assertNotNull(map.get("utt")); // Not checking a computed value, duration.
  }

  @Test
  public void dasListenerLogging() throws Exception {
    fasl.requestListener.onRequest("{\"method\":\"test\",\"id\":\"2\"}");
    fasl.responseListener.onResponse("{\"event\":\"server.log\",\"params\":{\"entry\":{\"time\":\"1\",\"kind\":\"\",\"data\":\"\"}}}");
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(ANALYSIS_SERVER_LOG, map.get("ec"));
    assertEquals("time|1|kind||data|", map.get("ea"));
  }

  @Test
  public void dasListenerLoggingWithSdk() throws Exception {
    fasl.requestListener.onRequest("{\"method\":\"test\",\"id\":\"2\"}");
    fasl.responseListener.onResponse("{\"event\":\"server.log\",\"params\":{\"entry\":{\"time\":\"1\",\"kind\":\"\",\"data\":\"\",\"sdkVersion\":\"1\"}}}");
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(ANALYSIS_SERVER_LOG, map.get("ec"));
    assertEquals("time|1|kind||data|", map.get("ea"));
    assertEquals("1", map.get("cd2"));
  }

  @Test
  public void logE2ECompletionSuccessMS() throws Exception {
    DartCompletionTimerListener dctl = new DartCompletionTimerListener();
    dctl.dasListener = fasl;
    dctl.dartCompletionStart();
    dctl.dartCompletionEnd();
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(E2E_IJ_COMPLETION_TIME, map.get("utc"));
    assertEquals(SUCCESS, map.get("utv"));
    assertEquals("timing", map.get("t"));
    assertNotNull(map.get("utt")); // Not checking a computed value, duration.
  }

  @Test
  public void logE2ECompletionErrorMS() throws Exception {
    DartCompletionTimerListener dctl = new DartCompletionTimerListener();
    dctl.dasListener = fasl;
    dctl.dartCompletionStart();
    dctl.dartCompletionError("101", "message", "trace");
    assertEquals(1, transport.sentValues.size());
    Map<String, String> map = transport.sentValues.get(0);
    assertEquals(E2E_IJ_COMPLETION_TIME, map.get("utc"));
    assertEquals(FAILURE, map.get("utv"));
    assertEquals("timing", map.get("t"));
    assertNotNull(map.get("utt")); // Not checking a computed value, duration.
  }

  @NotNull
  private Editor editor() throws Exception{
    //noinspection ConstantConditions
    Testing.runOnDispatchThread(() -> innerFixture.openFileInEditor(mainFile.getVirtualFile()));
    Editor e = innerFixture.getEditor();
    assert e != null;
    return e;
  }
}
