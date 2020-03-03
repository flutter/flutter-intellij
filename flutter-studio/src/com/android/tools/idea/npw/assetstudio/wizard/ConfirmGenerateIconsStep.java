/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import static com.android.tools.idea.npw.assetstudio.IconGenerator.getResDirectory;
import static com.android.tools.idea.npw.assetstudio.IconGenerator.pathToDensity;

import com.android.resources.Density;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.ProportionalImageScaler;
import com.android.tools.idea.npw.assetstudio.ui.ProposedFileTreeCellRenderer;
import com.android.tools.idea.npw.assetstudio.ui.ProposedFileTreeModel;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;

/**
 * This step allows the user to select a build variant and provides a preview of the assets that
 * are about to be created.
 */
public final class ConfirmGenerateIconsStep extends ModelWizardStep<GenerateIconsModel>
    implements PersistentStateComponent<PersistentState> {
  /** Limit the size of icons in the preview tree so that the tree doesn't look unnatural. */
  private static final int MAX_ICON_HEIGHT = 24;

  private static final String CONFIRMATION_STEP_PROPERTY = "confirmationStep";
  private static final String RESOURCE_DIRECTORY_PROPERTY = "resourceDirectory";

  private final List<NamedModuleTemplate> myTemplates;
  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  private JComboBox<NamedModuleTemplate> myPathsComboBox;
  private Tree myOutputPreviewTree;

  private ObjectProperty<NamedModuleTemplate> mySelectedTemplate;
  private BoolProperty myFilesAlreadyExist = new BoolValueProperty();

  public ConfirmGenerateIconsStep(@NotNull GenerateIconsModel model, @NotNull List<NamedModuleTemplate> templates) {
    super(model, "Confirm Icon Path");
    Preconditions.checkArgument(!templates.isEmpty());
    myTemplates = templates;
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    DefaultComboBoxModel<NamedModuleTemplate> moduleTemplatesModel = new DefaultComboBoxModel<>();
    for (NamedModuleTemplate template : templates) {
      moduleTemplatesModel.addElement(template);
    }
    myPathsComboBox.setRenderer(new ListCellRendererWrapper<NamedModuleTemplate>() {
      @Override
      public void customize(JList list, NamedModuleTemplate template, int index, boolean selected, boolean hasFocus) {
        setText(template.getName());
      }
    });
    myPathsComboBox.setModel(moduleTemplatesModel);

    DefaultTreeModel emptyModel = new DefaultTreeModel(null);
    myOutputPreviewTree.setModel(emptyModel);
    myOutputPreviewTree.setCellRenderer(new ProposedFileTreeCellRenderer());
    myOutputPreviewTree.setBorder(BorderFactory.createLineBorder(UIUtil.getBoundsColor()));
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
    myOutputPreviewTree.getEmptyText().setText("No resource folder defined in project");

    String alreadyExistsError = WizardUtils.toHtmlString(
        "Some existing files will be overwritten by this operation.<br>" +
        "Files which replace existing files are marked red in the preview above.");
    myValidatorPanel.registerValidator(myFilesAlreadyExist, new FalseValidator(Validator.Severity.WARNING, alreadyExistsError));
  }

  @Override
  @NotNull
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    mySelectedTemplate = ObjectProperty.wrap(new SelectedItemProperty<>(myPathsComboBox));

    PersistentStateUtil.load(this, getModel().getPersistentState().getChild(CONFIRMATION_STEP_PROPERTY));
  }

  @Override
  public void onWizardFinished() {
    getModel().getPersistentState().setChild(CONFIRMATION_STEP_PROPERTY, getState());
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    NamedModuleTemplate moduleTemplate = mySelectedTemplate.get();
    state.set(RESOURCE_DIRECTORY_PROPERTY, moduleTemplate.getName(), myTemplates.get(0).getName());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    String templateName = state.get(RESOURCE_DIRECTORY_PROPERTY);
    if (templateName != null) {
      for (NamedModuleTemplate template : myTemplates) {
        if (template.getName().equals(templateName)) {
          mySelectedTemplate.set(template);
          break;
        }
      }
    }
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setPaths(mySelectedTemplate.get().getPaths());
  }

  @Override
  protected void onEntering() {
    myListeners.release(mySelectedTemplate); // Just in case we're entering this step a second time
    myListeners.receiveAndFire(mySelectedTemplate, template -> {
      IconGenerator iconGenerator = getModel().getIconGenerator();
      File resDirectory = getResDirectory(template.getPaths());
      if (iconGenerator == null || resDirectory == null || resDirectory.getParentFile() == null) {
        return;
      }

      Map<File, BufferedImage> pathToUnscaledImage = iconGenerator.generateIntoFileMap(resDirectory);

      Map<File, Icon> pathToIcon = Maps.newTreeMap((file1, file2) -> {
        String path1 = file1.getAbsolutePath();
        String path2 = file2.getAbsolutePath();
        Density density1 = pathToDensity(path1);
        Density density2 = pathToDensity(path2);

        int cmp = Boolean.compare(density1 != null, density2 != null);
        if (cmp != 0) {
          return cmp;
        }

        if (density1 != null && density2 != null && density1 != density2) {
          return Integer.compare(density2.getDpiValue(), density1.getDpiValue()); // Sort least dense to most dense.
        }

        BufferedImage image1 = pathToUnscaledImage.get(file1);
        BufferedImage image2 = pathToUnscaledImage.get(file2);
        cmp = Integer.compare(image2.getHeight(), image1.getHeight());
        // If heights are the same, use path as a tie breaker.
        return cmp != 0 ? cmp : path2.compareTo(path1);
      });

      // By default, icons grow exponentially, and if presented at scale, may take up way too
      // much real estate. Instead, let's scale down all icons proportionally so the largest
      // one fits in our maximum allowed space.
      ProportionalImageScaler imageScaler = ProportionalImageScaler.forImages(pathToUnscaledImage.values());

      for (Map.Entry<File, BufferedImage> entry: pathToUnscaledImage.entrySet()) {
        Image image = imageScaler.scale(entry.getValue(), MAX_ICON_HEIGHT);
        pathToIcon.put(entry.getKey(), new ImageIcon(image));
      }

      ProposedFileTreeModel treeModel = new ProposedFileTreeModel(resDirectory.getParentFile(), pathToIcon);

      myFilesAlreadyExist.set(treeModel.hasConflicts());
      myOutputPreviewTree.setModel(treeModel);

      // The tree should be totally expanded by default.
      for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
        myOutputPreviewTree.expandRow(i);
      }
    });
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }
}
