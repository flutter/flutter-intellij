package io.flutter.module;

import com.intellij.execution.OutputListener;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
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

// TODO(devoncarew): It would be nice to show a hyperlink in the upper right of this wizard.
// https://youtrack.jetbrains.com/issue/WEB-27537

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
    final OutputListener listener = new OutputListener();
    final PubRoot root = sdk.createFiles(baseDir, module, listener);
    if (root == null) {
      final String stderr = listener.getOutput().getStderr();
      final String msg = stderr.isEmpty() ? "Flutter create command was unsuccessful" : stderr;
      FlutterMessages.showError("Error creating project", msg);
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
