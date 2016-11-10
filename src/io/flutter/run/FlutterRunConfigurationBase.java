/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FlutterRunConfigurationBase extends LocatableConfigurationBase
  implements RefactoringListenerProvider, RunConfiguration {

  protected FlutterRunConfigurationBase(final Project project, final ConfigurationFactory factory, final String name) {
    super(project, factory, name);
  }

  @NotNull
  public abstract FlutterRunnerParameters getRunnerParameters();

  public void checkConfiguration() throws RuntimeConfigurationException {
    getRunnerParameters().check(getProject());
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    XmlSerializer.serializeInto(getRunnerParameters(), element, new SkipDefaultValuesSerializationFilters());
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    XmlSerializer.deserializeInto(getRunnerParameters(), element);
  }

  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    if (!(element instanceof PsiFileSystemItem)) return null;

    final String filePath = getRunnerParameters().getFilePath();
    final VirtualFile file = filePath == null ? null : ((PsiFileSystemItem)element).getVirtualFile();
    if (file == null) return null;

    final String affectedPath = file.getPath();
    if (element instanceof PsiFile) {
      if (filePath.equals(affectedPath)) {
        return new RenameRefactoringListener(affectedPath);
      }
    }
    if (element instanceof PsiDirectory) {
      if (filePath.startsWith(affectedPath + "/")) {
        return new RenameRefactoringListener(affectedPath);
      }
    }

    return null;
  }

  private class RenameRefactoringListener extends UndoRefactoringElementAdapter {
    private @NotNull String myAffectedPath;

    private RenameRefactoringListener(final @NotNull String affectedPath) {
      myAffectedPath = affectedPath;
    }

    private String getNewPathAndUpdateAffectedPath(final @NotNull PsiElement newElement) {
      final String oldPath = getRunnerParameters().getFilePath();

      final VirtualFile newFile = newElement instanceof PsiFileSystemItem ? ((PsiFileSystemItem)newElement).getVirtualFile() : null;
      if (newFile != null && oldPath != null && oldPath.startsWith(myAffectedPath)) {
        final String newPath = newFile.getPath() + oldPath.substring(myAffectedPath.length());
        myAffectedPath = newFile.getPath(); // needed if refactoring will be undone
        return newPath;
      }

      return oldPath;
    }

    @Override
    protected void refactored(@NotNull final PsiElement element, @Nullable final String oldQualifiedName) {
      final boolean generatedName = getName().equals(suggestedName());
      final String filePath = getRunnerParameters().getFilePath();
      final boolean updateWorkingDir = filePath != null &&
                                       PathUtil.getParentPath(filePath).equals(getRunnerParameters().getWorkingDirectory());

      final String newPath = getNewPathAndUpdateAffectedPath(element);
      getRunnerParameters().setFilePath(newPath);

      if (updateWorkingDir) {
        getRunnerParameters().setWorkingDirectory(PathUtil.getParentPath(newPath));
      }

      if (generatedName) {
        setGeneratedName();
      }
    }
  }
}

