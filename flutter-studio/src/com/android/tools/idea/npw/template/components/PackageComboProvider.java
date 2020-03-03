/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.template.components;

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.templates.Parameter;
import com.google.common.collect.Iterables;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.ui.RecentsManager;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides an editable combobox which allows the user to specify a package name (or pull from a
 * list of recently used packages).
 */
public final class PackageComboProvider extends ParameterComponentProvider<EditorComboBox> {

  @NotNull private final Project myProject;
  @NotNull private final String myInitialPackage;
  @NotNull private final String myRecentsKey;

  public PackageComboProvider(@NotNull Project project,
                              @NotNull Parameter parameter,
                              @NotNull String initialPackage,
                              @NotNull String recentsKey) {
    super(parameter);
    myProject = project;
    myInitialPackage = initialPackage;
    myRecentsKey = recentsKey;
  }

  @NotNull
  @Override
  protected EditorComboBox createComponent(@NotNull Parameter parameter) {
    Document doc =
      JavaReferenceEditorUtil.createDocument(myInitialPackage, myProject, false, JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE);
    assert doc != null;
    final EditorComboBox classComboBox = new EditorComboBox(doc, myProject, StdFileTypes.JAVA);

    // Make sure our suggested package is in the recents list and at the top
    RecentsManager.getInstance(myProject).registerRecentEntry(myRecentsKey, myInitialPackage);
    List<String> recents = RecentsManager.getInstance(myProject).getRecentEntries(myRecentsKey);
    assert recents != null; // We just added at least one entry!

    classComboBox.setHistory(Iterables.toArray(recents, String.class));
    return classComboBox;
  }

  @Nullable
  @Override
  public AbstractProperty<?> createProperty(@NotNull final EditorComboBox classComboBox) {
    return new TextProperty(classComboBox);
  }

  @Override
  public void accept(@NotNull EditorComboBox component) {
    RecentsManager recentsManager = RecentsManager.getInstance(myProject);
    recentsManager.registerRecentEntry(myRecentsKey, component.getText());
  }
}
