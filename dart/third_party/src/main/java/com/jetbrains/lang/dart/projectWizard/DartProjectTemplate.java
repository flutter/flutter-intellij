// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.projectWizard;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.lang.dart.logging.PluginLogger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfiguration;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfigurationType;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunnerParameters;
import com.jetbrains.lang.dart.ide.runner.server.webdev.DartWebdevConfiguration;
import com.jetbrains.lang.dart.ide.runner.server.webdev.DartWebdevConfigurationType;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunConfiguration;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunConfigurationType;
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunnerParameters;
import com.jetbrains.lang.dart.projectWizard.DartCreate.DartCreateTemplate;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class DartProjectTemplate {

  private static final DartCreate DART_CREATE = new DartCreate();
  private static List<DartProjectTemplate> ourDartCreateTemplateCache;
  private static String ourDartCreateTemplateCacheSdkPath; //used to expire the above cache if the sdk is changed, an alternative would be to use the same cache method as com.jetbrains.lang.dart.sdk.DartSdkUtil.getSdkVersion

  private static final Logger LOG = PluginLogger.INSTANCE.createLogger(DartProjectTemplate.class);

  private final @NotNull @Nls String myName;
  private final @NotNull @Nls String myDescription;

  public DartProjectTemplate(@NotNull @Nls String name, @NotNull @Nls String description) {
    myName = name;
    myDescription = description;
  }

  public @NotNull @Nls String getName() {
    return myName;
  }

  public @NotNull @Nls String getDescription() {
    return myDescription;
  }

  public abstract Collection<VirtualFile> generateProject(@NotNull String sdkRoot,
                                                          @NotNull Module module,
                                                          @NotNull VirtualFile baseDir)
    throws IOException;


  /**
   * Must be called in pooled thread without read action; {@code templatesConsumer} will be invoked in EDT
   */
  public static void loadTemplatesAsync(@NotNull String sdkRoot, @NotNull Consumer<? super List<DartProjectTemplate>> templatesConsumer) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      LOG.error("DartProjectTemplate.loadTemplatesAsync() must be called in pooled thread without read action");
    }

    final List<DartProjectTemplate> templates = new ArrayList<>();
    try {
      templates.addAll(getDartCreateTemplates(sdkRoot));
    }
    finally {
      if (templates.isEmpty()) {
        templates.add(new CmdLineAppTemplate());
      }

      ApplicationManager.getApplication().invokeLater(() -> templatesConsumer.consume(templates), ModalityState.any());
    }
  }

  private static @NotNull List<DartProjectTemplate> getDartCreateTemplates(@NotNull String sdkRoot) {
    if (ourDartCreateTemplateCache != null && sdkRoot.equals(ourDartCreateTemplateCacheSdkPath)) {
      return ourDartCreateTemplateCache;
    }

    final List<DartCreateTemplate> templates = DART_CREATE.getAvailableTemplates(sdkRoot);

    ourDartCreateTemplateCache = new ArrayList<>();
    ourDartCreateTemplateCacheSdkPath = sdkRoot;
    for (DartCreateTemplate template : templates) {
      ourDartCreateTemplateCache.add(new DartCreateProjectTemplate(DART_CREATE, template));
    }
    return ourDartCreateTemplateCache;
  }

  static void createWebRunConfiguration(final @NotNull Module module, final @NotNull VirtualFile htmlFile) {
    DartModuleBuilder.runWhenNonModalIfModuleNotDisposed(() -> {
      final RunManager runManager = RunManager.getInstance(module.getProject());
      final RunnerAndConfigurationSettings settings = runManager.createConfiguration("", DartWebdevConfigurationType.class);

      DartWebdevConfiguration runConfiguration = (DartWebdevConfiguration)settings.getConfiguration();
      runConfiguration.getParameters().setHtmlFilePath(htmlFile.getPath());
      settings.setName(runConfiguration.suggestedName());

      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
    }, module);
  }

  static void createCmdLineRunConfiguration(final @NotNull Module module, final @NotNull VirtualFile mainDartFile) {
    DartModuleBuilder.runWhenNonModalIfModuleNotDisposed(() -> {
      final RunManager runManager = RunManager.getInstance(module.getProject());
      final RunnerAndConfigurationSettings settings = runManager.createConfiguration("", DartCommandLineRunConfigurationType.class);

      final DartCommandLineRunConfiguration runConfiguration = (DartCommandLineRunConfiguration)settings.getConfiguration();
      runConfiguration.getRunnerParameters().setFilePath(mainDartFile.getPath());
      runConfiguration.getRunnerParameters()
        .setWorkingDirectory(DartCommandLineRunnerParameters.suggestDartWorkingDir(module.getProject(), mainDartFile));

      settings.setName(runConfiguration.suggestedName());

      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
    }, module);
  }

  static void createTestRunConfiguration(@NotNull Module module, @NotNull @NonNls String baseDirPath) {
    DartModuleBuilder.runWhenNonModalIfModuleNotDisposed(() -> {
      final RunManager runManager = RunManager.getInstance(module.getProject());
      final RunnerAndConfigurationSettings settings = runManager.createConfiguration("", DartTestRunConfigurationType.class);

      final DartTestRunConfiguration runConfiguration = (DartTestRunConfiguration)settings.getConfiguration();
      runConfiguration.getRunnerParameters().setScope(DartTestRunnerParameters.Scope.FOLDER);
      runConfiguration.getRunnerParameters().setFilePath(baseDirPath);

      settings.setName(runConfiguration.suggestedName());

      runManager.addConfiguration(settings);
      runManager.setSelectedConfiguration(settings);
    }, module);
  }
}
