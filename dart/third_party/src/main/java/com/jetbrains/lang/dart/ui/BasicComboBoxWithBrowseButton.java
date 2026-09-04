// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.util.function.Consumer;
import java.util.function.Function;

public class BasicComboBoxWithBrowseButton<T> extends ComboBox<T> {
  private final ExtendableTextField myEditor = new ExtendableTextField();

  public BasicComboBoxWithBrowseButton() {
    setEditable(true);
    myEditor.setBorder(null);
    setEditor(new BasicComboBoxEditor() {
      @Override
      protected ExtendableTextField createEditorComponent() {
        return myEditor;
      }
    });
  }

  public <R> void addBrowseFolderListener(@Nullable @NlsContexts.DialogTitle String title,
                                          @Nullable Project project,
                                          @NotNull FileChooserDescriptor descriptor,
                                          @NotNull Function<String, R> backgroundResolver,
                                          @NotNull Consumer<R> edtConsumer) {
    myEditor.addExtension(
      ExtendableTextField.Extension.create(
        AllIcons.General.OpenDisk,
        AllIcons.General.OpenDiskHover,
        title,
        () -> {
          FileChooser.chooseFile(descriptor, project, this, null, file -> {
            String path = FileUtil.toSystemIndependentName(file.getPath());
            ReadAction.nonBlocking(() -> backgroundResolver.apply(path))
              .finishOnUiThread(ModalityState.any(), edtConsumer)
              .submit(AppExecutorUtil.getAppExecutorService());
          });
        }));
  }

  @NotNull
  public ExtendableTextField getEditorComponent() {
    return myEditor;
  }
}
