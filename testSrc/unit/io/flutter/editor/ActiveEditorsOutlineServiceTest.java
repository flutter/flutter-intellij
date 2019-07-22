/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.EditorTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.testing.AdaptedFixture;
import io.flutter.testing.Testing;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class ActiveEditorsOutlineServiceTest {
  private static final String fileContents = "void main() {\n" +
                                             "  group('group 1', () {\n" +
                                             "    test('test 1', () {});\n" +
                                             "  });\n" +
                                             "}";

  // The outline service doesn't care too much what the outline's contents are, only that the outlines update.
  // However, the request method uses the outline's length to determine if the outline is up-to-date.
  private static final FlutterOutline firstFlutterOutline =
    new FlutterOutline("First", 0, 0, 0, 0, "", null, null, null, null, null, null, 1, false, null, null, null, null);
  private static final FlutterOutline secondFlutterOutline =
    new FlutterOutline("Second", 0, 0, 0, 0, "", null, null, null, null, null, null, 2, false, null, null, null, null);
  private static final FlutterOutline outlineWithCorrectLength =
    new FlutterOutline("Third", 0, fileContents.length(), 0, 0, "", null, null, null, null, null, null, 3, false, null, null, null, null);

  @Rule
  public final AdaptedFixture<CodeInsightTestFixture> testFixture = new Fixture();

  ActiveEditorsOutlineService service;
  Listener listener;
  TestFlutterDartAnalysisServer flutterDas;
  PsiFile mainFile;
  String mainPath;

  @Before
  public void setUp() {
    flutterDas = new TestFlutterDartAnalysisServer(getProject());
    service = new ActiveEditorsOutlineService(getProject(), flutterDas) {
      @Override
      protected PsiFile getPsiFile(VirtualFile file) {
        return mainFile;
      }
    };
    listener = new Listener();
    service.addListener(listener);
    mainFile = getFixture().addFileToProject("lib/main.dart", fileContents);
    mainPath = mainFile.getVirtualFile().getCanonicalPath();
  }

  @After
  public void tearDown() {
    service.dispose();
  }

  // NOTE: we have no simple way of writing a test that includes calls from the Dart Analysis Server.  Instead, the tests
  // below mock out the behavior by notifying the Flutter Dart Analysis Server's listeners of changes when the DAS should do this.

  @Test
  public void notifiesWhenOutlinesChange() throws Exception {
    assertThat(listener.outlineChanged.keySet(), not(hasItem(mainPath)));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(null));
    assertThat(service.get(mainPath), equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = getFixture().getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(getProject(), editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(service.get(mainPath), equalTo(firstFlutterOutline));

    // Move the caret inside of the name of the test group.
    Testing.runOnDispatchThread(() -> editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(1, 10)));

    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(service.get(mainPath), equalTo(firstFlutterOutline));

    Testing.runOnDispatchThread(() -> editorTestFixture.type("longer name"));
    flutterDas.updateOutline(mainFile.getVirtualFile().getCanonicalPath(), secondFlutterOutline);

    assertThat(listener.outlineChanged.get(mainPath), equalTo(2));
    assertThat(service.get(mainPath), equalTo(secondFlutterOutline));
  }

  @Test
  public void notifiesOutlineChangedWhenOpeningAndClosingFiles() throws Exception {
    assertThat(listener.outlineChanged.keySet(), not(hasItem(mainPath)));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(null));
    assertThat(service.get(mainPath), equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = getFixture().getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(getProject(), editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(service.get(mainPath), equalTo(firstFlutterOutline));

    // Open another file.
    final PsiFile fileTwo = getFixture().addFileToProject("lib/main_two.dart", fileContents);
    final String fileTwoPath = fileTwo.getVirtualFile().getCanonicalPath();
    final EditorTestFixture editorTestFixtureTwo = new EditorTestFixture(getProject(), editor, fileTwo.getVirtualFile());
    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(fileTwo.getVirtualFile()));
    flutterDas.updateOutline(fileTwoPath, secondFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(fileTwoPath));
    assertThat(listener.outlineChanged.get(fileTwoPath), equalTo(1));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(service.get(fileTwoPath), equalTo(secondFlutterOutline));
    // The main file has been closed, so it is no longer there.
    assertThat(service.get(mainPath), equalTo(null));

    // NOTE: We can't test what happens when closing an editor because CodeInsightTestFixture always has one open editor.

    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(mainFile.getVirtualFile()));
    final EditorTestFixture editorTestFixtureThree = new EditorTestFixture(getProject(), editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.get(fileTwoPath), equalTo(1));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(2));
    assertThat(service.get(mainPath), equalTo(firstFlutterOutline));
    // Now file two is closed.
    assertThat(service.get(fileTwoPath), equalTo(null));
  }

  @Test
  public void requestReturnsImmediatelyIfTheOutlineHasTheCorrectLength() throws Exception {
    assertThat(listener.outlineChanged.keySet(), not(hasItem(mainPath)));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(null));
    assertThat(service.get(mainPath), equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = getFixture().getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(getProject(), editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, outlineWithCorrectLength);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(service.request(mainFile.getVirtualFile()), equalTo(outlineWithCorrectLength));
  }

  @Test
  public void requestWaitsForAnUpdatedOutlineIfNeeded() throws Exception {
    assertThat(listener.outlineChanged.keySet(), not(hasItem(mainPath)));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(null));
    assertThat(service.get(mainPath), equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = getFixture().getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(getProject(), editor, mainFile.getVirtualFile());

    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    // TODO(djshuckerow): Run this in a fake async executor so that the test doesn't slow travis down.
    final long now = Instant.now().toEpochMilli();
    assertThat(service.request(mainFile.getVirtualFile()), equalTo(firstFlutterOutline));
    final long after = Instant.now().toEpochMilli();
    assertThat(after - now >= 500, equalTo(true));
  }

  @Test
  public void requestWaitsForAnUpdatedOutlineAndUpdates() throws Exception {
    assertThat(listener.outlineChanged.keySet(), not(hasItem(mainPath)));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(null));
    assertThat(service.get(mainPath), equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> getFixture().openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = getFixture().getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(getProject(), editor, mainFile.getVirtualFile());

    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    // Set up a timer to change the outline in 100 milliseconds, before the request times out at 1000 milliseconds.
    final Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        flutterDas.updateOutline(mainPath, outlineWithCorrectLength);
      }
    }, 100);
    // TODO(djshuckerow): Run this in a fake async executor so that the test doesn't slow travis down.
    final long now = Instant.now().toEpochMilli();
    assertThat(service.request(mainFile.getVirtualFile()), equalTo(outlineWithCorrectLength));
    final long after = Instant.now().toEpochMilli();
    assertThat(after - now >= 100, equalTo(true));
  }

  private class Listener implements ActiveEditorsOutlineService.Listener {
    int editorsChanged = 0;
    final Map<String, Integer> outlineChanged = new HashMap<>();

    @Override
    public void onOutlineChanged(String path, FlutterOutline outline) {
      final Integer changes = outlineChanged.get(path);
      outlineChanged.put(path, changes == null ? 1 : changes + 1);
    }
  }

  private static class Fixture extends AdaptedFixture<CodeInsightTestFixture> {
    Fixture() {
      super((x) -> {
        final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
        final IdeaProjectTestFixture light = factory.createLightFixtureBuilder().getFixture();
        return factory.createCodeInsightFixture(light);
      }, false);
    }
  }

  private CodeInsightTestFixture getFixture() {
    return testFixture.getInner();
  }

  private Project getProject() {
    return testFixture.getInner().getProject();
  }

  private Module getModule() {
    return testFixture.getInner().getModule();
  }

  private static class TestFlutterDartAnalysisServer extends FlutterDartAnalysisServer {

    public TestFlutterDartAnalysisServer(@NotNull Project project) {
      super(project);
    }

    void updateOutline(String path, FlutterOutline outline) {
      for (FlutterOutlineListener listener : fileOutlineListeners.get(path)) {
        listener.outlineUpdated(path, outline, null);
      }
    }
  }
}
