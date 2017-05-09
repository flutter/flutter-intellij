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
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.FlutterModuleUtils;
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

    final FlutterSdk sdk = FlutterSdk.forPath(flutterSdkPath);
    if (sdk == null) {
      FlutterMessages.showError("Error creating project", flutterSdkPath + " is not a valid Flutter SDK");
      return;
    }

    // Run "flutter create".
    final PubRoot root = sdk.createFiles(baseDir, module);
    if (root == null) {
      FlutterMessages.showError("Error creating project", "Failed to run flutter create.");
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {

      // Set up Dart SDK.
      final String dartSdkPath = sdk.getDartSdkPath();
      if (dartSdkPath == null) {
        FlutterMessages.showError("Error creating project", "unable to get Dart SDK"); // shouldn't happen; we just created it.
        return;
      }
      DartPlugin.ensureDartSdkConfigured(project, sdk.getDartSdkPath());
      DartPlugin.enableDartSdk(module);
      FlutterSdkUtil.updateKnownSdkPaths(sdk.getHomePath());

      // Set up module.
      final ModifiableRootModel modifiableModel = ModifiableModelsProvider.SERVICE.getInstance().getModuleModifiableModel(module);
      modifiableModel.addContentEntry(root.getRoot());
      ModifiableModelsProvider.SERVICE.getInstance().commitModuleModifiableModel(modifiableModel);

      FlutterModuleUtils.autoShowMain(project, root);
    });
  }
}
