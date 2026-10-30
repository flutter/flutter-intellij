/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.dartlang.analysis.server.protocol.ExtractWidgetOptions;
import org.dartlang.analysis.server.protocol.RefactoringKind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ExtractWidgetRefactoringTest {

  @Test
  public void testExtractWidgetRefactoringIsDeprecatedForRemoval() {
    final Deprecated deprecated = ExtractWidgetRefactoring.class.getAnnotation(Deprecated.class);
    assertNotNull("ExtractWidgetRefactoring should be annotated with @Deprecated", deprecated);
    assertTrue("ExtractWidgetRefactoring should be marked for removal", deprecated.forRemoval());
  }

  @Test
  public void testInitialRefactoringOptions() {
    final Project project = mock(Project.class);
    final VirtualFile file = mock(VirtualFile.class);

    final ExtractWidgetRefactoring refactoring = new ExtractWidgetRefactoring(project, file, 0, 10);
    assertEquals(RefactoringKind.EXTRACT_WIDGET, refactoring.getKind());

    final ExtractWidgetOptions options = (ExtractWidgetOptions) refactoring.getOptions();
    assertNotNull(options);
    assertEquals("NewWidget", options.getName());
  }

  @Test
  public void testSetNameUpdatesOptions() {
    final Project project = mock(Project.class);
    final VirtualFile file = mock(VirtualFile.class);

    final ExtractWidgetRefactoring refactoring = new ExtractWidgetRefactoring(project, file, 10, 20);
    refactoring.setName("CustomWidgetName");

    final ExtractWidgetOptions options = (ExtractWidgetOptions) refactoring.getOptions();
    assertEquals("CustomWidgetName", options.getName());
  }
}
