// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.runner.server.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugConfiguration;
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugParameters;
import com.jetbrains.lang.dart.ui.BasicComboBoxWithBrowseButton;
import com.jetbrains.lang.dart.util.PubspecYamlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.jetbrains.lang.dart.util.PubspecYamlUtil.PUBSPEC_YAML;

public class DartRemoteDebugConfigurationEditor extends SettingsEditor<DartRemoteDebugConfiguration> {

  private JPanel myMainPanel;
  private BasicComboBoxWithBrowseButton<NameAndPath> myDartProjectCombo;
  private JBLabel myHintLabel;

  private final Project myProject;
  private final SortedSet<NameAndPath> myComboItems = new TreeSet<>();

  public DartRemoteDebugConfigurationEditor(final @NotNull Project project) {
    myProject = project;
    myDartProjectCombo.addBrowseFolderListener(DartBundle.message("button.browse.dialog.title.select.dart.project.path"),
                                               myProject,
                                               FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                                 .withTitle(DartBundle.message("button.browse.dialog.title.select.dart.project.path")),
                                               this::resolveNameAndPath,
                                               this::applySelectedProjectPath);
    initDartProjectsCombo(project);
    myHintLabel.setCopyable(true);
  }

  private void initDartProjectsCombo(final @NotNull Project project) {
    myDartProjectCombo.setRenderer(SimpleListCellRenderer.create("", NameAndPath::getPresentableText));

    if (project.isDefault()) return;

    ReadAction.nonBlocking(() -> {
      SortedSet<NameAndPath> items = new TreeSet<>();
      for (VirtualFile pubspecFile : FilenameIndex.getVirtualFilesByName(PUBSPEC_YAML, GlobalSearchScope.projectScope(project))) {
        ProgressManager.checkCanceled();
        items.add(new NameAndPath(PubspecYamlUtil.getDartProjectName(pubspecFile), pubspecFile.getParent().getPath()));
      }

      if (items.isEmpty()) {
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
          ProgressManager.checkCanceled();
          if (FileTypeIndex.containsFileOfType(DartFileType.INSTANCE, GlobalSearchScopesCore.directoryScope(project, contentRoot, true))) {
            items.add(new NameAndPath(null, contentRoot.getPath()));
          }
        }
      }
      return items;
    })
      .finishOnUiThread(ModalityState.any(), items -> {
        final Object selectedItem = myDartProjectCombo.getSelectedItem();
        myComboItems.addAll(items);
        myDartProjectCombo.setModel(new DefaultComboBoxModel<>(myComboItems.toArray(NameAndPath[]::new)));
        if (selectedItem != null) {
          myDartProjectCombo.setSelectedItem(selectedItem);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return myMainPanel;
  }

  @Override
  protected void resetEditorFrom(final @NotNull DartRemoteDebugConfiguration config) {
    final DartRemoteDebugParameters params = config.getParameters();
    setSelectedProjectPath(params.getDartProjectPath());
  }

  private void setSelectedProjectPath(final @NotNull String projectPath) {
    if (projectPath.isEmpty()) return;

    ReadAction.nonBlocking(() -> resolveNameAndPath(projectPath))
      .finishOnUiThread(ModalityState.any(), this::applySelectedProjectPath)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private @NotNull NameAndPath resolveNameAndPath(final @NotNull String projectPath) {
    final VirtualFile pubspecFile = LocalFileSystem.getInstance().findFileByPath(projectPath + "/" + PUBSPEC_YAML);
    final String projectName = pubspecFile == null ? null : PubspecYamlUtil.getDartProjectName(pubspecFile);
    return new NameAndPath(projectName, projectPath);
  }

  private void applySelectedProjectPath(final @NotNull NameAndPath item) {
    if (!myComboItems.contains(item)) {
      myComboItems.add(item);
      myDartProjectCombo.setModel(new DefaultComboBoxModel<>(myComboItems.toArray(NameAndPath[]::new)));
    }
    myDartProjectCombo.setSelectedItem(item);
  }

  @Override
  protected void applyEditorTo(final @NotNull DartRemoteDebugConfiguration config) {
    final DartRemoteDebugParameters params = config.getParameters();
    final Object selectedItem = myDartProjectCombo.getSelectedItem();
    params.setDartProjectPath(selectedItem == null ? "" : selectedItem.toString().trim());
  }

  private void createUIComponents() {
    myDartProjectCombo = new BasicComboBoxWithBrowseButton<>();
  }

  private static class NameAndPath implements Comparable<NameAndPath> {
    private final @Nullable String myName;
    private final @NotNull String myPath;

    NameAndPath(final @Nullable String name, final @NotNull String path) {
      myName = name;
      myPath = path;
    }

    public String getPresentableText() {
      return myName == null ? FileUtil.toSystemDependentName(myPath) : myName + " (" + FileUtil.toSystemDependentName(myPath) + ")";
    }

    @Override
    public String toString() {
      return myPath;
    }

    @Override
    public boolean equals(final Object o) {
      return (o instanceof NameAndPath) && myPath.equals(((NameAndPath)o).myPath);
    }

    @Override
    public int hashCode() {
      return myPath.hashCode();
    }

    @Override
    public int compareTo(final NameAndPath o) {
      return myPath.compareTo(o.myPath); // root project goes first, before its subprojects
    }
  }
}
