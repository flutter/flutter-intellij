/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.flutter.FlutterConstants;
import io.flutter.analytics.AnalyticsConstants;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the reload-on-save analytics outcomes reported by {@link FlutterReloadManager#reloadOnSave}.
 */
public class FlutterReloadManagerReloadOnSaveTest extends BasePlatformTestCase {

  private record Event(String trigger, String outcome, @Nullable Long durationMs, @Nullable Integer errorCode) {}

  private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
  private FlutterReloadManager manager;
  private FlutterApp app;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    manager = FlutterReloadManager.getInstance(getProject());
    assertNotNull(manager);
    // Restored when the test's root disposable is disposed, because the light project (and its services) is shared.
    manager.setReloadOnSaveReporterForTest(
      (trigger, outcome, durationMs, errorCode) -> events.add(new Event(trigger, outcome, durationMs, errorCode)),
      getTestRootDisposable());

    app = mock(FlutterApp.class);
    when(app.getProject()).thenReturn(getProject());
    when(app.getMode()).thenReturn(RunMode.RUN);
    when(app.getLaunchMode()).thenReturn(FlutterLaunchMode.DEBUG);
    when(app.appSupportsHotReload()).thenReturn(true);
    when(app.isStarted()).thenReturn(true);
    when(app.isConnected()).thenReturn(true);
    when(app.isReloading()).thenReturn(false);
    when(app.transitionStartingHotReload()).thenReturn(FlutterApp.State.STARTED);
  }

  // Outcomes

  public void testSuccessReportsDuration() throws Exception {
    final DaemonApi.RestartResult ok = result(0, null);
    when(app.performHotReload(anyBoolean(), anyString())).thenReturn(CompletableFuture.completedFuture(ok));

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_SAVE_ACTION, false);

    final Event event = onlyEvent();
    assertEquals(AnalyticsConstants.TRIGGER_SAVE_ACTION, event.trigger());
    assertEquals(AnalyticsConstants.OUTCOME_SUCCESS, event.outcome());
    assertNotNull(event.durationMs());
    assertNull(event.errorCode());
    verify(app).performHotReload(true, FlutterConstants.RELOAD_REASON_SAVE);
  }

  public void testErrorResultReportsCodeNotMessage() throws Exception {
    final DaemonApi.RestartResult failed = result(3, "lib/main.dart: secret user code");
    when(app.performHotReload(anyBoolean(), anyString())).thenReturn(CompletableFuture.completedFuture(failed));

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, false);

    final Event event = onlyEvent();
    assertEquals(AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, event.trigger());
    assertEquals(AnalyticsConstants.OUTCOME_ERROR, event.outcome());
    assertEquals(Integer.valueOf(3), event.errorCode());
  }

  public void testExceptionalCompletionReportsException() throws Exception {
    final CompletableFuture<DaemonApi.RestartResult> failed = new CompletableFuture<>();
    failed.completeExceptionally(new IOException("error from app.restart: boom"));
    when(app.performHotReload(anyBoolean(), anyString())).thenReturn(failed);

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_SAVE_ACTION, false);

    final Event event = onlyEvent();
    assertEquals(AnalyticsConstants.OUTCOME_EXCEPTION, event.outcome());
    assertNotNull(event.durationMs());
    assertNull(event.errorCode());
  }

  public void testNullResultReportsException() throws Exception {
    when(app.performHotReload(anyBoolean(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_SAVE_ACTION, false);

    assertEquals(AnalyticsConstants.OUTCOME_EXCEPTION, onlyEvent().outcome());
  }

  // Skips

  public void testNotConnectedIsReported() throws Exception {
    when(app.isConnected()).thenReturn(false);

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, false);

    assertEquals(AnalyticsConstants.OUTCOME_SKIPPED_NOT_CONNECTED, onlyEvent().outcome());
    verify(app, never()).performHotReload(anyBoolean(), anyString());
  }

  public void testSyntaxErrorInAnyEditorSkipsWithoutTouchingState() throws Exception {
    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, true);

    assertEquals(AnalyticsConstants.OUTCOME_SKIPPED_SYNTAX_ERROR_ANY_EDITOR, onlyEvent().outcome());
    verify(app, never()).transitionStartingHotReload();
    verify(app, never()).performHotReload(anyBoolean(), anyString());
  }

  public void testSyntaxErrorInAnyEditorIsReportedAtMostOncePerInterval() throws Exception {
    final Document document = validDocument();

    manager.reloadOnSave(app, document, AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, true);
    manager.reloadOnSave(app, document, AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, true);

    assertEquals(AnalyticsConstants.OUTCOME_SKIPPED_SYNTAX_ERROR_ANY_EDITOR, onlyEvent().outcome());
  }

  public void testSyntaxErrorInCurrentFileRestoresStateAndSkips() throws Exception {
    manager.reloadOnSave(app, documentWithSyntaxError(), AnalyticsConstants.TRIGGER_SAVE_ACTION, false);

    assertEquals(AnalyticsConstants.OUTCOME_SKIPPED_SYNTAX_ERROR_CURRENT_FILE, onlyEvent().outcome());
    verify(app).cancelHotReloadState(FlutterApp.State.STARTED);
    verify(app, never()).performHotReload(anyBoolean(), anyString());
  }

  // Not reported

  public void testAlreadyReloadingIsNotReported() throws Exception {
    // Also how the second path of an explicit Save All (and the plugin's own reload saves) stays silent.
    when(app.isReloading()).thenReturn(true);

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, false);

    assertNoEvent();
    verify(app, never()).transitionStartingHotReload();
  }

  public void testAppNotStartedIsNotReported() throws Exception {
    when(app.isStarted()).thenReturn(false);

    manager.reloadOnSave(app, validDocument(), AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, false);

    assertNoEvent();
    verify(app, never()).transitionStartingHotReload();
  }

  // Helpers

  private @NotNull Document validDocument() {
    return document("void main() {}\n");
  }

  private @NotNull Document documentWithSyntaxError() {
    return document("void main( {\n");
  }

  private @NotNull Document document(@NotNull String text) {
    final PsiFile file = myFixture.configureByText("main.dart", text);
    final Document document = file.getViewProvider().getDocument();
    assertNotNull(document);
    return document;
  }

  /** Waits for exactly one event, and checks that no second one follows. */
  private @NotNull Event onlyEvent() throws InterruptedException {
    final Event event = events.poll(10, TimeUnit.SECONDS);
    assertNotNull("expected a reload-on-save event", event);
    assertNoEvent();
    return event;
  }

  private void assertNoEvent() throws InterruptedException {
    final Event extra = events.poll(500, TimeUnit.MILLISECONDS);
    assertNull("unexpected extra event: " + extra, extra);
  }

  private static @NotNull DaemonApi.RestartResult result(int code, @Nullable String message) {
    final DaemonApi.RestartResult result = mock(DaemonApi.RestartResult.class);
    when(result.ok()).thenReturn(code == 0);
    when(result.getCode()).thenReturn(code);
    when(result.getMessage()).thenReturn(message);
    return result;
  }
}
