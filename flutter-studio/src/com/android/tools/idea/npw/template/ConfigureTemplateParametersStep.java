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
package com.android.tools.idea.npw.template;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CLASS_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PARENT_ACTIVITY_CLASS;
import static com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll;

import com.android.builder.model.SourceProvider;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipLabel;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.model.RenderTemplateModel;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils;
import com.android.tools.idea.npw.template.components.ActivityComboProvider;
import com.android.tools.idea.npw.template.components.CheckboxProvider;
import com.android.tools.idea.npw.template.components.ComponentProvider;
import com.android.tools.idea.npw.template.components.EnumComboProvider;
import com.android.tools.idea.npw.template.components.LabelWithEditButtonProvider;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.template.components.ModuleTemplateComboProvider;
import com.android.tools.idea.npw.template.components.PackageComboProvider;
import com.android.tools.idea.npw.template.components.ParameterComponentProvider;
import com.android.tools.idea.npw.template.components.SeparatorProvider;
import com.android.tools.idea.npw.template.components.TextFieldProvider;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.IconProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.CircularParameterDependencyException;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.ParameterValueResolver;
import com.android.tools.idea.templates.StringEvaluator;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A step which takes a {@link Template} (generated by a template.xml file) and wraps a UI around
 * it, allowing a user to modify its various parameters.
 *
 * Far from being generic data, the template edited by this step is very Android specific, and
 * needs to be aware of things like the current project/module, package name, min supported API,
 * previously configured values, etc.
 */
public final class ConfigureTemplateParametersStep extends ModelWizardStep<RenderTemplateModel> {
  private final List<NamedModuleTemplate> myTemplates;

  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final LoadingCache<File, Optional<Icon>> myThumbnailsCache = IconLoader.createLoadingCache();
  private final Map<Parameter, RowEntry> myParameterRows = Maps.newHashMap();
  private final Map<Parameter, Object> myUserValues = Maps.newHashMap();

  private final StringEvaluator myEvaluator = new StringEvaluator();

  private final StringProperty myThumbPath = new StringValueProperty();

  /**
   * All parameters are calculated for validity every time any of them changes, and the first error
   * found is set here. This is then registered as its own validator with {@link #myValidatorPanel}.
   * This vastly simplifies validation, as we no longer have to worry about implicit relationships
   * between parameters (where changing one, like the package name, makes another valid/invalid).
   */
  private final StringProperty myInvalidParameterMessage = new StringValueProperty();
  private final ValidatorPanel myValidatorPanel;

  private JPanel myRootPanel;
  private JLabel myTemplateThumbLabel;
  private JPanel myParametersPanel;
  private JSeparator myFooterSeparator;
  private TooltipLabel myParameterDescriptionLabel;
  private JLabel myTemplateDescriptionLabel;

  private EvaluationState myEvaluationState = EvaluationState.NOT_EVALUATING;

  public ConfigureTemplateParametersStep(@NotNull RenderTemplateModel model,
                                         @NotNull String title,
                                         @NotNull List<NamedModuleTemplate> templates) {
    super(model, title);

    myTemplates = templates;
    myValidatorPanel = new ValidatorPanel(this, wrappedWithVScroll(myRootPanel));

    myParameterDescriptionLabel.setScope(myParametersPanel);

    // Add an extra blank line under the template description to separate it from the main body
    myTemplateDescriptionLabel.setBorder(JBUI.Borders.emptyBottom(myTemplateDescriptionLabel.getFont().getSize()));
  }

  private static Logger getLog() {
    return Logger.getInstance(ConfigureTemplateParametersStep.class);
  }

  /**
   * Given a parameter, return a String key we can use to interact with IntelliJ's
   * {@link RecentsManager} system.
   */
  @NotNull
  private static String getRecentsKeyForParameter(@NotNull Parameter parameter) {
    return "android.template." + parameter.id;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    TemplateHandle template = getModel().getTemplateHandle();
    if (template != null && template.getMetadata().getIconType() == AndroidIconType.NOTIFICATION) {
      // The Android Facet field will only be null if this step is being shown for a brand new, not-yet-created
      // project (a project must exist before it gets a facet associated with it). However, there are
      // currently no activities in the "new project" flow that need to create notification icons, so we
      // can always assume that Android Facet will be non-null here.
      assert getModel().getAndroidFacet() != null;
      return Collections.singletonList(new GenerateIconsStep(getModel().getAndroidFacet(), getModel()));
    }
    else {
      return super.createDependentSteps();
    }
  }

  @Override
  protected boolean shouldShow() {
    return getModel().getTemplateHandle() != null;
  }

  @Override
  protected void onEntering() {
    // The Model TemplateHandle may have changed, rebuild the panel
    resetPanel();

    final TemplateHandle templateHandle = getModel().getTemplateHandle();
    final TemplateMetadata templateMetadata = templateHandle.getMetadata();

    ApplicationManager.getApplication().invokeLater(() -> {
      // We want to set the label's text AFTER the wizard has been packed. Otherwise, its
      // width calculation gets involved and can really stretch out some wizards if the label is
      // particularly long (see Master/Detail Activity for example).
      myTemplateDescriptionLabel.setText(WizardUtils.toHtmlString(Strings.nullToEmpty(templateMetadata.getDescription())));
    }, ModalityState.any());

    if (templateMetadata.getFormFactor() != null) {
      setIcon(FormFactor.get(templateMetadata.getFormFactor()).getIcon());
    }

    final IconProperty thumb = new IconProperty(myTemplateThumbLabel);
    BoolProperty thumbVisibility = new VisibleProperty(myTemplateThumbLabel);
    myBindings.bind(thumb, new Expression<Optional<Icon>>(myThumbPath) {
      @NotNull
      @Override
      public Optional<Icon> get() {
        return myThumbnailsCache.getUnchecked(new File(templateHandle.getRootPath(), myThumbPath.get()));
      }
    });
    myBindings.bind(thumbVisibility, new Expression<Boolean>(thumb) {
      @NotNull
      @Override
      public Boolean get() {
        return thumb.get().isPresent();
      }
    });
    myThumbPath.set(getDefaultThumbnailPath());

    final TextProperty parameterDescription = new TextProperty(myParameterDescriptionLabel);
    myBindings.bind(new VisibleProperty(myFooterSeparator), new Expression<Boolean>(parameterDescription) {
      @NotNull
      @Override
      public Boolean get() {
        return !parameterDescription.get().isEmpty();
      }
    });

    for (final Parameter parameter : templateMetadata.getParameters()) {
      RowEntry row = createRowForParameter(getModel().getModule(), parameter);
      final ObservableValue<?> property = row.getProperty();
      if (property != null) {
        property.addListener(sender -> {
          // If not evaluating, change comes from the user (or user pressed "Back" and updates are "external". eg Template changed)
          if (myEvaluationState != EvaluationState.EVALUATING && myRootPanel.isShowing()) {
            myUserValues.put(parameter, property.get());
            // Evaluate later to prevent modifying Swing values that are locked during read
            enqueueEvaluateParameters();
          }
        });

        final ActionGroup resetParameterGroup = new ActionGroup() {
          @NotNull
          @Override
          public AnAction[] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{new ResetParameterAction(parameter)};
          }
        };
        row.getComponent().addMouseListener(new PopupHandler() {
          @Override
          public void invokePopup(Component comp, int x, int y) {
            ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, resetParameterGroup).getComponent().show(comp, x, y);
          }
        });
      }
      myParameterRows.put(parameter, row);
      row.addToPanel(myParametersPanel);
    }

    if (displayLanguageChoice(templateMetadata)) {
      RowEntry row = new RowEntry<>("Source Language", new LanguageComboProvider());
      row.addToPanel(myParametersPanel);
      SelectedItemProperty<Language> language = (SelectedItemProperty<Language>)row.getProperty();
      assert language != null; // LanguageComboProvider always sets this
      myBindings.bindTwoWay(ObjectProperty.wrap(language), getModel().getLanguage());
    }

    if (myTemplates.size() > 1) {
      RowEntry row = new RowEntry<>("Target Source Set", new ModuleTemplateComboProvider(myTemplates));
      row.setEnabled(myTemplates.size() > 1);
      row.addToPanel(myParametersPanel);

      //noinspection unchecked
      SelectedItemProperty<NamedModuleTemplate> template = (SelectedItemProperty<NamedModuleTemplate>)row.getProperty();
      assert template != null; // ModuleTemplateComboProvider always sets this
      myBindings.bind(getModel().getTemplate(), ObjectProperty.wrap(template));
      template.addListener(sender -> enqueueEvaluateParameters());
    }

    myValidatorPanel.registerMessageSource(myInvalidParameterMessage);

    evaluateParameters();
  }

  private boolean displayLanguageChoice(TemplateMetadata templateMetadata) {
    // Note: For new projects and new module we have a different UI.
    if (getModel().getAndroidFacet() == null) {
      return false;
    }
    // For Templates with an Android FormFactor or that have a class/package name, we allow the user to select the programming language
    return (templateMetadata.getFormFactor() != null || templateMetadata.getParameter(ATTR_CLASS_NAME) != null ||
            templateMetadata.getParameter(ATTR_PACKAGE_NAME) != null);
  }

  /**
   * Every template parameter, based on its type, can generate a row of* components. For example,
   * a text parameter becomes a "Label: Textfield" set, while a list of choices becomes
   * "Label: pulldown".
   * <p/>
   * This method takes an input {@link Parameter} and returns a generated {@link RowEntry} for
   * it, which neatly encapsulates its UI. The caller should use
   * {@link RowEntry#addToPanel(JPanel)} after receiving it.
   */
  private RowEntry<?> createRowForParameter(@Nullable final Module module, @NotNull Parameter parameter) {

    // Handle custom parameter types first.
    // TODO: Should we extract this logic into an extension point at some point, in order to be
    // more friendly to third-party plugins with templates? Do they need custom UI components?
    if (ATTR_PACKAGE_NAME.equals(parameter.id)) {
      assert parameter.name != null;
      RowEntry<?> rowEntry;
      if (module != null) {
        rowEntry = new RowEntry<>(parameter.name,
                                  new PackageComboProvider(module.getProject(), parameter, getModel().packageName().get(),
                                                           getRecentsKeyForParameter(parameter)));
      }
      else {
        rowEntry = new RowEntry<>(parameter.name, new LabelWithEditButtonProvider(parameter));
      }

      // All ATTR_PACKAGE_NAME providers should be string types and provide StringProperties
      //noinspection unchecked
      StringProperty packageName = (StringProperty)rowEntry.getProperty();
      assert packageName != null;
      myBindings.bindTwoWay(packageName, getModel().packageName());
      // Model.packageName is used for parameter evaluation, but updated asynchronously. Do new evaluation when value changes.
      myListeners.listen(getModel().packageName(), sender -> enqueueEvaluateParameters());
      return rowEntry;
    }

    if (ATTR_PARENT_ACTIVITY_CLASS.equals(parameter.id)) {
      if (module != null) {
        assert parameter.name != null;
        return new RowEntry<>(parameter.name, new ActivityComboProvider(module, parameter,
                                                                        getRecentsKeyForParameter(
                                                                          parameter)));
      }
    }

    // Handle standard parameter types
    switch (parameter.type) {
      case STRING:
        assert parameter.name != null;
        return new RowEntry<>(parameter.name, new TextFieldProvider(parameter));
      case BOOLEAN:
        return new RowEntry<>(new CheckboxProvider(parameter), RowEntry.WantGrow.NO);
      case SEPARATOR:
        return new RowEntry<>(new SeparatorProvider(parameter), RowEntry.WantGrow.YES);
      case ENUM:
        assert parameter.name != null;
        return new RowEntry<>(parameter.name, new EnumComboProvider(parameter));
      default:
        throw new IllegalStateException(
          String.format("Can't create UI for unknown component type: %1$s (%2$s)", parameter.type, parameter.id));
    }
  }

  /**
   * Instead of evaluating all parameters immediately, invoke the request to run later. This
   * option allows us to avoid the situation where a value has just changed, is forcefully
   * re-evaluated immediately, and causes Swing to throw an exception between we're editing a
   * value while it's in a locked read-only state.
   */
  private void enqueueEvaluateParameters() {
    if (myEvaluationState == EvaluationState.REQUEST_ENQUEUED) {
      return;
    }
    myEvaluationState = EvaluationState.REQUEST_ENQUEUED;

    ApplicationManager.getApplication().invokeLater(this::evaluateParameters, ModalityState.any());
  }

  private boolean isNewModule() {
    return getModel().getModule() == null;
  }

  /**
   * If we are creating a new module, there are some fields that we need to hide.
   */
  private boolean isParameterVisible(Parameter parameter) {
    return !isNewModule() ||
           (!ATTR_PACKAGE_NAME.equals(parameter.id) &&
            !ATTR_IS_LAUNCHER.equals(parameter.id) &&
            !ATTR_PARENT_ACTIVITY_CLASS.equals(parameter.id));
  }

  /**
   * Run through all parameters for our current template and update their values, including
   * visibility, enabled state, and actual values.
   *
   * Because our templating system is opaque to us, this operation is relatively overkill (we
   * evaluate all parameters every time, not just ones we suspect have changed), but this should
   * only get run in response to user input, which isn't too often.
   */
  private void evaluateParameters() {
    myEvaluationState = EvaluationState.EVALUATING;

    Collection<Parameter> parameters = getModel().getTemplateHandle().getMetadata().getParameters();
    Set<String> excludedParameters = Sets.newHashSet();

    try {
      int projectType = getModel().getAndroidFacet() == null ? -1 : getModel().getAndroidFacet().getConfiguration().getProjectType();
      boolean isInstantApp = projectType == PROJECT_TYPE_FEATURE || getModel().instantApp().get();

      Map<String, Object> additionalValues = Maps.newHashMap();
      new TemplateValueInjector(additionalValues)
        .addTemplateAdditionalValues(getModel().packageName().get(), isInstantApp, getModel().getTemplate());

      Map<String, Object> allValues = Maps.newHashMap(additionalValues);

      Map<Parameter, Object> parameterValues =
        ParameterValueResolver.resolve(parameters, myUserValues, additionalValues, new ParameterDeduplicator());
      for (Parameter parameter : parameters) {
        Object value = parameterValues.get(parameter);
        if (value == null) continue;
        myParameterRows.get(parameter).setValue(value);
        allValues.put(parameter.id, value);
      }

      for (Parameter parameter : parameters) {
        String enabledStr = Strings.nullToEmpty(parameter.enabled);
        if (!enabledStr.isEmpty()) {
          boolean enabled = myEvaluator.evaluateBooleanExpression(enabledStr, allValues, true);
          myParameterRows.get(parameter).setEnabled(enabled);
          if (!enabled) {
            excludedParameters.add(parameter.id);
          }
        }

        if (!isParameterVisible(parameter)) {
          myParameterRows.get(parameter).setVisible(false);
          excludedParameters.add(parameter.id);
          continue;
        }

        String visibilityStr = Strings.nullToEmpty(parameter.visibility);
        if (!visibilityStr.isEmpty()) {
          boolean visible = myEvaluator.evaluateBooleanExpression(visibilityStr, allValues, true);
          myParameterRows.get(parameter).setVisible(visible);
          if (!visible) {
            excludedParameters.add(parameter.id);
          }
        }
      }

      // Aggressively update the icon path just in case it changed
      myThumbPath.set(getCurrentThumbnailPath());
    }
    catch (CircularParameterDependencyException e) {
      getLog().error("Circular dependency between parameters in template %1$s", e, getModel().getTemplateHandle().getMetadata().getTitle());
    }
    finally {
      myEvaluationState = EvaluationState.NOT_EVALUATING;
    }

    myInvalidParameterMessage.set(Strings.nullToEmpty(validateAllParametersExcept(excludedParameters)));
  }

  /**
   * Get the default thumbnail path, which is useful at initialization time before we have all
   * parameters set up.
   */
  @NotNull
  private String getDefaultThumbnailPath() {
    return Strings.nullToEmpty(getModel().getTemplateHandle().getMetadata().getThumbnailPath());
  }

  /**
   * Get the current thumbnail path, based on current parameter values.
   */
  @NotNull
  private String getCurrentThumbnailPath() {
    return Strings.nullToEmpty(getModel().getTemplateHandle().getMetadata().getThumbnailPath(parameterId -> {
      Parameter parameter = getModel().getTemplateHandle().getMetadata().getParameter(parameterId);
      ObservableValue<?> property = myParameterRows.get(parameter).getProperty();
      return property != null ? property.get() : null;
    }));
  }

  @Nullable
  private String validateAllParametersExcept(@NotNull Set<String> excludedParameters) {
    String message = null;

    Collection<Parameter> parameters = getModel().getTemplateHandle().getMetadata().getParameters();
    Module module = getModel().getModule();
    Project project = getModel().getProject().getValueOrNull();
    SourceProvider sourceProvider = AndroidGradleModuleUtils.getSourceProvider(getModel().getTemplate().get());

    for (Parameter parameter : parameters) {
      ObservableValue<?> property = myParameterRows.get(parameter).getProperty();
      if (property == null || excludedParameters.contains(parameter.id)) {
        continue;
      }

      Set<Object> relatedValues = getRelatedValues(parameter);
      message = parameter.validate(project, module, sourceProvider, getModel().packageName().get(), property.get(), relatedValues);

      if (message != null) {
        break;
      }
    }

    return message;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    Component[] children = myParametersPanel.getComponents();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < children.length; i++) {
      JComponent child = (JComponent)children[i];
      boolean isContainer = child.getComponentCount() > 0;
      if (!isContainer && child.isFocusable() && child.isVisible()) {
        return child;
      }
    }

    return null;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void createUIComponents() {
    myParametersPanel = new JPanel(new TabularLayout("Fit-,*").setVGap(10));
  }

  private void resetPanel() {
    myParametersPanel.removeAll();
    myParameterRows.clear();
    dispose();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
    myThumbnailsCache.invalidateAll();
  }

  /**
   * When finished with this step, calculate and install a bunch of values that will be used in our
   * template's <a href="http://freemarker.incubator.apache.org/docs/dgui_quickstart_basics.html">data model.</a>
   */
  @Override
  protected void onProceeding() {
    // Some parameter values should be saved for later runs through this wizard, so do that first.
    for (RowEntry rowEntry : myParameterRows.values()) {
      rowEntry.accept();
    }

    // Prepare the template data-model, starting from scratch and filling in all values we know
    Map<String, Object> templateValues = getModel().getTemplateValues();
    templateValues.clear();

    for (Parameter parameter : myParameterRows.keySet()) {
      ObservableValue<?> property = myParameterRows.get(parameter).getProperty();
      if (property != null) {
        templateValues.put(parameter.id, property.get());
      }
    }
  }

  /**
   * Fetches the values of all parameters that are related to the target parameter. This is useful
   * information when validating a parameter's value.
   */
  private Set<Object> getRelatedValues(@NotNull Parameter parameter) {
    Set<Object> relatedValues = Sets.newHashSet();
    for (Parameter related : parameter.template.getRelatedParams(parameter)) {
      ObservableValue<?> property = myParameterRows.get(related).getProperty();
      if (property == null) continue;

      relatedValues.add(property.get());
    }
    return relatedValues;
  }

  /**
   * Because the FreeMarker templating engine is mostly opaque to us, any time any parameter
   * changes, we need to re-evaluate all parameters. Parameter evaluation can be started
   * immediately via {@link #evaluateParameters()} or with a delay using
   * {@link #enqueueEvaluateParameters()}.
   */
  private enum EvaluationState {
    NOT_EVALUATING,
    REQUEST_ENQUEUED,
    EVALUATING,
  }

  /**
   * A template is broken down into separate fields, each which is given a row with optional
   * header. This class wraps all UI elements in the row, providing methods for managing them.
   */
  private static final class RowEntry<T extends JComponent> {
    @Nullable private final JPanel myHeader;
    @NotNull private final ComponentProvider<T> myComponentProvider;
    @NotNull private final T myComponent;
    @Nullable private final AbstractProperty<?> myProperty;
    @NotNull private final WantGrow myWantGrow;

    public RowEntry(@NotNull String headerText, @NotNull ComponentProvider<T> componentProvider) {
      myHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
      JBLabel headerLabel = new JBLabel(headerText + ":");
      myHeader.add(headerLabel);
      myHeader.add(Box.createHorizontalStrut(20));
      myWantGrow = WantGrow.NO;
      myComponentProvider = componentProvider;
      myComponent = componentProvider.createComponent();
      myProperty = componentProvider.createProperty(myComponent);

      headerLabel.setLabelFor(myComponent);
    }

    public RowEntry(@NotNull ParameterComponentProvider<T> componentProvider, @NotNull WantGrow stretch) {
      myHeader = null;
      myWantGrow = stretch;
      myComponentProvider = componentProvider;
      myComponent = componentProvider.createComponent();
      myProperty = componentProvider.createProperty(myComponent);
    }

    @Nullable
    public ObservableValue<?> getProperty() {
      return myProperty;
    }

    public void addToPanel(@NotNull JPanel panel) {
      assert panel.getLayout().getClass().equals(TabularLayout.class);
      int row = panel.getComponentCount();

      if (myHeader != null) {
        panel.add(myHeader, new TabularLayout.Constraint(row, 0));
        assert myWantGrow == WantGrow.NO;
      }

      int colspan = myWantGrow == WantGrow.YES ? 2 : 1;
      panel.add(myComponent, new TabularLayout.Constraint(row, 1, colspan));
    }

    public void setEnabled(boolean enabled) {
      if (myHeader != null) {
        myHeader.setEnabled(enabled);
      }
      myComponent.setEnabled(enabled);
    }

    public void setVisible(boolean visible) {
      if (myHeader != null) {
        myHeader.setVisible(visible);
      }
      myComponent.setVisible(visible);
    }

    public <V> void setValue(@NotNull V value) {
      assert myProperty != null;
      //noinspection unchecked Should always be true if registration is done correctly
      ((AbstractProperty<V>)myProperty).set(value);
    }

    @NotNull
    public JComponent getComponent() {
      return myComponent;
    }

    public void accept() {
      myComponentProvider.accept(myComponent);
    }

    /**
     * A row is usually broken into two columns, but the item can optionally grow into both columns
     * if it doesn't have a header.
     */
    public enum WantGrow {
      NO,
      YES,
    }
  }

  private final class ParameterDeduplicator implements ParameterValueResolver.Deduplicator {
    @Override
    @Nullable
    public String deduplicate(@NotNull Parameter parameter, @Nullable String value) {
      if (Strings.isNullOrEmpty(value) || !parameter.constraints.contains(Parameter.Constraint.UNIQUE)) {
        return value;
      }

      String suggested = value;
      String extPart = Strings.emptyToNull(Files.getFileExtension(value));
      String namePart = value.replace("." + extPart, "");

      // Remove all trailing digits, because we probably were the ones that put them there.
      // For example, if two parameters affect each other, say "Name" and "Layout", you get this:
      // Step 1) Resolve "Name" -> "Name2", causes related "Layout" to become "Layout2"
      // Step 2) Resolve "Layout2" -> "Layout22"
      // Although we may possibly strip real digits from a name, it's much more likely we're not,
      // and a user can always modify the related value manually in that rare case.
      namePart = namePart.replaceAll("\\d*$", "");
      Joiner filenameJoiner = Joiner.on('.').skipNulls();

      int suffix = 2;
      Module module = getModel().getModule();
      Project project = getModel().getProject().getValueOrNull();
      Set<Object> relatedValues = getRelatedValues(parameter);
      SourceProvider sourceProvider = AndroidGradleModuleUtils.getSourceProvider(getModel().getTemplate().get());
      while (!parameter.uniquenessSatisfied(project, module, sourceProvider, getModel().packageName().get(), suggested, relatedValues)) {
        suggested = filenameJoiner.join(namePart + suffix, extPart);
        suffix++;
      }
      return suggested;
    }
  }

  /**
   * Right-click context action which lets the user clear any modifications they made to a
   * parameter. Once cleared, the parameter is re-evaluated.
   */
  private final class ResetParameterAction extends AnAction {
    @NotNull private final Parameter myParameter;

    public ResetParameterAction(@NotNull Parameter parameter) {
      super("Restore default value", "Discards any user modifications made to this parameter", AllIcons.General.Reset);
      myParameter = parameter;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myUserValues.containsKey(myParameter));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myUserValues.remove(myParameter);
      evaluateParameters();
    }
  }
}
