/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.EditorTestFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ActiveEditorsOutlineServiceTest {
  private final ProjectFixture testFixture = Testing.makeCodeInsightModule();
  public EditorTestFixture editorTestFixture;

  private static final String fileContents = "void main() {\n" +
                                             "  group('group 1', () {\n" +
                                             "    test('test 1', () {});\n" +
                                             "  });\n" +
                                             "}";

  @Test
  public void notifiesWhenEditorsChange() {
    final CodeInsightTestFixture fixture = (CodeInsightTestFixture)testFixture.getInner();
    final ActiveEditorsOutlineService service = ActiveEditorsOutlineService.getInstance(fixture.getProject());
    Listener listener = new Listener();
    service.addListener(listener);
    final PsiFile mainFile = fixture.addFileToProject("lib/main.dart", fileContents);
    assertEquals(listener.editorsChanged, 0);
    fixture.openFileInEditor(mainFile.getVirtualFile());
    editorTestFixture = new EditorTestFixture(fixture.getProject(), fixture.getEditor(), mainFile.getVirtualFile());
    assertEquals(listener.editorsChanged, 1);
  }

  private class Listener implements ActiveEditorsOutlineService.Listener {
    int editorsChanged = 0;
    final Map<String, Integer> outlineChanged = new HashMap<>();

    @Override
    public void onEditorsChanged() {
      editorsChanged++;
    }

    @Override
    public void onOutlineChanged(String path) {
      final Integer changes = outlineChanged.get(path);
      outlineChanged.put(path, changes == null ? 1 : changes + 1);
    }
  };
}
