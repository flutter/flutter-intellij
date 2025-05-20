package io.flutter.project;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.module.FlutterProjectType;
import org.jetbrains.annotations.NotNull;

// Define a group of Flutter project types for use in Android Studio 4.2 and later.
// TODO (messick) DO NOT delete this during post-4.2 clean-up.
public abstract class FlutterModuleGroup extends FlutterModuleBuilder {

  abstract public FlutterProjectType getFlutterProjectType();

  abstract public String getDescription();

  @Override
  public String getBuilderId() {
    return super.getBuilderId() + getFlutterProjectType().arg;
  }

  @Override
  public ModuleWizardStep getCustomOptionsStep(final WizardContext context, final Disposable parentDisposable) {
    // This runs each time the project type selection changes.
    FlutterModuleWizardStep step = (FlutterModuleWizardStep)super.getCustomOptionsStep(context, parentDisposable);
    assert step!= null;
    getSettingsField().linkHelpForm(step.getHelpForm());
    setProjectTypeInSettings();
    return step;
  }

  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    // This runs when the Next button takes the wizard to the second page.
    ModuleWizardStep wizard = super.modifySettingsStep(settingsStep);
    setProjectTypeInSettings(); // TODO (messick) Remove this if possible (needs testing).
    return wizard;
  }

  // Using a parent group ensures there are no separators. The getWeight() in each subclass method controls sort order.
  @Override
  public String getParentGroup() {
    return "Flutter";
  }

  protected void setProjectTypeInSettings() {
    getSettingsField().updateProjectType(getFlutterProjectType());
  }

  public static class App extends FlutterModuleGroup {

    @NotNull
    public FlutterProjectType getFlutterProjectType() {
      return FlutterProjectType.APP;
    }

    @Override
    public String getPresentableName() {
      return FlutterBundle.message("module.wizard.app_title_short");
    }

    public String getDescription() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app");
    }

    public int getWeight() {
      return 4;
    }
  }

  public static class Mod extends FlutterModuleGroup {

    @NotNull
    public FlutterProjectType getFlutterProjectType() {
      return FlutterProjectType.MODULE;
    }

    @Override
    public String getPresentableName() {
      return FlutterBundle.message("module.wizard.module_title");
    }

    public String getDescription() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.module");
    }

    public int getWeight() {
      return 3;
    }
  }

  public static class Plugin extends FlutterModuleGroup {

    @NotNull
    public FlutterProjectType getFlutterProjectType() {
      return FlutterProjectType.PLUGIN;
    }

    @Override
    public String getPresentableName() {
      return FlutterBundle.message("module.wizard.plugin_title");
    }

    public String getDescription() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin");
    }

    public int getWeight() {
      return 2;
    }
  }

  public static class Package extends FlutterModuleGroup {

    @NotNull
    public FlutterProjectType getFlutterProjectType() {
      return FlutterProjectType.PACKAGE;
    }

    @Override
    public String getPresentableName() {
      return FlutterBundle.message("module.wizard.package_title");
    }

    public String getDescription() {
      return FlutterBundle.message("flutter.module.create.settings.help.project_type.description.package");
    }

    public int getWeight() {
      return 1;
    }
  }
}
