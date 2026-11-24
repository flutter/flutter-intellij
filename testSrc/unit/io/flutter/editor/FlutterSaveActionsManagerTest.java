/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import io.flutter.AbstractDartElementTest;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class FlutterSaveActionsManagerTest extends AbstractDartElementTest {
  private boolean originalFormatOnSave;
  private boolean originalOrganizeImportsOnSave;
  private Disposable testDisposable;

  @Before
  public void setUpSettings() {
    final FlutterSettings settings = FlutterSettings.getInstance();
    originalFormatOnSave = settings.isFormatCodeOnSave();
    originalOrganizeImportsOnSave = settings.isOrganizeImportsOnSave();
    testDisposable = Disposer.newDisposable();
  }

  @After
  public void tearDownSettings() {
    Disposer.dispose(testDisposable);
    final FlutterSettings settings = FlutterSettings.getInstance();
    settings.setFormatCodeOnSave(originalFormatOnSave);
    settings.setOrganizeImportsOnSave(originalOrganizeImportsOnSave);
  }

  @Test
  public void testInitAndGetInstance() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      FlutterSaveActionsManager.init(project);
      assertNotNull(FlutterSaveActionsManager.getInstance(project));
    });
  }

  @Test
  public void testCreateProcessorFormatOnly() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = fixture.getInner().configureByText("main.dart", "void main() {}\n");
      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, true);

      final AbstractLayoutCodeProcessor processor = manager.createProcessor(psiFile, false);
      assertTrue(processor instanceof ReformatCodeProcessor);
      assertTrue(manager.createdOptimizeImportsProcessors.isEmpty());
    });
  }

  @Test
  public void testCreateProcessorWithOrganizeImports() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = fixture.getInner().configureByText("main.dart", "void main() {}\n");
      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, true);

      final AbstractLayoutCodeProcessor processor = manager.createProcessor(psiFile, true);
      assertTrue(processor instanceof ReformatCodeProcessor);
      assertEquals(1, manager.createdOptimizeImportsProcessors.size());
    });
  }

  @Test
  public void testSkipsWhenFormatCodeOnSaveDisabled() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final Document document = configureDocument("main.dart", "void main() {}\n");

      FlutterSettings.getInstance().setFormatCodeOnSave(false);
      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, true);
      manager.handleBeforeDocumentSaving(document);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue(manager.executedProcessors.isEmpty());
    });
  }

  @Test
  public void testSkipsNonDartFile() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final Document document = configureDocument("notes.txt", "hello\n");

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, true);
      manager.handleBeforeDocumentSaving(document);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue(manager.executedProcessors.isEmpty());
    });
  }

  @Test
  public void testSkipsWhenDartSdkNotEnabled() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final Document document = configureDocument("main.dart", "void main() {}\n");

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, false);
      manager.handleBeforeDocumentSaving(document);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue(manager.executedProcessors.isEmpty());
    });
  }

  @Test
  public void testSkipsFileWithSyntaxErrors() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final Document document = configureDocument("broken.dart", "void main( {\n");

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, true);
      manager.handleBeforeDocumentSaving(document);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue(manager.executedProcessors.isEmpty());
    });
  }

  @Test
  public void testRunsFormatAndSaveWithoutRecursion() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final Document document = configureDocument("main.dart", "void main() {}\n");

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      FlutterSettings.getInstance().setOrganizeImportsOnSave(true);

      final RecordingSaveActionsManager manager = new RecordingSaveActionsManager(project, testDisposable, true, document);
      WriteAction.run(() -> {
        document.setText("void main(){}\n");
        PsiDocumentManager.getInstance(project).commitDocument(document);
      });
      FileDocumentManager.getInstance().saveDocument(document);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertEquals(1, manager.executedProcessors.size());
      final AbstractLayoutCodeProcessor processor = manager.executedProcessors.get(0);
      assertTrue(processor instanceof ReformatCodeProcessor);
      assertEquals(1, manager.createdOptimizeImportsProcessors.size());
    });
  }

  @Test
  public void testShouldDeferSaveForAsyncFormattingBaselineGate() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void main() {}\n");

      // In unit tests without a running LSP server, CoreFormattingService is selected (synchronous).
      final FlutterSaveActionsManager realManager = new FlutterSaveActionsManager(project, testDisposable);
      assertFalse(realManager.isAsyncFormattingService(psiFile));

      assertTrue(new AsyncStubSaveActionsManager(project, testDisposable, 253, true).shouldDeferSaveForAsyncFormatting(psiFile));
      assertTrue(new AsyncStubSaveActionsManager(project, testDisposable, 261, true).shouldDeferSaveForAsyncFormatting(psiFile));
      assertFalse(new AsyncStubSaveActionsManager(project, testDisposable, 262, true).shouldDeferSaveForAsyncFormatting(psiFile));
      assertFalse(new AsyncStubSaveActionsManager(project, testDisposable, 263, true).shouldDeferSaveForAsyncFormatting(psiFile));
      assertFalse(new AsyncStubSaveActionsManager(project, testDisposable, 253, false).shouldDeferSaveForAsyncFormatting(psiFile));
      assertFalse(new AsyncStubSaveActionsManager(project, testDisposable, 261, false).shouldDeferSaveForAsyncFormatting(psiFile));
    });
  }

  @Test
  public void testDefersSaveUntilAsyncFormatAppliesEdits() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "import 'dart:math';\nvoid   main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 5_000;
      manager.onRunProcessor = postRunnable -> {
        // Simulate OptimizeImportsProcessor editing inside a normal (non-undo-transparent) write action.
        WriteAction.run(() -> document.setText("void   main() {}\n"));
        postRunnable.run();
      };

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      manager.performFormatAndSave(document, psiFile, true);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      // postRunnable must NOT save immediately before the async formatter delivers its edit.
      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());

      // Simulate AsyncDocumentFormattingService applying formatted text inside runUndoTransparentAction.
      CommandProcessor.getInstance().runUndoTransparentAction(
        () -> WriteAction.run(() -> document.setText("void main() {}\n"))
      );
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals("void main() {}\n", document.getText());
      assertEquals(1, saveCount.get());
      assertEquals(1, manager.executedProcessors.size());
    });
  }

  @Test
  public void testDefersSaveAcrossMultipleUndoTransparentEdits() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void   main() {  }\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 5_000;
      manager.onRunProcessor = Runnable::run;

      manager.performFormatAndSave(document, psiFile, false);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      // Simulate a DocumentMerger applying multiple edits within a single undo-transparent action.
      CommandProcessor.getInstance().runUndoTransparentAction(() -> WriteAction.run(() -> {
        document.replaceString(4, 7, " ");
        document.replaceString(13, 15, "");
      }));
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertEquals("void main() {}\n", document.getText());
      assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(1, saveCount.get());
    });
  }

  @Test
  public void testSavesImmediatelyWhenAsyncFormatFinishesBeforePostRunnable() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void   main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 5_000;
      manager.onRunProcessor = postRunnable -> {
        CommandProcessor.getInstance().runUndoTransparentAction(
          () -> WriteAction.run(() -> document.setText("void main() {}\n"))
        );
        postRunnable.run();
      };

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      manager.performFormatAndSave(document, psiFile, false);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(1, saveCount.get());
      assertEquals(1, manager.executedProcessors.size());
    });
  }

  @Test
  public void testUserEditAfterFastAsyncFormatBeforePostRunnableCancelsSave() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void   main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 50;
      manager.onRunProcessor = postRunnable -> {
        CommandProcessor.getInstance().runUndoTransparentAction(
          () -> WriteAction.run(() -> document.setText("void main() {}\n"))
        );
        // Simulate a user typing right after async format finished, before postRunnable ran.
        WriteAction.run(() -> document.setText("void main() { /* wip */ }\n"));
        postRunnable.run();
      };

      manager.performFormatAndSave(document, psiFile, false);
      dispatchEventsFor(200);

      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testUserEditWhileAwaitingAsyncFormatCancelsDeferredSave() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "import 'dart:math';\nvoid main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 50;
      manager.onRunProcessor = postRunnable -> {
        WriteAction.run(() -> document.setText("void main() {}\n"));
        postRunnable.run();
      };

      manager.performFormatAndSave(document, psiFile, true);
      // User starts editing while waiting for the async format result.
      WriteAction.run(() -> document.setText("void main() { print(1); }\n"));
      dispatchEventsFor(200);

      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testUserEditAfterAsyncFormatBeforeDeferredSaveCancelsSave() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void   main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 5_000;
      manager.onRunProcessor = Runnable::run;

      manager.performFormatAndSave(document, psiFile, false);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      // Async formatting queues the deferred save on the EDT.
      CommandProcessor.getInstance().runUndoTransparentAction(
        () -> WriteAction.run(() -> document.setText("void main() {}\n"))
      );
      // Before that queued save executes, a user edit arrives.
      WriteAction.run(() -> document.setText("void main() { /* wip */ }\n"));
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testFallbackTimeoutSavesDirtyDocumentWhenAsyncFormatIsNoOp() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "import 'dart:math';\nvoid main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 50;
      manager.onRunProcessor = postRunnable -> {
        WriteAction.run(() -> document.setText("void main() {}\n"));
        postRunnable.run();
      };

      FlutterSettings.getInstance().setFormatCodeOnSave(true);
      manager.performFormatAndSave(document, psiFile, true);

      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());

      assertTrue("Expected fallback timer to save organized imports",
                 waitForCondition(() -> !FileDocumentManager.getInstance().isDocumentUnsaved(document), 5_000));
      assertEquals(1, saveCount.get());
      assertEquals(1, manager.executedProcessors.size());
    });
  }

  @Test
  public void testFallbackTimeoutSkipsCleanDocumentWhenNoEditsMade() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void main() {}\n");
      final Document document = getDocument(project, psiFile);
      FileDocumentManager.getInstance().saveDocument(document);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 50;
      manager.onRunProcessor = Runnable::run;

      manager.performFormatAndSave(document, psiFile, false);
      dispatchEventsFor(200);

      assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testNewDeferredSaveCancelsPreviousPendingSave() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "import 'dart:math';\nvoid main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.fallbackDelayMs = 50;
      manager.onRunProcessor = postRunnable -> {
        WriteAction.run(() -> document.setText("void main() {}\n"));
        postRunnable.run();
      };

      // First run schedules a 50ms fallback save.
      manager.performFormatAndSave(document, psiFile, true);

      // Second run replaces it with a much longer fallback timeout; old 50ms timer must be cancelled.
      manager.fallbackDelayMs = 60_000;
      manager.onRunProcessor = Runnable::run;
      manager.performFormatAndSave(document, psiFile, true);

      dispatchEventsFor(200);
      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testDisposeCancelsPendingDeferredSave() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "import 'dart:math';\nvoid main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final Disposable childDisposable = Disposer.newDisposable("testChild");
      Disposer.register(testDisposable, childDisposable);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, childDisposable, 261, true);
      manager.fallbackDelayMs = 50;
      manager.onRunProcessor = postRunnable -> {
        WriteAction.run(() -> document.setText("void main() {}\n"));
        postRunnable.run();
      };

      manager.performFormatAndSave(document, psiFile, true);
      Disposer.dispose(childDisposable);

      dispatchEventsFor(200);
      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testRunProcessorExceptionCleansUpDeferredSave() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "void   main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 261, true);
      manager.onRunProcessor = postRunnable -> {
        throw new IllegalStateException("simulated processor failure");
      };

      try {
        manager.performFormatAndSave(document, psiFile, false);
        fail("Expected IllegalStateException");
      }
      catch (IllegalStateException expected) {
        assertEquals("simulated processor failure", expected.getMessage());
      }

      // Subsequent undo-transparent edit must not trigger a save from the failed run.
      CommandProcessor.getInstance().runUndoTransparentAction(
        () -> WriteAction.run(() -> document.setText("void main() {}\n"))
      );
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(0, saveCount.get());
    });
  }

  @Test
  public void testBaseline262SavesImmediatelyInPostRunnable() throws Exception {
    run(() -> {
      final Project project = Objects.requireNonNull(fixture.getProject());
      final PsiFile psiFile = configurePsiFile("main.dart", "import 'dart:math';\nvoid main() {}\n");
      final Document document = getDocument(project, psiFile);
      final AtomicInteger saveCount = countSaves(project, document);

      final AsyncStubSaveActionsManager manager = new AsyncStubSaveActionsManager(project, testDisposable, 262, true);
      manager.fallbackDelayMs = 60_000;
      manager.onRunProcessor = postRunnable -> {
        WriteAction.run(() -> document.setText("void main() {}\n"));
        postRunnable.run();
      };

      manager.performFormatAndSave(document, psiFile, true);

      assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
      assertEquals(1, saveCount.get());
    });
  }

  @NotNull
  private PsiFile configurePsiFile(@NotNull String fileName, @NotNull String text) {
    final PsiFile psiFile = fixture.getInner().configureByText(fileName, text);
    ModuleRootModificationUtil.addContentRoot(
      Objects.requireNonNull(fixture.getModule()),
      psiFile.getVirtualFile().getParent()
    );
    return psiFile;
  }

  @NotNull
  private static Document getDocument(@NotNull Project project, @NotNull PsiFile psiFile) {
    return Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(psiFile));
  }

  @NotNull
  private Document configureDocument(@NotNull String fileName, @NotNull String text) {
    final Project project = Objects.requireNonNull(fixture.getProject());
    return getDocument(project, configurePsiFile(fileName, text));
  }

  @NotNull
  private AtomicInteger countSaves(@NotNull Project project, @NotNull Document target) {
    final AtomicInteger count = new AtomicInteger(0);
    project.getMessageBus().connect(testDisposable).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        if (document == target) {
          count.incrementAndGet();
        }
      }
    });
    return count;
  }

  private static boolean waitForCondition(@NotNull BooleanSupplier condition, long timeoutMs) {
    final long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    while (System.nanoTime() < deadlineNs) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      if (condition.getAsBoolean()) {
        return true;
      }
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return condition.getAsBoolean();
      }
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    return condition.getAsBoolean();
  }

  private static void dispatchEventsFor(long durationMs) {
    waitForCondition(() -> false, durationMs);
  }

  private static final class AsyncStubSaveActionsManager extends FlutterSaveActionsManager {
    private final int baselineVersion;
    private final boolean asyncFormatting;
    private final List<AbstractLayoutCodeProcessor> executedProcessors = new ArrayList<>();
    private long fallbackDelayMs = 50;
    @Nullable
    private Consumer<Runnable> onRunProcessor;

    private AsyncStubSaveActionsManager(@NotNull Project project,
                                        @NotNull Disposable parentDisposable,
                                        int baselineVersion,
                                        boolean asyncFormatting) {
      super(project, parentDisposable);
      this.baselineVersion = baselineVersion;
      this.asyncFormatting = asyncFormatting;
    }

    @Override
    int getPlatformBaselineVersion() {
      return baselineVersion;
    }

    @Override
    boolean isAsyncFormattingService(@NotNull PsiFile psiFile) {
      return asyncFormatting;
    }

    @Override
    long getAsyncFormatFallbackSaveDelayMs() {
      return fallbackDelayMs;
    }

    @Override
    boolean isDartSdkEnabled(@NotNull Module module) {
      return true;
    }

    @Override
    void runProcessor(@NotNull AbstractLayoutCodeProcessor processor, @NotNull Runnable postRunnable) {
      executedProcessors.add(processor);
      if (onRunProcessor != null) {
        onRunProcessor.accept(postRunnable);
      }
      else {
        postRunnable.run();
      }
    }
  }

  private static final class RecordingSaveActionsManager extends FlutterSaveActionsManager {
    private final boolean sdkEnabled;
    private final Document documentToModifyOnFormat;
    private final List<AbstractLayoutCodeProcessor> executedProcessors = new ArrayList<>();
    private final List<OptimizeImportsProcessor> createdOptimizeImportsProcessors = new ArrayList<>();

    private RecordingSaveActionsManager(@NotNull Project project, @NotNull Disposable parentDisposable, boolean sdkEnabled) {
      this(project, parentDisposable, sdkEnabled, null);
    }

    private RecordingSaveActionsManager(@NotNull Project project,
                                        @NotNull Disposable parentDisposable,
                                        boolean sdkEnabled,
                                        Document documentToModifyOnFormat) {
      super(project, parentDisposable);
      this.sdkEnabled = sdkEnabled;
      this.documentToModifyOnFormat = documentToModifyOnFormat;
    }

    @Override
    boolean isDartSdkEnabled(@NotNull Module module) {
      return sdkEnabled;
    }

    @Override
    @NotNull
    OptimizeImportsProcessor createOptimizeImportsProcessor(@NotNull PsiFile psiFile) {
      final OptimizeImportsProcessor processor = super.createOptimizeImportsProcessor(psiFile);
      createdOptimizeImportsProcessors.add(processor);
      return processor;
    }

    @Override
    void runProcessor(@NotNull AbstractLayoutCodeProcessor processor, @NotNull Runnable postRunnable) {
      executedProcessors.add(processor);
      if (documentToModifyOnFormat != null) {
        WriteAction.run(() -> documentToModifyOnFormat.setText("void main() {}\n"));
      }
      postRunnable.run();
    }
  }
}
