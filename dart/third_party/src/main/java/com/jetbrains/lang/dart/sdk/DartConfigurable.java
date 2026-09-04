// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.sdk;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.Configurable.NoScroll;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTreeListener;
import com.intellij.ui.CheckboxTreeTable;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.PortField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.flutter.FlutterUtil;
import com.jetbrains.lang.dart.lsp.DartBridgeLspServerManager;
import com.jetbrains.lang.dart.lsp.LspMethod;
import com.jetbrains.lang.dart.ui.BasicComboBoxWithBrowseButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DartConfigurable implements SearchableConfigurable, NoScroll, Disposable {
  public static final int WEBDEV_PORT_DEFAULT = 53322;
  private static final String WEBDEV_PORT_PROPERTY_NAME = "dart.webdev.port";

  private static final String DART_SETTINGS_PAGE_ID = "dart.settings";

  private JPanel myMainPanel;
  private JBCheckBox myEnableDartSupportCheckBox;

  private JPanel mySettingsPanel;
  private BasicComboBoxWithBrowseButton<String> mySdkPathComboWithBrowse;
  private JBLabel myVersionLabel;
  private JBCheckBox myCheckSdkUpdateCheckBox;
  // disabled and unchecked, shown in UI instead of myCheckSdkUpdateCheckBox if selected Dart SDK is a part of a Flutter SDK
  private JBCheckBox myCheckSdkUpdateCheckBoxFake;
  private ComboBox<DartSdkUpdateOption> mySdkUpdateChannelCombo;
  private JButton myCheckSdkUpdateButton;

  private PortField myPortField;

  private JBLabel myModulesPanelLabel;
  private JPanel myModulesPanel;

  private CheckboxTreeTable myModulesCheckboxTreeTable;
  private JBLabel myErrorLabel;
  private JBCheckBox myExperimentalLspFeaturesCheckBox;
  private static final String DART_LSP_EXPERIMENTAL_ENABLED = "dart.lsp.experimental.enabled";

  private final @NotNull Project myProject;
  private final boolean myShowModulesPanel;

  private boolean myDartSupportEnabledInitial;
  private @Nullable DartSdk mySdkInitial;
  private final @NotNull Collection<Module> myModulesWithDartSdkLibAttachedInitial = new HashSet<>();

  public DartConfigurable(final @NotNull Project project) {
    myProject = project;
    myShowModulesPanel = DartSdkLibUtil.isIdeWithMultipleModuleSupport() || ModuleManager.getInstance(project).getModules().length > 1;
    initEnableDartSupportCheckBox();
    initDartSdkControls();
    initModulesPanel();
    myErrorLabel.setIcon(AllIcons.Actions.Lightning);

    String lspFeatures = LspMethod.Companion.getExperimentalFeatures().stream()
      .map(LspMethod::getPresentableName)
      .filter(Objects::nonNull)
      .collect(Collectors.joining(", "));
    myExperimentalLspFeaturesCheckBox.setText(DartBundle.message("settings.page.checkbox.experimental.lsp.features", lspFeatures));
  }

  private void initEnableDartSupportCheckBox() {
    myEnableDartSupportCheckBox.setText(DartSdkLibUtil.isIdeWithMultipleModuleSupport()
                                        ? DartBundle
                                          .message("settings.page.checkbox.enable.dart.support.for.project.0", myProject.getName())
                                        : DartBundle.message("settings.page.checkbox.enable.dart.support.check.box"));
    myEnableDartSupportCheckBox.addActionListener(e -> {
      updateControlsEnabledState();
      updateErrorLabel();
    });
  }

  private void initDartSdkControls() {
    DartSdkUtil.initDartSdkControls(myProject, mySdkPathComboWithBrowse, myVersionLabel, this);

    final JTextComponent sdkEditor = (JTextComponent)mySdkPathComboWithBrowse.getEditor().getEditorComponent();
    final DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(final @NotNull DocumentEvent e) {
        final String sdkHomePath = getTextFromCombo(mySdkPathComboWithBrowse);
        if (!sdkHomePath.isEmpty()) {
          final String version = DartSdkUtil.getSdkVersion(sdkHomePath);
          if (version != null && (version.contains("-dev.") || version.contains("-edge."))) {
            mySdkUpdateChannelCombo.setSelectedItem(DartSdkUpdateOption.StableAndDev);
          }
        }

        updateControlsEnabledState();
        updateErrorLabel();
      }
    };
    sdkEditor.getDocument().addDocumentListener(listener);
    Disposer.register(this, () -> sdkEditor.getDocument().removeDocumentListener(listener));

    myCheckSdkUpdateCheckBox.addActionListener(e -> {
      final boolean enabled = myCheckSdkUpdateCheckBox.isSelected() && myCheckSdkUpdateCheckBox.isEnabled();
      mySdkUpdateChannelCombo.setEnabled(enabled);
      myCheckSdkUpdateButton.setEnabled(enabled);

      if (enabled) {
        IdeFocusManager.getInstance(myProject).requestFocus(mySdkUpdateChannelCombo, true);
      }
    });

    mySdkUpdateChannelCombo.setModel(new DefaultComboBoxModel<>(DartSdkUpdateOption.OPTIONS_TO_SHOW_IN_COMBO));
    mySdkUpdateChannelCombo.setRenderer(SimpleListCellRenderer.create("", DartSdkUpdateOption::getPresentableName));

    myCheckSdkUpdateButton.addActionListener(e -> {
      final Runnable runnable = this::checkSdkUpdate;
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(runnable, DartBundle.message("checking.dart.sdk.update"), true, myProject, myMainPanel);
    });
  }

  private void checkSdkUpdate() {
    final String currentSdkVersion = myVersionLabel.getText();

    final DartSdkUpdateChecker.SdkUpdateInfo sdkUpdateInfo =
      DartSdkUpdateChecker.getSdkUpdateInfo((DartSdkUpdateOption)mySdkUpdateChannelCombo.getSelectedItem());

    ApplicationManager.getApplication().invokeLater(() -> {
      if (sdkUpdateInfo == null) {
        Messages.showErrorDialog(myProject,
                                 DartBundle.message("dart.sdk.update.check.failed"),
                                 DartBundle.message("dart.sdk.update.title"));
      }
      else {
        final String message;
        if (currentSdkVersion == null || currentSdkVersion.isEmpty()) {
          message = DartBundle.message("dart.sdk.0.available.for.download", sdkUpdateInfo.myVersion, sdkUpdateInfo.myDownloadUrl);
        }
        else if (DartSdkUpdateChecker.compareDartSdkVersions(currentSdkVersion, sdkUpdateInfo.myVersion) >= 0) {
          message = DartBundle.message("dart.sdk.0.is.up.to.date", currentSdkVersion);
        }
        else {
          message =
            DartBundle.message("new.dart.sdk.0.available.for.download..dialog", sdkUpdateInfo.myVersion, sdkUpdateInfo.myDownloadUrl);
        }

        Messages.showInfoMessage(myProject, message, DartBundle.message("dart.sdk.update.title"));
      }
    }, ModalityState.defaultModalityState(), myProject.getDisposed());
  }

  private void initModulesPanel() {
    if (!myShowModulesPanel) {
      myModulesPanel.setVisible(false);
      return;
    }

    myModulesPanelLabel.setText(DartSdkLibUtil.isIdeWithMultipleModuleSupport()
                                ? DartBundle.message("settings.page.label.enable.dart.support.for.following.modules")
                                : DartBundle.message("settings.page.label.enable.dart.support.for.following.projects"));

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    Arrays.sort(modules, Comparator.comparing(module -> StringUtil.toLowerCase(module.getName())));

    final CheckedTreeNode rootNode = new CheckedTreeNode(myProject);
    ((DefaultTreeModel)myModulesCheckboxTreeTable.getTree().getModel()).setRoot(rootNode);
    myModulesCheckboxTreeTable.getTree().setRootVisible(DartSdkLibUtil.isIdeWithMultipleModuleSupport());
    myModulesCheckboxTreeTable.getTree().setShowsRootHandles(false);
    myModulesCheckboxTreeTable.getTree().setBorder(JBUI.Borders.emptyLeft(5));

    for (final Module module : modules) {
      rootNode.add(new CheckedTreeNode(module));
    }

    ((DefaultTreeModel)myModulesCheckboxTreeTable.getTree().getModel()).reload(rootNode);
  }

  @Override
  public @NotNull String getId() {
    return "dart.settings";
  }

  @Override
  public @Nls String getDisplayName() {
    return DartBundle.message("dart.title");
  }

  @Override
  public @Nullable JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    final String sdkHomePath = getTextFromCombo(mySdkPathComboWithBrowse);
    final boolean sdkSelected = DartSdkUtil.isDartSdkHome(sdkHomePath);

    // was disabled, now disabled (or no sdk selected) => not modified, do not care about other controls
    if (!myDartSupportEnabledInitial && (!myEnableDartSupportCheckBox.isSelected() || !sdkSelected)) return false;
    // was enabled, now disabled => modified
    if (myDartSupportEnabledInitial && !myEnableDartSupportCheckBox.isSelected()) return true;
    // was disabled, now enabled or was enabled, now enabled => need to check further

    final String initialSdkHomePath = mySdkInitial == null ? "" : mySdkInitial.getHomePath();
    if (sdkSelected && !sdkHomePath.equals(initialSdkHomePath)) return true;

    final boolean flutter = FlutterUtil.getFlutterRoot(sdkHomePath) != null;
    if (!flutter) {
      final DartSdkUpdateOption sdkUpdateOption = myCheckSdkUpdateCheckBox.isSelected()
                                                  ? (DartSdkUpdateOption)mySdkUpdateChannelCombo.getSelectedItem()
                                                  : DartSdkUpdateOption.DoNotCheck;
      if (sdkUpdateOption != DartSdkUpdateOption.getDartSdkUpdateOption()) return true;
    }

    if (myPortField.getNumber() != getWebdevPort(myProject)) return true;

    if (myExperimentalLspFeaturesCheckBox.isSelected() != isExperimentalLspFeaturesEnabled(myProject)) return true;

    if (myShowModulesPanel) {
      final Module[] selectedModules = myModulesCheckboxTreeTable.getCheckedNodes(Module.class);
      if (selectedModules.length != myModulesWithDartSdkLibAttachedInitial.size()) return true;

      for (final Module module : selectedModules) {
        if (!myModulesWithDartSdkLibAttachedInitial.contains(module)) return true;
      }
    }
    else {
        return myDartSupportEnabledInitial != myEnableDartSupportCheckBox.isSelected();
    }

    return false;
  }

  private static @NotNull String getTextFromCombo(final @NotNull ComboBox<?> combo) {
    return FileUtilRt.toSystemIndependentName(combo.getEditor().getItem().toString().trim());
  }

  @Override
  public void reset() {
    // remember initial state
    mySdkInitial = DartSdk.getDartSdk(myProject);
    myModulesWithDartSdkLibAttachedInitial.clear();

    if (mySdkInitial != null) {
      myModulesWithDartSdkLibAttachedInitial.addAll(DartSdkLibUtil.getModulesWithDartSdkEnabled(myProject));
    }

    myDartSupportEnabledInitial = !myModulesWithDartSdkLibAttachedInitial.isEmpty();

    // reset UI
    myEnableDartSupportCheckBox.setSelected(myDartSupportEnabledInitial);
    @NlsSafe String sdkInitialPath = mySdkInitial == null ? "" : FileUtilRt.toSystemDependentName(mySdkInitial.getHomePath());
    mySdkPathComboWithBrowse.getEditor().setItem(sdkInitialPath);
    if (!sdkInitialPath.isEmpty()) {
      ensureComboModelContainsCurrentItem(mySdkPathComboWithBrowse);
    }

    final DartSdkUpdateOption sdkUpdateOption = DartSdkUpdateOption.getDartSdkUpdateOption();
    myCheckSdkUpdateCheckBox.setSelected(sdkUpdateOption != DartSdkUpdateOption.DoNotCheck);
    mySdkUpdateChannelCombo.setSelectedItem(sdkUpdateOption);

    myPortField.setNumber(getWebdevPort(myProject));

    myExperimentalLspFeaturesCheckBox.setSelected(isExperimentalLspFeaturesEnabled(myProject));

    if (myShowModulesPanel) {
      final CheckedTreeNode rootNode = (CheckedTreeNode)myModulesCheckboxTreeTable.getTree().getModel().getRoot();
      rootNode.setChecked(false);
      final Enumeration children = rootNode.children();
      while (children.hasMoreElements()) {
        final CheckedTreeNode node = (CheckedTreeNode)children.nextElement();
        node.setChecked(myModulesWithDartSdkLibAttachedInitial.contains((Module)node.getUserObject()));
      }
    }

    updateControlsEnabledState();
    updateErrorLabel();
  }

  private static void ensureComboModelContainsCurrentItem(final @NotNull JComboBox comboBox) {
    final Object currentItem = comboBox.getEditor().getItem();

    boolean contains = false;
    for (int i = 0; i < comboBox.getModel().getSize(); i++) {
      if (currentItem.equals(comboBox.getModel().getElementAt(i))) {
        contains = true;
        break;
      }
    }

    if (!contains) {
      ((DefaultComboBoxModel)comboBox.getModel()).insertElementAt(currentItem, 0);
      comboBox.setSelectedItem(currentItem); // to set focus on current item in combo popup
      comboBox.getEditor().setItem(currentItem); // to set current item in combo itself
    }
  }

  @Override
  public void apply() {
    final boolean initialExperimentalEnabled = isExperimentalLspFeaturesEnabled(myProject);
    final boolean currentExperimentalEnabled = myExperimentalLspFeaturesCheckBox.isSelected();

    final Runnable runnable = () -> {
      if (myEnableDartSupportCheckBox.isSelected()) {
        final String sdkHomePath = getTextFromCombo(mySdkPathComboWithBrowse);

        if (DartSdkUtil.isDartSdkHome(sdkHomePath)) {
          DartSdkUtil.updateKnownSdkPaths(myProject, sdkHomePath);

          DartSdkLibUtil.ensureDartSdkConfigured(myProject, sdkHomePath);
          DaemonCodeAnalyzer.getInstance(myProject).restart();

          final Module[] modules = myShowModulesPanel
                                   ? myModulesCheckboxTreeTable.getCheckedNodes(Module.class)
                                   : ModuleManager.getInstance(myProject).getModules();
          DartSdkLibUtil.enableDartSdkForSpecifiedModulesAndDisableForOthers(myProject, modules);
        }

        final boolean flutter = FlutterUtil.getFlutterRoot(sdkHomePath) != null;
        if (!flutter) {
          final DartSdkUpdateOption sdkUpdateOption = myCheckSdkUpdateCheckBox.isSelected()
                                                      ? (DartSdkUpdateOption)mySdkUpdateChannelCombo.getSelectedItem()
                                                      : DartSdkUpdateOption.DoNotCheck;
          DartSdkUpdateOption.setDartSdkUpdateOption(sdkUpdateOption);
        }

        setWebdevPort(myProject, myPortField.getNumber());
        setExperimentalLspFeaturesEnabled(myProject, currentExperimentalEnabled);
      }
      else {
        if (!myModulesWithDartSdkLibAttachedInitial.isEmpty() && mySdkInitial != null) {
          DartSdkLibUtil.disableDartSdk(myModulesWithDartSdkLibAttachedInitial);
        }
      }
    };

    ApplicationManager.getApplication().runWriteAction(runnable);

    if (myEnableDartSupportCheckBox.isSelected() && initialExperimentalEnabled != currentExperimentalEnabled) {
      DartBridgeLspServerManager bridgeManager = myProject.getService(DartBridgeLspServerManager.class);
      bridgeManager.stopBridgeServer();
      bridgeManager.startBridgeServer();
    }

    reset(); // because we rely on remembering initial state
  }

  @Override
  public void disposeUIResources() {
    mySdkInitial = null;
    myModulesWithDartSdkLibAttachedInitial.clear();
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    // No-op: this class implements Disposable solely to act as a parent node in the Disposer tree.
    // All actual resource cleanup (e.g., DocumentListeners) is handled by child Disposables
    // registered with this instance and triggered via Disposer.dispose(this) in disposeUIResources().
  }

  private void updateControlsEnabledState() {
    UIUtil.setEnabled(mySettingsPanel, myEnableDartSupportCheckBox.isSelected(), true);

    final boolean flutter = FlutterUtil.getFlutterRoot(getTextFromCombo(mySdkPathComboWithBrowse)) != null;
    myCheckSdkUpdateCheckBox.setVisible(!flutter);
    final boolean enabled = myCheckSdkUpdateCheckBox.isVisible() &&
                            myCheckSdkUpdateCheckBox.isEnabled() &&
                            myCheckSdkUpdateCheckBox.isSelected();
    mySdkUpdateChannelCombo.setEnabled(enabled);
    myCheckSdkUpdateButton.setEnabled(enabled);

    myCheckSdkUpdateCheckBoxFake.setVisible(flutter);
    myCheckSdkUpdateCheckBoxFake.setEnabled(false);
  }

  private void updateErrorLabel() {
    final String message = getErrorMessage();
    if (message == null) {
      myErrorLabel.setText("");
      myErrorLabel.setVisible(false);
      return;
    }

    HtmlChunk.Element html = new HtmlBuilder().append(message)
      .wrapWith("font").attr("color", "#" + ColorUtil.toHex(JBColor.RED))
      .wrapWith("html");
    myErrorLabel.setText(html.toString());
    myErrorLabel.setVisible(true);
  }

  private @Nullable @NlsContexts.Label String getErrorMessage() {
    if (!myEnableDartSupportCheckBox.isSelected()) {
      return null;
    }

    String message = DartSdkUtil.getErrorMessageIfWrongSdkRootPath(getTextFromCombo(mySdkPathComboWithBrowse));
    if (message != null) return message;

    if (myShowModulesPanel) {
      final Module[] modules = myModulesCheckboxTreeTable.getCheckedNodes(Module.class);
      if (modules.length == 0) {
        return DartSdkLibUtil.isIdeWithMultipleModuleSupport()
               ? DartBundle.message("warning.no.modules.selected.dart.support.will.be.disabled")
               : DartBundle.message("warning.no.projects.selected.dart.support.will.be.disabled");
      }
    }

    return null;
  }

  private void createUIComponents() {
    mySdkPathComboWithBrowse = new BasicComboBoxWithBrowseButton<>();

    final CheckboxTree.CheckboxTreeCellRenderer checkboxTreeCellRenderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode node)) return;

        final boolean dartSupportEnabled = myEnableDartSupportCheckBox.isSelected();

        final Object userObject = node.getUserObject();

        if (userObject instanceof Project) {
          if (!dartSupportEnabled) {
            //disabled state is also used as partially selected, that's why we do not change 'enabled' state if dartSupportEnabled
            getCheckbox().setEnabled(false);
          }
          getTextRenderer().setEnabled(dartSupportEnabled);

          getTextRenderer().append(DartBundle.message("project.0", ((Project)userObject).getName()));
        }
        else if (userObject instanceof Module) {
          getCheckbox().setEnabled(dartSupportEnabled);
          getTextRenderer().setEnabled(dartSupportEnabled);

          final Icon moduleIcon = ModuleType.get((Module)userObject).getIcon();
          getTextRenderer().setIcon(dartSupportEnabled ? moduleIcon : IconLoader.getDisabledIcon(moduleIcon));
          getTextRenderer().append(((Module)userObject).getName());
        }
      }
    };

    myModulesCheckboxTreeTable = new CheckboxTreeTable(null, checkboxTreeCellRenderer, new ColumnInfo[]{new TreeColumnInfo("")});
    myModulesCheckboxTreeTable.addCheckboxTreeListener(new CheckboxTreeListener() {
      @Override
      public void nodeStateChanged(@NotNull CheckedTreeNode node) {
        updateErrorLabel();
      }
    });
    //myModulesCheckboxTreeTable.setRowHeight(myModulesCheckboxTreeTable.getRowHeight() + 2);

    myModulesCheckboxTreeTable.getTree().addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(final TreeExpansionEvent event) {}

      @Override
      public void treeWillCollapse(final TreeExpansionEvent event) throws ExpandVetoException {
        throw new ExpandVetoException(event);
      }
    });
  }

  public static void openDartSettings(final @NotNull Project project) {
    ShowSettingsUtilImpl.showSettingsDialog(project, DART_SETTINGS_PAGE_ID, "");
  }

  public static int getWebdevPort(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getInt(WEBDEV_PORT_PROPERTY_NAME, WEBDEV_PORT_DEFAULT);
  }

  private static void setWebdevPort(@NotNull Project project, int port) {
    PropertiesComponent.getInstance(project).setValue(WEBDEV_PORT_PROPERTY_NAME, port, WEBDEV_PORT_DEFAULT);
  }

  public static boolean isExperimentalLspFeaturesEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(DART_LSP_EXPERIMENTAL_ENABLED, true);
  }

  public static void setExperimentalLspFeaturesEnabled(@NotNull Project project, boolean enabled) {
    PropertiesComponent.getInstance(project).setValue(DART_LSP_EXPERIMENTAL_ENABLED, enabled, true);
  }
}
