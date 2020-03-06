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
package com.android.tools.idea.npwOld.template.components;

import com.android.SdkConstants;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.templates.Parameter;
import com.google.common.base.Strings;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A provider which returns a combobox paired with a browse button which allows the user to explore
 * the current project and select a target Activity class.
 */
public final class ActivityComboProvider extends ParameterComponentProvider<ReferenceEditorComboWithBrowseButton> {

  @NotNull private final Module myModule;
  @NotNull private final String myRecentsKey;

  public ActivityComboProvider(@NotNull Module module, @NotNull Parameter parameter, @NotNull String recentsKey) {
    super(parameter);
    myModule = module;
    myRecentsKey = recentsKey;
  }

  @NotNull
  @Override
  protected ReferenceEditorComboWithBrowseButton createComponent(@NotNull Parameter parameter) {
    ChooseClassActionListener browseAction = new ChooseClassActionListener(myModule);

    RecentsManager recentsManager = RecentsManager.getInstance(myModule.getProject());
    // Always have a blank entry and make sure it shows up first - because by default, most users
    // won't want to add any parent.
    recentsManager.registerRecentEntry(myRecentsKey, "");

    ReferenceEditorComboWithBrowseButton control =
      new ReferenceEditorComboWithBrowseButton(browseAction, "", myModule.getProject(), true, new OnlyShowActivities(myModule),
                                               myRecentsKey);

    // Need to tell our browse action which component to modify when user hits OK. Ideally we'd
    // pass this into browseAction's constructor but the control isn't created until after.
    browseAction.setOwner(control);

    return control;
  }

  @Nullable
  @Override
  public AbstractProperty<?> createProperty(@NotNull ReferenceEditorComboWithBrowseButton component) {
    return new TextProperty(component.getChildComponent());
  }

  @Override
  public void accept(@NotNull ReferenceEditorComboWithBrowseButton component) {
    RecentsManager recentsManager = RecentsManager.getInstance(myModule.getProject());
    recentsManager.registerRecentEntry(myRecentsKey, component.getText());
  }

  /**
   * A filter so we only show classes that are subclassed from Android SDK's Activity class.
   */
  private static final class OnlyShowActivities implements JavaCodeFragment.VisibilityChecker {
    @NotNull private final Module myModule;

    public OnlyShowActivities(@NotNull Module module) {
      myModule = module;
    }

    private static boolean isActivitySubclass(@NotNull PsiClass classDecl) {
      for (PsiClass superClass : classDecl.getSupers()) {
        String typename = superClass.getQualifiedName();
        if (SdkConstants.CLASS_ACTIVITY.equals(typename) || isActivitySubclass(superClass)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Visibility isDeclarationVisible(PsiElement declaration, @Nullable PsiElement place) {
      if (declaration instanceof PsiClass) {
        PsiClass classDecl = (PsiClass)declaration;
        if (PsiClassUtil.isRunnableClass(classDecl, true, true) &&
            isActivitySubclass(classDecl) && isOnClasspath(classDecl)) {
          return Visibility.VISIBLE;
        }
      }
      return Visibility.NOT_VISIBLE;
    }

    private boolean isOnClasspath(@NotNull PsiClass classDecl) {
      GlobalSearchScope scope = myModule.getModuleWithDependenciesAndLibrariesScope(false);
      VirtualFile file = classDecl.getContainingFile().getVirtualFile();
      return scope.contains(file);
    }
  }

  /**
   * An action listener that is triggered when the user hits the browse button, showing a dialog
   * in response which lets the user choose a target Activity class.
   */
  private static final class ChooseClassActionListener implements ActionListener {
    @NotNull private final Module myModule;
    @Nullable private ReferenceEditorComboWithBrowseButton myOwner;

    public ChooseClassActionListener(@NotNull Module module) {
      myModule = module;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final OnlyShowActivities filter = new OnlyShowActivities(myModule);
      Project project = myModule.getProject();
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
        .createWithInnerClassesScopeChooser("Select Activity", GlobalSearchScope.projectScope(project), new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass psiClass) {
            return filter.isDeclarationVisible(psiClass, null) == JavaCodeFragment.VisibilityChecker.Visibility.VISIBLE;
          }
        }, null);

      assert myOwner != null; // Should have been set by us above (in createComponent)!

      String currClass = myOwner.getText();
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(currClass, GlobalSearchScope.allScope(project));
      if (psiClass != null) {
        chooser.selectDirectory(psiClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      PsiClass selectedClass = chooser.getSelected();
      if (selectedClass != null) {
        myOwner.setText(Strings.nullToEmpty(selectedClass.getQualifiedName()));
      }
    }

    /**
     * Link this listener with the component it is listening to.
     *
     * It would have been nice if this was passed in via the actionPerformed event, but since it
     * wasn't, we allow explicit registration instead. (Note that this class is private, so even
     * if this is an ugly API detail, it's encapsulated).
     */
    public void setOwner(@NotNull ReferenceEditorComboWithBrowseButton owner) {
      myOwner = owner;
    }
  }
}
