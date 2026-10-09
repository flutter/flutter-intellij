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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

  @NotNull
  private Document configureDocument(@NotNull String fileName, @NotNull String text) {
    final Project project = Objects.requireNonNull(fixture.getProject());
    final PsiFile psiFile = fixture.getInner().configureByText(fileName, text);
    ModuleRootModificationUtil.addContentRoot(
      Objects.requireNonNull(fixture.getModule()),
      psiFile.getVirtualFile().getParent()
    );
    return Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(psiFile));
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
