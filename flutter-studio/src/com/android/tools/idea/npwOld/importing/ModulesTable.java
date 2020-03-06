/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npwOld.importing;

import com.android.tools.idea.gradle.project.ModuleToImport;
import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.Collator;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Table for showing a list of modules that will be imported.
 */
public final class ModulesTable extends JBPanel implements Scrollable {
  public static final String PROPERTY_SELECTED_MODULES = "selectedModules";
  private final Map<VirtualFile, ModuleImportSettingsPane> panes = Maps.newHashMap();
  private ModuleImportSettings myPrimaryModuleSettings;
  private JComponent myDependenciesLabel;
  private ModuleListModel myListModel;
  private boolean isRefreshing = false;

  private static GridConstraints createGridConstraints(int row, boolean growVertically) {
    GridConstraints constraints = new GridConstraints();
    constraints.setRow(row);
    constraints.setFill(GridConstraints.FILL_HORIZONTAL);
    constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
    if (growVertically) {
      constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW);
    }
    return constraints;
  }

  @Nullable
  private ModuleImportSettingsPane createPanel(final ModuleToImport module, boolean isFirst) {
    VirtualFile location = module.location;
    if (location != null) {
      final ModuleImportSettingsPane pane = panes.get(location);
      if (pane != null) {
        return pane;
      }
      else {
        final ModuleImportSettingsPane newPane = new ModuleImportSettingsPane();
        if (!isFirst) {
          newPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtil.getBorderColor()));
        }
        newPane.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updateModule(newPane, module);
          }
        });
        panes.put(location, newPane);
        return newPane;
      }
    }
    else {
      return null;
    }
  }

  private void refreshModulesList() {
    removeAll();
    Collection<ModuleImportSettingsPane> panes = updateModuleEditors();
    if (!panes.isEmpty()) {
      setLayout(new GridLayoutManager(panes.size(), 1));
      int row = 0;
      for (ModuleImportSettingsPane pane : panes) {
        add(pane, createGridConstraints(row++, false));
      }
    }
    invalidate();
  }

  private Collection<ModuleImportSettingsPane> updateModuleEditors() {
    isRefreshing = true;
    try {
      ModuleToImport primary = myListModel.getPrimary();
      setModuleNameVisibility(primary != null, myListModel.getAllModules().size() > 1);
      if (primary != null) {
        apply(myPrimaryModuleSettings, primary);
      }

      boolean isFirst = true;
      Collection<ModuleImportSettingsPane> editors = Lists.newLinkedList();

      Set<ModuleToImport> allModules = Sets.newTreeSet(new ModuleComparator(myListModel.getCurrentPath()));
      Iterables
        .addAll(allModules, Iterables.filter(myListModel.getAllModules(), Predicates.not(Predicates.equalTo(myListModel.getPrimary()))));

      for (final ModuleToImport module : allModules) {
        final ModuleImportSettingsPane pane = createModuleSetupPanel(module, isFirst);
        if (pane != null) {
          isFirst = false;
          editors.add(pane);
        }
      }
      return editors;
    }
    finally {
      isRefreshing = false;
    }
  }

  private void updateModule(ModuleImportSettings pane, @Nullable ModuleToImport module) {
    if (!isRefreshing && module != null) {
      boolean isSelected = pane.isModuleSelected();
      if (myListModel.isSelected(module) != isSelected) {
        myListModel.setSelected(module, isSelected);
      }
      String moduleName = pane.getModuleName();
      if (!Objects.equal(myListModel.getModuleName(module), moduleName)) {
        myListModel.setModuleName(module, moduleName);
      }
      updateModuleEditors();
    }
    firePropertyChange(PROPERTY_SELECTED_MODULES, null, null);
  }

  private void setModuleNameVisibility(boolean visible, boolean hasMoreDependencies) {
    myPrimaryModuleSettings.setVisible(visible);
    myDependenciesLabel.setVisible(visible && hasMoreDependencies);
  }

  @Nullable
  private ModuleImportSettingsPane createModuleSetupPanel(ModuleToImport module, boolean isFirst) {
    final ModuleImportSettingsPane pane = createPanel(module, isFirst);
    if (pane != null) {
      apply(pane, module);
    }
    return pane;
  }

  private void apply(ModuleImportSettings pane, ModuleToImport module) {
    pane.setModuleName(myListModel.getModuleName(module));
    pane.setModuleSourcePath(ImportUIUtil.getRelativePath(myListModel.getCurrentPath(), module.location));
    pane.setModuleSelected(myListModel.isSelected(module));
    pane.setCanToggleModuleSelection(myListModel.canToggleModuleSelection(module));
    pane.setCanRenameModule(myListModel.canRename(module));
    pane.setValidationStatus(myListModel.getStatusSeverity(module), myListModel.getStatusDescription(module));
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return orientation == SwingConstants.VERTICAL && getComponentCount() >= 2
           ? getComponent(1).getHeight() + AbstractLayout.DEFAULT_VGAP
           : 10;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return getHeight();
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  public Set<ModuleToImport> getSelectedModules() {
    return myListModel.getSelectedModules();
  }

  public Map<String, VirtualFile> getSelectedModulesMap() {
    final Collection<ModuleToImport> modules = myListModel.getSelectedModules();
    if (modules.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, VirtualFile> selectedModules = Maps.newHashMap();
    for (ModuleToImport module : modules) {
      selectedModules.put(myListModel.getModuleName(module), module.location);
    }
    return selectedModules;
  }

  public void setModules(@Nullable Project project, @Nullable VirtualFile currentPath, @Nullable Iterable<ModuleToImport> modules) {
    if (myListModel == null) {
      myListModel = new ModuleListModel(project);
    }
    myListModel.setContents(currentPath, modules == null ? ImmutableSet.of() : modules);
    refreshModulesList();
  }

  public void bindPrimaryModuleEntryComponents(final ModuleImportSettings primaryModulePane, JComponent shownIfThereAreDependencies) {
    myPrimaryModuleSettings = primaryModulePane;
    myDependenciesLabel = shownIfThereAreDependencies;
    setModuleNameVisibility(false, false);
    primaryModulePane.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateModule(primaryModulePane, myListModel.getPrimary());
      }
    });
  }

  public boolean canImport() {
    for (ModuleToImport moduleToImport : myListModel.getSelectedModules()) {
      if (Objects.equal(myListModel.getStatusSeverity(moduleToImport), MessageType.ERROR)) {
        return false;
      }
    }
    return true;
  }

  public String getModuleName(ModuleToImport module) {
    return myListModel.getModuleName(module);
  }

  /**
   * Sorts module in the tree.
   * <ol>
   * <li>First element is the module located in a path selected by user, if any.</li>
   * <li>Modules are ordered based on their location</li>
   * <li>Modules with unknown location come last.</li>
   * </ol>
   */
  private static class ModuleComparator implements Comparator<ModuleToImport> {
    @Nullable private final VirtualFile myImportPath;

    public ModuleComparator(@Nullable VirtualFile importPath) {
      myImportPath = importPath;
    }

    @Override
    public int compare(ModuleToImport o1, ModuleToImport o2) {
      if (o1 == null) {
        return o2 == null ? 0 : 1;
      }
      else if (o2 == null) {
        return -1;
      }
      else {
        VirtualFile l1 = o1.location;
        VirtualFile l2 = o2.location;

        Collator collator = Collator.getInstance();
        int namesComparison = collator.compare(o1.name, o2.name);

        if (l1 == null) {
          return l2 == null ? namesComparison : 1;
        }
        else if (l2 == null) {
          return -1;
        }
        else {
          if (Objects.equal(l1, myImportPath)) {
            return Objects.equal(l2, myImportPath) ? namesComparison : -1;
          }
          else if (Objects.equal(l2, myImportPath)) {
            return 1;
          }
          else {
            int pathComparison = collator.compare(l1.getPath(), l2.getPath());
            return pathComparison == 0 ? namesComparison : pathComparison;
          }
        }
      }
    }
  }
}

