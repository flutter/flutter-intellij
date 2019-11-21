/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import io.flutter.project.FlutterProjectModel;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In an Android project, edit the settings.gradle and the app/build.gradle files as described in the add-to-app spec.
 */
public class FlutterModuleImporter {
  @SuppressWarnings("FieldCanBeLocal")
  private static String EDIT_INSTR_LINK =
    "https://github.com/flutter/flutter/wiki/Add-Flutter-to-existing-apps#make-the-host-app-depend-on-the-flutter-module";

  private final FlutterProjectModel myModel;
  private boolean hasFinishedEditing = false;
  private StringWriter writer;
  private String myRelativePath;
  private String myNewline;

  public FlutterModuleImporter(FlutterProjectModel model) {
    myModel = model;
    myNewline = System.lineSeparator();
  }

  public void importModule() {
    String location = myModel.projectLocation().get() + "/" + myModel.projectName().get();
    VirtualFile moduleRoot = VfsUtil.findFileByIoFile(new File(location), true);
    assert (moduleRoot != null);
    assert (myModel.project().get().isPresent());
    Project androidProject = myModel.project().get().get();
    VirtualFile projectRoot = androidProject.getBaseDir();
    myRelativePath = VfsUtilCore.findRelativePath(projectRoot, moduleRoot, File.separatorChar);
    if (myRelativePath == null) {
      showHowToEditDialog();
      return;
    }

    String newPath = Paths.get(myRelativePath, ".android", "include_flutter.groovy").normalize().toString();
    if (!new File((newPath.startsWith("..") ? moduleRoot : moduleRoot.getParent()).getPath(), newPath).exists()) {
      showHowToEditDialog();
      return;
    }
    myRelativePath = StringUtil.escapeBackSlashes(newPath);
    VirtualFile settingsFile = projectRoot.findChild("settings.gradle");
    if (settingsFile == null) {
      showHowToEditDialog();
      return;
    }

    VirtualFile appDir = projectRoot.findChild("app");
    if (appDir == null) {
      showHowToEditDialog();
      return;
    }
    VirtualFile buildFile = appDir.findChild("build.gradle");
    if (buildFile == null) {
      showHowToEditDialog();
      return;
    }

    editSettingsFile(settingsFile);
    editBuildFile(buildFile);

    // Setup a default run configuration for 'main.dart' (if it's not there already and the file exists).
    PubRoot pubRoot = PubRoot.forDirectory(moduleRoot);
    assert (pubRoot != null);
    FlutterModuleUtils.autoCreateRunConfig(androidProject, pubRoot);
  }

  private void editSettingsFile(@NotNull VirtualFile settingsFile) {
    // TODO(messick) Rewrite this to use ProjectBuildModel similarly to editBuildFile().
    writer = new StringWriter();
    StringWriter settingsWriter;
    try {
      settingsWriter = editFileContent(settingsFile, this::writeSettingsLineWithEdits);
    }
    catch (IOException e) {
      showHowToEditDialog();
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        settingsFile.setBinaryContent(settingsWriter.toString().getBytes(StandardCharsets.UTF_8));
      }
      catch (IOException e) {
        // Should not happen
      }
    });
  }

  private void editBuildFile(@NotNull VirtualFile moduleDir) {
    Project project = myModel.project().getValue();
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(project);
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(moduleDir);
    AndroidModel android = buildModel.android();
    CompileOptionsModel options = android.compileOptions();
    LanguageLevelPropertyModel source = options.sourceCompatibility();
    source.setLanguageLevel(LanguageLevel.JDK_1_8);
    LanguageLevelPropertyModel target = options.targetCompatibility();
    target.setLanguageLevel(LanguageLevel.JDK_1_8);
    DependenciesModel deps = buildModel.dependencies();
    deps.addModule("implementation", ":flutter");
    runWriteCommandAction(project, "build", "import", buildModel::applyChanges);
  }

  private StringWriter editFileContent(VirtualFile file, Consumer<String> editFunction) throws IOException {
    writer = new StringWriter();
    try (BufferedReader r = Files.newBufferedReader(Paths.get(file.getPath()), StandardCharsets.UTF_8)) {
      hasFinishedEditing = false;
      r.lines().forEach(editFunction);
      writer.close();
    }
    return writer;
  }

  private void writeSettingsLineWithEdits(String line) {
    writeLineWithEdits(line, "include", this::writeSettingsModToBuffer);
  }

  private void writeLineWithEdits(String line, String trigger, Supplier editFunction) {
    if (hasFinishedEditing) {
      writeLineToBuffer(line);
    }
    else {
      if (line.trim().startsWith(trigger)) {
        writeLineToBuffer(line);
        hasFinishedEditing = (Boolean)editFunction.get();
      }
      else {
        writeLineToBuffer(line);
      }
    }
  }

  private boolean writeSettingsModToBuffer() {
    writeLineToBuffer("setBinding(new Binding([gradle: this]))");
    writeLineToBuffer("evaluate(new File(");
    writeLineToBuffer("  settingsDir,");
    writeLineToBuffer("  '" + myRelativePath + "'");
    writeLineToBuffer("))");
    return true;
  }

  private void writeLineToBuffer(String line) {
    writer.write(line);
    writer.write(myNewline);
  }

  private static void showHowToEditDialog() {
    ApplicationManager.getApplication().invokeLater(() -> new HowToEditDialog().show(), ModalityState.NON_MODAL);
  }

  private static class HowToEditDialog extends DialogWrapper {
    private JPanel myPanel;
    private JTextPane myTextPane;

    HowToEditDialog() {
      super(null, false, false);
      setTitle("Automatic Import Failed");
      myPanel = new JPanel();
      myTextPane = new JTextPane();
      Messages.installHyperlinkSupport(myTextPane);
      String howToEdit = "<html><body><p>The Gradle files could not be updated automatically." +
                         "<p>To use your Flutter module in this Android app you'll need to<br>" +
                         "edit the files as described in the <a href=\"" +
                         EDIT_INSTR_LINK +
                         "\">add2app documentation.</a></body></html>";
      myTextPane.setText(howToEdit);
      myPanel.add(myTextPane);
      init();
      //noinspection ConstantConditions
      getButton(getCancelAction()).setVisible(false);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }
  }
}
