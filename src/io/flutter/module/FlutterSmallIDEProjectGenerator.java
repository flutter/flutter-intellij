package io.flutter.module;

import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterSmallIDEProjectGenerator extends WebProjectTemplate<String> {
  @NotNull
  @Override
  public String getName() {
    return FlutterBundle.message("flutter.title");
  }

  @Override
  public String getDescription() {
    return FlutterBundle.message("flutter.project.description");
  }

  @NotNull
  @Override
  public GeneratorPeer<String> createPeer() {
    return new FlutterSmallIDEGeneratorPeer();
  }

  @Override
  public Icon getLogo() {
    return FlutterIcons.Flutter;
  }

  @Override
  public void generateProject(@NotNull Project project,
                              @NotNull VirtualFile baseDir,
                              @NotNull String flutterSdkPath,
                              @NotNull Module module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final ModifiableRootModel modifiableModel = ModifiableModelsProvider.SERVICE.getInstance().getModuleModifiableModel(module);
        FlutterModuleBuilder.setupSmallProject(project, modifiableModel, baseDir, flutterSdkPath);
        ModifiableModelsProvider.SERVICE.getInstance().commitModuleModifiableModel(modifiableModel);
      }
      catch (ConfigurationException e) {
        FlutterMessages.showError("Error creating project", e.getMessage());
      }
    });
  }
}
