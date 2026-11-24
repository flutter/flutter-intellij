/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.FormattingService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A manager class to run actions on save (formatting, organize imports, ...).
 */
public class FlutterSaveActionsManager implements Disposable {

  /**
   * IntelliJ Platform baseline where {@code AbstractLayoutCodeProcessor} waits for
   * {@link AsyncDocumentFormattingService} tasks before invoking {@code postRunnable} (IJPL-29913).
   * TODO: Remove the deferred-save workaround once {@code sinceBuild >= 262}.
   */
  private static final int ASYNC_FORMAT_STRUCTURED_WAIT_BASELINE = 262;
  private static final long ASYNC_FORMAT_FALLBACK_SAVE_DELAY_MS = 1_000;
  private static final long ASYNC_FORMAT_ORPHAN_TIMEOUT_MS = 30_000;

  /**
   * Initialize the save actions manager for the given project.
   */
  public static void init(@NotNull Project project) {
    // Call getInstance() will init FlutterSaveActionsManager for the given project by calling the constructor below.
    getInstance(project);
  }

  @Nullable
  public static FlutterSaveActionsManager getInstance(@NotNull Project project) {
    return project.getService(FlutterSaveActionsManager.class);
  }

  private final @NotNull Project myProject;
  private volatile boolean myDisposed;
  private final @NotNull Set<Document> myPendingDocuments =
    Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
  private final @NotNull Set<Document> mySavingDocuments =
    Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
  private final @NotNull Map<Document, DeferredAsyncSave> myDeferredSaves = new WeakHashMap<>();

  public FlutterSaveActionsManager(@NotNull Project project) {
    this.myProject = project;
    subscribeToDocumentSaves(project);
  }

  @VisibleForTesting
  FlutterSaveActionsManager(@NotNull Project project, @NotNull Disposable parentDisposable) {
    this.myProject = project;
    Disposer.register(parentDisposable, this);
    subscribeToDocumentSaves(project);
  }

  private void subscribeToDocumentSaves(@NotNull Project project) {
    final MessageBus bus = project.getMessageBus();
    final MessageBusConnection connection = bus.connect(this);
    connection.subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        // Don't try and format read only docs.
        if (!document.isWritable()) {
          return;
        }

        handleBeforeDocumentSaving(document);
      }
    });
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @VisibleForTesting
  void handleBeforeDocumentSaving(@NotNull Document document) {
    if (mySavingDocuments.contains(document) || myPendingDocuments.contains(document)) {
      return;
    }

    final FlutterSettings settings = FlutterSettings.getInstance();
    if (!settings.isFormatCodeOnSave() || myDisposed || !myProject.isInitialized() || myProject.isDisposed()) {
      return;
    }

    final PsiFile psiFile = getFormattablePsiFile(document);
    if (psiFile == null) {
      return;
    }

    scheduleFormatAndSave(document, psiFile, settings.isOrganizeImportsOnSave());
  }

  @Nullable
  private PsiFile getFormattablePsiFile(@NotNull Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null || !FlutterUtils.isDartFile(file)) {
      return null;
    }

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null || !psiFile.isValid()) {
      return null;
    }

    final Module module = ModuleUtil.findModuleForFile(file, myProject);
    if (module == null || !isDartSdkEnabled(module) || hasSyntaxErrors(psiFile)) {
      return null;
    }

    return psiFile;
  }

  private void scheduleFormatAndSave(@NotNull Document document, @NotNull PsiFile psiFile, boolean organizeImports) {
    final VirtualFile file = psiFile.getVirtualFile();
    myPendingDocuments.add(document);
    OpenApiUtils.safeInvokeLater(() -> {
      myPendingDocuments.remove(document);
      if (myDisposed || myProject.isDisposed() || !psiFile.isValid() || file == null || !file.isValid()) {
        return;
      }
      PsiDocumentManager.getInstance(myProject).commitDocument(document);
      if (hasSyntaxErrors(psiFile)) {
        return;
      }

      performFormatAndSave(document, psiFile, organizeImports);
    });
  }

  private static boolean hasSyntaxErrors(@NotNull PsiFile psiFile) {
    // Use PsiTreeUtil.hasErrorElements directly rather than PsiErrorElementUtil.hasErrors,
    // because DartHighlightingErrorFilter suppresses PSI error elements for in-project Dart files.
    return Boolean.TRUE.equals(OpenApiUtils.safeRunReadAction(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @VisibleForTesting
  void performFormatAndSave(@NotNull Document document, @NotNull PsiFile psiFile, boolean organizeImports) {
    final AbstractLayoutCodeProcessor processor = createProcessor(psiFile, organizeImports);
    if (!shouldDeferSaveForAsyncFormatting(psiFile)) {
      cancelDeferredSave(document);
      runProcessor(processor, () -> saveDocumentWithGuard(document));
      return;
    }

    final DeferredAsyncSave deferredSave = new DeferredAsyncSave(document);
    final DeferredAsyncSave previous;
    synchronized (myDeferredSaves) {
      previous = myDeferredSaves.put(document, deferredSave);
    }
    if (previous != null) {
      previous.cancel();
    }
    if (!deferredSave.attach()) {
      removeDeferredSaveIfMatches(document, deferredSave);
      return;
    }
    try {
      runProcessor(processor, deferredSave::onProcessorFinished);
    }
    catch (Throwable t) {
      deferredSave.cancel();
      throw t;
    }
  }

  private void cancelDeferredSave(@NotNull Document document) {
    final DeferredAsyncSave previous;
    synchronized (myDeferredSaves) {
      previous = myDeferredSaves.remove(document);
    }
    if (previous != null) {
      previous.cancel();
    }
  }

  private void removeDeferredSaveIfMatches(@NotNull Document document, @NotNull DeferredAsyncSave expected) {
    synchronized (myDeferredSaves) {
      if (myDeferredSaves.get(document) == expected) {
        myDeferredSaves.remove(document);
      }
    }
  }

  private void saveDocumentWithGuard(@NotNull Document document) {
    if (myDisposed || myProject.isDisposed() || !document.isWritable()) {
      return;
    }
    mySavingDocuments.add(document);
    try {
      FileDocumentManager.getInstance().saveDocument(document);
    }
    finally {
      mySavingDocuments.remove(document);
    }
  }

  /**
   * Returns true on IntelliJ Platform builds older than {@value #ASYNC_FORMAT_STRUCTURED_WAIT_BASELINE}
   * (before IJPL-29913) when the active {@link FormattingService} for {@code psiFile} is an
   * {@link AsyncDocumentFormattingService} (such as {@code LspFormattingService}).
   * <p>
   * On those builds, {@link ReformatCodeProcessor} runs its {@code postRunnable} before async
   * formatting applies its edits to the document. Saving immediately inside {@code postRunnable}
   * both leaves the formatted file unsaved and clears its content overlay mid-request, which causes
   * the Dart analysis server to abort {@code textDocument/formatting} with {@code ContentModified}
   * when imports were just modified by {@link OptimizeImportsProcessor}.
   */
  @VisibleForTesting
  boolean shouldDeferSaveForAsyncFormatting(@NotNull PsiFile psiFile) {
    if (getPlatformBaselineVersion() >= ASYNC_FORMAT_STRUCTURED_WAIT_BASELINE) {
      return false;
    }
    return isAsyncFormattingService(psiFile);
  }

  @VisibleForTesting
  int getPlatformBaselineVersion() {
    return ApplicationInfo.getInstance().getBuild().getBaselineVersion();
  }

  @VisibleForTesting
  boolean isAsyncFormattingService(@NotNull PsiFile psiFile) {
    return Boolean.TRUE.equals(OpenApiUtils.safeRunReadAction(() -> {
      for (FormattingService service : FormattingService.EP_NAME.getExtensionList()) {
        if (service.canFormat(psiFile)) {
          return service instanceof AsyncDocumentFormattingService;
        }
      }
      return false;
    }));
  }

  @VisibleForTesting
  long getAsyncFormatFallbackSaveDelayMs() {
    return ASYNC_FORMAT_FALLBACK_SAVE_DELAY_MS;
  }

  @VisibleForTesting
  @NotNull
  AbstractLayoutCodeProcessor createProcessor(@NotNull PsiFile psiFile, boolean organizeImports) {
    if (organizeImports) {
      return new ReformatCodeProcessor(createOptimizeImportsProcessor(psiFile), false);
    }
    return new ReformatCodeProcessor(psiFile, false);
  }

  @VisibleForTesting
  @NotNull
  OptimizeImportsProcessor createOptimizeImportsProcessor(@NotNull PsiFile psiFile) {
    return new OptimizeImportsProcessor(myProject, psiFile);
  }

  @VisibleForTesting
  void runProcessor(@NotNull AbstractLayoutCodeProcessor processor, @NotNull Runnable postRunnable) {
    processor.setPostRunnable(postRunnable);
    processor.run();
  }

  @VisibleForTesting
  boolean isDartSdkEnabled(@NotNull Module module) {
    return DartPlugin.isDartSdkEnabled(module);
  }

  /**
   * Coordinates a single deferred save for an {@link AsyncDocumentFormattingService} run on builds
   * prior to 262. {@link AsyncDocumentFormattingService} applies formatted text inside an
   * undo-transparent write action; detecting that edit lets us save immediately once formatting
   * finishes, cancel without saving if the user resumes editing, and fall back to saving after a
   * short delay if formatting returned no edits (e.g., when only {@link OptimizeImportsProcessor}
   * changed the file).
   */
  private final class DeferredAsyncSave {
    private final @NotNull Document document;
    private final @NotNull Disposable listenerDisposable =
      Disposer.newDisposable("FlutterSaveActionsManager.asyncSave");
    private final @NotNull AtomicBoolean finished = new AtomicBoolean(false);
    private final @NotNull AtomicBoolean asyncFormatApplied = new AtomicBoolean(false);
    private final @NotNull AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final @NotNull AtomicReference<ScheduledFuture<?>> scheduledTask = new AtomicReference<>();
    private volatile boolean awaitingFormatResult;
    private volatile long stampAtProcessorFinish;
    private volatile long stampAfterAsyncFormat;

    private DeferredAsyncSave(@NotNull Document document) {
      this.document = document;
    }

    boolean attach() {
      if (myDisposed || myProject.isDisposed()) {
        return false;
      }
      Disposer.register(FlutterSaveActionsManager.this, listenerDisposable);
      Disposer.register(listenerDisposable, this::onDisposed);
      document.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          onDocumentChanged();
        }
      }, listenerDisposable);
      // Safety cleanup if ReformatCodeProcessor aborts before running postRunnable.
      scheduleTask(ASYNC_FORMAT_ORPHAN_TIMEOUT_MS, () -> OpenApiUtils.safeInvokeLater(this::onOrphanTimeout));
      return true;
    }

    void onProcessorFinished() {
      if (finished.get()) {
        return;
      }
      if (asyncFormatApplied.get()) {
        if (finished.compareAndSet(false, true)) {
          Disposer.dispose(listenerDisposable);
          if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            saveDocumentWithGuard(document);
          }
        }
        return;
      }
      stampAtProcessorFinish = document.getModificationStamp();
      awaitingFormatResult = true;
      scheduleTask(getAsyncFormatFallbackSaveDelayMs(), () -> OpenApiUtils.safeInvokeLater(this::onFallbackTimeout));
    }

    void cancel() {
      if (finished.compareAndSet(false, true)) {
        Disposer.dispose(listenerDisposable);
      }
    }

    private void onOrphanTimeout() {
      if (!awaitingFormatResult) {
        cancel();
      }
    }

    private void onDocumentChanged() {
      if (finished.get()) {
        return;
      }
      final boolean isUndoTransparent = CommandProcessor.getInstance().isUndoTransparentActionInProgress();
      if (isUndoTransparent) {
        if (awaitingFormatResult) {
          stampAfterAsyncFormat = document.getModificationStamp();
          if (saveScheduled.compareAndSet(false, true)) {
            cancelScheduledTask();
            OpenApiUtils.safeInvokeLater(this::saveAfterAsyncFormat);
          }
        }
        else {
          asyncFormatApplied.set(true);
        }
        return;
      }

      // A regular edit after formatting finished (or while awaiting the format result) means the
      // user resumed typing; abandon the deferred save rather than saving partial edits.
      if (awaitingFormatResult || asyncFormatApplied.get()) {
        cancel();
      }
    }

    private void saveAfterAsyncFormat() {
      if (finished.compareAndSet(false, true)) {
        Disposer.dispose(listenerDisposable);
        if (!myDisposed
            && !myProject.isDisposed()
            && document.getModificationStamp() == stampAfterAsyncFormat
            && FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
          saveDocumentWithGuard(document);
        }
      }
    }

    private void onFallbackTimeout() {
      if (saveScheduled.get() || !finished.compareAndSet(false, true)) {
        return;
      }
      Disposer.dispose(listenerDisposable);
      if (!myDisposed
          && !myProject.isDisposed()
          && document.getModificationStamp() == stampAtProcessorFinish
          && FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
        saveDocumentWithGuard(document);
      }
    }

    private void scheduleTask(long delayMs, @NotNull Runnable task) {
      final ScheduledFuture<?> next =
        AppExecutorUtil.getAppScheduledExecutorService().schedule(task, delayMs, TimeUnit.MILLISECONDS);
      final ScheduledFuture<?> previous = scheduledTask.getAndSet(next);
      if (previous != null) {
        previous.cancel(false);
      }
      if (finished.get()) {
        next.cancel(false);
      }
    }

    private void cancelScheduledTask() {
      final ScheduledFuture<?> pending = scheduledTask.getAndSet(null);
      if (pending != null) {
        pending.cancel(false);
      }
    }

    private void onDisposed() {
      finished.set(true);
      cancelScheduledTask();
      removeDeferredSaveIfMatches(document, this);
    }
  }
}
