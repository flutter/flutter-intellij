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
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.EditorTestFixture;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.dart.FlutterOutlineListener;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

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
    new FlutterOutline("First", 0, 0, 0, 0, "", null, null, null, null, null, null);
  private static final FlutterOutline secondFlutterOutline =
    new FlutterOutline("Second", 0, 0, 0, 0, "", null, null, null, null, null, null);
  private static final FlutterOutline outlineWithCorrectLength =
    new FlutterOutline("Third", 0, fileContents.length(), 0, 0, "", null, null, null, null, null, null);

  @Rule
  public final CodeInsightProjectFixture projectFixture = Testing.makeCodeInsightModule();

  CodeInsightTestFixture innerFixture;
  Project project;
  Module module;
  ActiveEditorsOutlineService service;
  Listener listener;
  TestFlutterDartAnalysisServer flutterDas;
  PsiFile mainFile;
  String mainPath;

  @Before
  public void setUp() {
    innerFixture = projectFixture.getInner();
    project = projectFixture.getProject();
    flutterDas = new TestFlutterDartAnalysisServer(project);
    service = new ActiveEditorsOutlineService(project, flutterDas);
    listener = new Listener();
    service.addListener(listener);
    mainFile = innerFixture.addFileToProject("lib/main.dart", fileContents);
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
    assertThat(listener.mostRecentOutline, equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> innerFixture.openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = innerFixture.getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(project, editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(listener.mostRecentOutline, equalTo(firstFlutterOutline));

    // Move the caret inside of the name of the test group.
    Testing.runOnDispatchThread(() -> editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(1, 10)));

    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(listener.mostRecentOutline, equalTo(firstFlutterOutline));

    Testing.runOnDispatchThread(() -> editorTestFixture.type("longer name"));
    flutterDas.updateOutline(mainFile.getVirtualFile().getCanonicalPath(), secondFlutterOutline);

    assertThat(listener.outlineChanged.get(mainPath), equalTo(2));
    assertThat(listener.mostRecentOutline, equalTo(secondFlutterOutline));
  }

  @Test
  public void notifiesOutlineChangedWhenOpeningAndClosingFiles() throws Exception {
    assertThat(listener.outlineChanged.keySet(), not(hasItem(mainPath)));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(null));
    assertThat(listener.mostRecentOutline, equalTo(null));
    assertThat(listener.editorsChanged, equalTo(0));

    Testing.runOnDispatchThread(() -> innerFixture.openFileInEditor(mainFile.getVirtualFile()));
    final Editor editor = innerFixture.getEditor();
    final EditorTestFixture editorTestFixture = new EditorTestFixture(project, editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(mainPath));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(listener.mostRecentPath, equalTo(mainPath));
    assertThat(listener.mostRecentOutline, equalTo(firstFlutterOutline));

    // Open another file.
    final PsiFile fileTwo = innerFixture.addFileToProject("lib/main_two.dart", fileContents);
    final String fileTwoPath = fileTwo.getVirtualFile().getCanonicalPath();
    final EditorTestFixture editorTestFixtureTwo = new EditorTestFixture(project, editor, fileTwo.getVirtualFile());
    Testing.runOnDispatchThread(() -> innerFixture.openFileInEditor(fileTwo.getVirtualFile()));
    flutterDas.updateOutline(fileTwoPath, secondFlutterOutline);

    assertThat(listener.outlineChanged.keySet(), hasItem(fileTwoPath));
    assertThat(listener.outlineChanged.get(fileTwoPath), equalTo(1));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(1));
    assertThat(listener.mostRecentPath, equalTo(fileTwoPath));
    assertThat(listener.mostRecentOutline, equalTo(secondFlutterOutline));

    // NOTE: We can't test what happens when closing an editor because CodeInsightTestFixture always has one open editor.

    Testing.runOnDispatchThread(() -> innerFixture.openFileInEditor(mainFile.getVirtualFile()));
    final EditorTestFixture editorTestFixtureThree = new EditorTestFixture(project, editor, mainFile.getVirtualFile());
    flutterDas.updateOutline(mainPath, firstFlutterOutline);

    assertThat(listener.outlineChanged.get(fileTwoPath), equalTo(1));
    assertThat(listener.outlineChanged.get(mainPath), equalTo(2));
    assertThat(listener.mostRecentPath, equalTo(mainPath));
    assertThat(listener.mostRecentOutline, equalTo(firstFlutterOutline));
  }

  @Test
  public void getIfUpdatedDeterminesOutlineValidity() throws Exception {
    Testing.runOnDispatchThread(() -> {
      innerFixture.openFileInEditor(mainFile.getVirtualFile());
      assertThat(service.getIfUpdated(mainFile), nullValue());
      flutterDas.updateOutline(mainPath, firstFlutterOutline);
      assertThat(service.getIfUpdated(mainFile), nullValue());
      flutterDas.updateOutline(mainPath, secondFlutterOutline);
      assertThat(service.getIfUpdated(mainFile), nullValue());
      flutterDas.updateOutline(mainPath, outlineWithCorrectLength);
      assertThat(service.getIfUpdated(mainFile), equalTo(outlineWithCorrectLength));
    });
  }

  private class Listener implements ActiveEditorsOutlineService.Listener {
    int editorsChanged = 0;
    final Map<String, Integer> outlineChanged = new HashMap<>();
    FlutterOutline mostRecentOutline = null;
    String mostRecentPath = null;

    @Override
    public void onOutlineChanged(@NotNull String path, FlutterOutline outline) {
      final Integer changes = outlineChanged.get(path);
      outlineChanged.put(path, changes == null ? 1 : changes + 1);
      mostRecentPath = path;
      mostRecentOutline = outline;
    }
  }

  private static class TestFlutterDartAnalysisServer extends FlutterDartAnalysisServer {

    public TestFlutterDartAnalysisServer(@NotNull Project project) {
      super(project);
    }

    void updateOutline(@NotNull String path, @NotNull FlutterOutline outline) {
      for (FlutterOutlineListener listener : fileOutlineListeners.get(path)) {
        listener.outlineUpdated(path, outline, null);
      }
    }
  }
}
