/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.project;

import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.cpp.ConfigureCppSupportStep;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.ui.ActivityGallery;
import com.android.tools.idea.npw.ui.WizardGallery;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * First page in the New Project wizard that allows user to select the Form Factor (Mobile, Wear, TV, etc) and its
 * Template ("Empty Activity", "Basic", "Nav Drawer", etc)
 * TODO: "No Activity" needs a Template Icon place holder
 */
public class ChooseAndroidProjectStep extends ModelWizardStep<NewProjectModel> {
  // To have the sequence specified by design, we hardcode the sequence.
  private static final String[] ORDERED_ACTIVITY_NAMES = {
    "Basic Activity", "Empty Activity", "Bottom Navigation Activity", "Fragment + ViewModel", "Fullscreen Activity", "Master/Detail Flow",
    "Navigation Drawer Activity", "Google Maps Activity", "Login Activity", "Scrolling Activity", "Tabbed Activity"
  };

  private final Supplier<List<FormFactorInfo>> myFormFactors =
          Suppliers.memoize(() -> createFormFactors(getTitle()));

  private JPanel myRootPanel;
  private CommonTabbedPane myTabsPanel;
  private JBLoadingPanel myLoadingPanel;
  private NewProjectModuleModel myNewProjectModuleModel;
  private final BoolValueProperty canGoForward = new BoolValueProperty();

  public ChooseAndroidProjectStep(@NotNull NewProjectModel model) {
    super(model, message("android.wizard.project.new.choose"));
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    myNewProjectModuleModel = new NewProjectModuleModel(getModel());
    RenderTemplateModel renderModel = myNewProjectModuleModel.getExtraRenderTemplateModel();

    return newArrayList(new ConfigureAndroidProjectStep(myNewProjectModuleModel, getModel()),
                        new ConfigureCppSupportStep(getModel()),
                        new ConfigureTemplateParametersStep(renderModel, message("android.wizard.config.activity.title"), newArrayList()));
  }

  private void createUIComponents() {
    myLoadingPanel = new JBLoadingPanel(
      new BorderLayout(), this,
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      @Override
      public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);

        // Work-around for IDEA-205343 issue.
        for (Component component : getComponents()) {
          component.setBounds(x, y, width, height);
        }
      }
    };
    myLoadingPanel.setLoadingText("Loading Android project template files");
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myLoadingPanel.startLoading();
    BackgroundTaskUtil.executeOnPooledThread(this, () -> {
      // Constructing FormFactors performs disk access and XML parsing, so let's do it in background
      // thread.
      final List<FormFactorInfo> formFactors = myFormFactors.get();

      // Update UI with the loaded formFactors. Switch back to UI thread.
      GuiUtils.invokeLaterIfNeeded(() -> updateUi(wizard, formFactors), ModalityState.any());
    });
  }

  /**
   * Updates UI with a given form factors. This method must be executed on event dispatch thread.
   */
  private void updateUi(@NotNull ModelWizard.Facade wizard,
                        @NotNull List<FormFactorInfo> formFactors) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    for (FormFactorInfo formFactorInfo : formFactors) {
      ChooseAndroidProjectPanel<TemplateRenderer> tabPanel = formFactorInfo.tabPanel;
      myTabsPanel.addTab(formFactorInfo.formFactor.toString(), tabPanel.myRootPanel);

      tabPanel.myGallery.setDefaultAction(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          wizard.goForward();
        }
      });

      ListSelectionListener activitySelectedListener = selectionEvent -> {
        TemplateRenderer selectedTemplate = tabPanel.myGallery.getSelectedElement();
        if (selectedTemplate != null) {
          tabPanel.myTemplateName.setText(selectedTemplate.getImageLabel());
          tabPanel.myTemplateDesc.setText("<html>" + selectedTemplate.getTemplateDescription() + "</html>");
          tabPanel.myDocumentationLink.setVisible(selectedTemplate.isCppTemplate());
        }
      };

      tabPanel.myGallery.addListSelectionListener(activitySelectedListener);
      activitySelectedListener.valueChanged(null);
    }

    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
    myLoadingPanel.stopLoading();
    canGoForward.set(Boolean.TRUE);
  }

  @Override
  protected void onProceeding() {
    FormFactorInfo formFactorInfo = myFormFactors.get().get(myTabsPanel.getSelectedIndex());
    TemplateRenderer selectedTemplate = formFactorInfo.tabPanel.myGallery.getSelectedElement();

    getModel().enableCppSupport().set(selectedTemplate.isCppTemplate());
    myNewProjectModuleModel.formFactor().set(formFactorInfo.formFactor);
    myNewProjectModuleModel.moduleTemplateFile().setNullableValue(formFactorInfo.templateFile);
    myNewProjectModuleModel.renderTemplateHandle().setNullableValue(selectedTemplate.getTemplate());

    TemplateHandle extraStepTemplateHandle = formFactorInfo.formFactor == FormFactor.THINGS ? selectedTemplate.getTemplate() : null;
    myNewProjectModuleModel.getExtraRenderTemplateModel().setTemplateHandle(extraStepTemplateHandle);
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return canGoForward;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myTabsPanel;
  }

  @NotNull
  private static List<FormFactorInfo> createFormFactors(@NotNull String wizardTitle) {
    Map<FormFactor, FormFactorInfo> formFactorInfoMap = Maps.newTreeMap();
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(CATEGORY_APPLICATION);

    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
      if (formFactor == FormFactor.GLASS && !AndroidSdkUtils.isGlassInstalled()) {
        // Only show Glass if you've already installed the SDK
        continue;
      }
      FormFactorInfo prevFormFactorInfo = formFactorInfoMap.get(formFactor);
      int templateMinSdk = metadata.getMinSdk();

      if (prevFormFactorInfo == null) {
        int minSdk = Math.max(templateMinSdk, formFactor.getMinOfflineApiLevel());
        ChooseAndroidProjectPanel<TemplateRenderer> tabPanel = new ChooseAndroidProjectPanel<>(createGallery(wizardTitle, formFactor));
        formFactorInfoMap.put(formFactor, new FormFactorInfo(templateFile, formFactor, minSdk, tabPanel));
      }
      else if (templateMinSdk > prevFormFactorInfo.minSdk) {
        prevFormFactorInfo.minSdk = templateMinSdk;
        prevFormFactorInfo.templateFile = templateFile;
      }
    }

    return formFactorInfoMap.values().stream().sorted(Comparator.comparing(f -> f.formFactor)).collect(toList());
  }

  @NotNull
  private static List<TemplateHandle> getFilteredTemplateHandles(@NotNull FormFactor formFactor) {
    List<TemplateHandle> templateHandles = TemplateManager.getInstance().getTemplateList(formFactor);

    if (formFactor == FormFactor.MOBILE) {
      Map<String, TemplateHandle> entryMap = templateHandles.stream().collect(toMap(it -> it.getMetadata().getTitle(), it -> it));
      return Arrays.stream(ORDERED_ACTIVITY_NAMES).map(it -> entryMap.get(it)).filter(Objects::nonNull).collect(toList());
    }

    return templateHandles;
  }

  @NotNull
  private static ASGallery<TemplateRenderer> createGallery(@NotNull String title, @NotNull FormFactor formFactor) {
    List<TemplateHandle> templateHandles = getFilteredTemplateHandles(formFactor);

    List<TemplateRenderer> templateRenderers = Lists.newArrayListWithExpectedSize(templateHandles.size() + 2);
    templateRenderers.add(new TemplateRenderer(null, false)); // "Add No Activity" entry
    for (TemplateHandle templateHandle : templateHandles) {
      templateRenderers.add(new TemplateRenderer(templateHandle, false));
    }

    if (formFactor == FormFactor.MOBILE) {
      templateRenderers.add(new TemplateRenderer(null, true)); // "Native C++" entry
    }

    TemplateRenderer[] listItems = templateRenderers.toArray(new TemplateRenderer[0]);

    ASGallery<TemplateRenderer> gallery = new WizardGallery<>(title, TemplateRenderer::getImage, TemplateRenderer::getImageLabel);
    gallery.setModel(JBList.createDefaultListModel((Object[])listItems));
    gallery.setSelectedIndex(getDefaultSelectedTemplateIndex(listItems));

    return gallery;
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateRenderer[] templateRenderers) {
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getImageLabel().equals("Empty Activity")) {
        return i;
      }
    }

    // Default template not found. Instead, return the index to the first valid template renderer (e.g. skip "Add No Activity", etc.)
    for (int i = 0; i < templateRenderers.length; i++) {
      if (templateRenderers[i].getTemplate() != null) {
        return i;
      }
    }

    assert false : "No valid Template found";
    return 0;
  }

  private static class FormFactorInfo {
    @NotNull final FormFactor formFactor;
    @NotNull final ChooseAndroidProjectPanel<TemplateRenderer> tabPanel;
    @NotNull File templateFile;
    int minSdk;

    FormFactorInfo(@NotNull File templateFile, @NotNull FormFactor formFactor, int minSdk,
                   @NotNull ChooseAndroidProjectPanel<TemplateRenderer> tabPanel) {

      this.templateFile = templateFile;
      this.formFactor = formFactor;
      this.minSdk = minSdk;
      this.tabPanel = tabPanel;
    }
  }

  private static class TemplateRenderer {
    @Nullable private final TemplateHandle myTemplate;
    private final boolean myIsCppTemplate;

    TemplateRenderer(@Nullable TemplateHandle template, boolean isCppTemplate) {
      this.myTemplate = template;
      this.myIsCppTemplate = isCppTemplate;
    }

    @Nullable
    TemplateHandle getTemplate() {
      return myTemplate;
    }

    boolean isCppTemplate() {
      return myIsCppTemplate;
    }

    @NotNull
    String getImageLabel() {
      return ActivityGallery.getTemplateImageLabel(myTemplate, isCppTemplate());
    }

    @NotNull
    String getTemplateDescription() {
      return ActivityGallery.getTemplateDescription(myTemplate, isCppTemplate());
    }

    @NotNull
    @Override
    public String toString() {
      return getImageLabel();
    }

    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    @Nullable
    Image getImage() {
      return ActivityGallery.getTemplateImage(myTemplate, isCppTemplate());
    }
  }
}
