/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.projectWizard.ProjectCategory;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilderFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeEP;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleTypeManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.OffsetIcon;
import com.intellij.util.ReflectionUtil;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.module.FlutterModuleGroup;
import io.flutter.module.FlutterProjectType;
import io.flutter.project.ChoseProjectTypeStep;
import io.flutter.project.FlutterProjectModel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class FlutterNewProjectAction extends AnAction implements DumbAware {

  ModuleBuilderFactory[] originalModuleBuilders;
  ProjectCategory[] originalCategories;
  List<ModuleType<?>> originalTypes;
  ModuleType<?>[] originalModuleTypes;
  LinkedHashMap<ModuleType<?>, Boolean> originalModuleMap;

  public FlutterNewProjectAction() {
    super(FlutterBundle.message("action.new.project.title"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(getFlutterDecoratedIcon());
      e.getPresentation().setText(
        Registry.is("use.tabbed.welcome.screen", false)
        ? FlutterBundle.message("welcome.new.project.compact")
        : FlutterBundle.message("welcome.new.project.title"));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!FlutterUtils.isNewAndroidStudioProjectWizard()) {
      FlutterProjectModel model = new FlutterProjectModel(FlutterProjectType.APP);
      try {
        ModelWizard wizard = new ModelWizard.Builder()
          .addStep(new ChoseProjectTypeStep(model))
          .build();
        StudioWizardDialogBuilder builder = new StudioWizardDialogBuilder(wizard, "Create New Flutter Project");
        ModelWizardDialog dialog = builder.build();
        try {
          dialog.show();
        }
        catch (NoSuchElementException ex) {
          // This happens if no Flutter SDK is installed and the user cancels the FlutterProjectStep.
        }
      }
      catch (NoSuchMethodError x) {
        Messages.showMessageDialog("Android Studio canary is not supported", "Unsupported IDE", Messages.getErrorIcon());
      }
    }
    else {
      NewProjectWizard wizard;
      replaceModuleBuilders();
      try {
        wizard = new NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null);
      }
      finally {
        restoreModuleBuilders();
      }
      NewProjectUtil.createNewProject(wizard);
    }
  }

  @NotNull
  Icon getFlutterDecoratedIcon() {
    Icon icon = AllIcons.Welcome.CreateNewProject;
    Icon badgeIcon = new OffsetIcon(0, FlutterIcons.Flutter_badge).scale(0.666f);

    LayeredIcon decorated = new LayeredIcon(2);
    decorated.setIcon(badgeIcon, 0, 7, 7);
    decorated.setIcon(icon, 1, 0, 0);
    return decorated;
  }

  @SuppressWarnings("UnstableApiUsage")
  private void replaceModuleBuilders() {
    ModuleBuilder.EP_NAME.getExtensions();
    ProjectCategory.EXTENSION_POINT_NAME.getExtensions();
    ModuleTypeManagerImpl.EP_NAME.getExtensionList();

    ExtensionPointImpl<ModuleBuilderFactory> factoryExtensionPoint =
      (ExtensionPointImpl<ModuleBuilderFactory>)ModuleBuilder.EP_NAME.getPoint();
    Field arrayField = ReflectionUtil.getDeclaredField(ExtensionPointImpl.class, "myExtensionsCacheAsArray");
    assert arrayField != null;
    ModuleBuilderFactory[] originalArray = ReflectionUtil.getFieldValue(arrayField, factoryExtensionPoint);
    assert originalArray != null;
    originalModuleBuilders = originalArray;

    ExtensionPointImpl<ModuleTypeEP> typeExtensionPoint =
      (ExtensionPointImpl<ModuleTypeEP>)ModuleTypeManagerImpl.EP_NAME.getPoint();
    Field listField = ReflectionUtil.getDeclaredField(ExtensionPointImpl.class, "myExtensionsCache");
    assert listField != null;
    List<ModuleType<?>> originalList = ReflectionUtil.getFieldValue(listField, typeExtensionPoint);
    assert originalList != null;
    originalTypes = originalList;

    ExtensionPointImpl<ProjectCategory> categoryExtensionPoint =
      (ExtensionPointImpl<ProjectCategory>)ProjectCategory.EXTENSION_POINT_NAME.getPoint();
    Field categoryField = ReflectionUtil.getDeclaredField(ExtensionPointImpl.class, "myExtensionsCacheAsArray");
    assert categoryField != null;
    ProjectCategory[] categories = ReflectionUtil.getFieldValue(categoryField, categoryExtensionPoint);
    assert categories != null;
    originalCategories = categories;

    Field mapField = ReflectionUtil.getDeclaredField(ModuleTypeManagerImpl.class, "myModuleTypes");
    assert mapField != null;
    LinkedHashMap<ModuleType<?>, Boolean> originalMap = ReflectionUtil.getFieldValue(mapField, ModuleTypeManager.getInstance());
    assert originalMap != null;
    originalModuleMap = originalMap;

    ModuleTypeManager typeManager = ModuleTypeManager.getInstance();
    originalModuleTypes = typeManager.getRegisteredTypes();
    for (ModuleType<?> type : originalModuleTypes) {
      typeManager.unregisterModuleType(type);
    }

    try {
      arrayField.set(factoryExtensionPoint, getFlutterModuleBuilders());
      listField.set(typeExtensionPoint, new ArrayList<ModuleType<?>>());
      categoryField.set(categoryExtensionPoint, new ProjectCategory[0]);
      mapField.set(ModuleTypeManager.getInstance(), new LinkedHashMap<ModuleType<?>, Boolean>());
    }
    catch (IllegalAccessException e) {
      // not reached
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  private void restoreModuleBuilders() {
    ExtensionPointImpl<ModuleBuilderFactory> factoryExtensionPoint =
      (ExtensionPointImpl<ModuleBuilderFactory>)ModuleBuilder.EP_NAME.getPoint();
    Field arrayField = ReflectionUtil.getDeclaredField(ExtensionPointImpl.class, "myExtensionsCacheAsArray");
    assert arrayField != null;

    ExtensionPointImpl<ModuleTypeEP> typeExtensionPoint =
      (ExtensionPointImpl<ModuleTypeEP>)ModuleTypeManagerImpl.EP_NAME.getPoint();
    Field listField = ReflectionUtil.getDeclaredField(ExtensionPointImpl.class, "myExtensionsCache");
    assert listField != null;

    ExtensionPointImpl<ProjectCategory> categoryExtensionPoint =
      (ExtensionPointImpl<ProjectCategory>)ProjectCategory.EXTENSION_POINT_NAME.getPoint();
    Field categoryField = ReflectionUtil.getDeclaredField(ExtensionPointImpl.class, "myExtensionsCacheAsArray");
    assert categoryField != null;

    Field mapField = ReflectionUtil.getDeclaredField(ModuleTypeManagerImpl.class, "myModuleTypes");
    assert mapField != null;

    assert originalModuleBuilders != null;
    assert originalTypes != null;
    assert originalModuleTypes != null;
    assert originalModuleMap != null;

    try {
      arrayField.set(factoryExtensionPoint, originalModuleBuilders);
      listField.set(typeExtensionPoint, originalTypes);
      categoryField.set(categoryExtensionPoint, originalCategories);
      mapField.set(ModuleTypeManager.getInstance(), originalModuleMap);
    }
    catch (IllegalAccessException e) {
      // not reached
    }

    ModuleTypeManager typeManager = ModuleTypeManager.getInstance();
    for (ModuleType<?> type : originalModuleTypes) {
      typeManager.registerModuleType(type);
    }

    originalModuleBuilders = null;
    originalTypes = null;
    originalModuleTypes = null;
    originalModuleMap = null;
  }

  @NotNull
  private ModuleBuilderFactory[] getFlutterModuleBuilders() {
    // The order is not relevant. The builders are sorted by weight.
    return new ModuleBuilderFactory[]{
      w(new FlutterModuleGroup.App()),
      w(new FlutterModuleGroup.Mod()),
      w(new FlutterModuleGroup.Plugin()),
      w(new FlutterModuleGroup.Package()),
    };
  }

  @NotNull
  private ModuleBuilderFactory w(@NotNull final FlutterModuleBuilder b) {
    // For some unknown reason our FlutterModuleGroup classes are not in the class loader used by default.
    // Unless the IDE is being run by the debugger.
    // Instead of letting the factory fail to find the class just return the instance.
    ModuleBuilderFactory f = new ModuleBuilderFactory() {
      @Override
      public ModuleBuilder createBuilder() {
        return b;
      }
    };
    f.builderClass = b.getClass().getName();
    return f;
  }
}
