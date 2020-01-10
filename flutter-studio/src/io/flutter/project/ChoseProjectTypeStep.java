/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashMap;
import io.flutter.module.FlutterDescriptionProvider;
import io.flutter.module.FlutterDescriptionProvider.FlutterGalleryEntry;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChoseProjectTypeStep extends ModelWizardStep<FlutterProjectModel> {
  private static final Logger LOG = Logger.getInstance(ChoseProjectTypeStep.class);

  private final Collection<ModuleGalleryEntry> myModuleGalleryEntryList;
  private final JComponent myRootPanel;
  private JLabel[] helpLabels;

  private ASGallery<ModuleGalleryEntry> myProjectTypeGallery;
  private Map<ModuleGalleryEntry, FlutterProjectStep> myModuleDescriptionToStepMap;

  public ChoseProjectTypeStep(@NotNull FlutterProjectModel model) {
    this(model, FlutterDescriptionProvider.getGalleryList(true));
  }

  private ChoseProjectTypeStep(@NotNull FlutterProjectModel model, @NotNull Collection<ModuleGalleryEntry> moduleGalleryEntries) {
    super(model, "New Flutter Project");

    myModuleGalleryEntryList = moduleGalleryEntries;
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new VerticalFlowLayout());
    myRootPanel.add(createGallery());

    JPanel section = new JPanel();
    section.setLayout(new VerticalFlowLayout());
    helpLabels = new JLabel[moduleGalleryEntries.size()];
    int idx = 0;
    for (ModuleGalleryEntry entry : moduleGalleryEntries) {
      helpLabels[idx] = new JLabel(((FlutterGalleryEntry)entry).getHelpText());
      helpLabels[idx].setEnabled(false);
      section.add(helpLabels[idx++]);
    }

    myProjectTypeGallery.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      for (JLabel label : helpLabels) {
        label.setEnabled(false);
      }
      if (myProjectTypeGallery.getSelectedIndex() >= 0) {
        helpLabels[myProjectTypeGallery.getSelectedIndex()].setEnabled(true);
      }
    });

    myRootPanel.add(section);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();
    myModuleDescriptionToStepMap = new HashMap<>();
    for (ModuleGalleryEntry moduleGalleryEntry : myModuleGalleryEntryList) {
      FlutterProjectStep step = ((FlutterGalleryEntry)moduleGalleryEntry).createFlutterStep(getModel());
      allSteps.add(step);
      myModuleDescriptionToStepMap.put(moduleGalleryEntry, step);
    }

    return allSteps;
  }

  @NotNull
  private JComponent createGallery() {
    myProjectTypeGallery = new ASGallery<ModuleGalleryEntry>(
      JBList.createDefaultListModel(),
      image -> image == null ? null : image.getIcon() == null ? null : image.getIcon(),
      label -> label == null ? message("android.wizard.gallery.item.none") : label.getName(), DEFAULT_GALLERY_THUMBNAIL_SIZE,
      null
    ) {

      @Override
      public Dimension getPreferredScrollableViewportSize() {
        // The default implementation assigns a height as tall as the screen.
        // When calling setVisibleRowCount(2), the underlying implementation is buggy, and  will have a gap on the right and when the user
        // resizes, it enters on an adjustment loop at some widths (can't decide to fit 3 or for elements, and loops between the two)
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 5 + widthInsets, (int)(cellSize.height * 1.2) + heightInsets);
      }
    };

    myProjectTypeGallery.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    AccessibleContext accessibleContext = myProjectTypeGallery.getAccessibleContext();
    if (accessibleContext != null) {
      accessibleContext.setAccessibleDescription(getTitle());
    }
    return new JBScrollPane(myProjectTypeGallery);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myProjectTypeGallery.setModel(JBList.createDefaultListModel(myModuleGalleryEntryList.toArray()));
    myProjectTypeGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myProjectTypeGallery.setSelectedIndex(0);
  }

  @Override
  protected void onProceeding() {
    // This wizard includes a step for each module, but we only visit the selected one. First, we hide all steps (in case we visited a
    // different module before and hit back), and then we activate the step we care about.
    ModuleGalleryEntry selectedEntry = myProjectTypeGallery.getSelectedElement();
    myModuleDescriptionToStepMap.forEach((galleryEntry, step) -> step.setShouldShow(galleryEntry == selectedEntry));
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myProjectTypeGallery;
  }

  @NotNull
  private static List<ModuleGalleryEntry> getGalleryList() {
    return new ArrayList<>();
  }
}
