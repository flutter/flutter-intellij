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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages list of modules.
 */
public final class ModuleListModel {
  @Nullable private final Project myProject;
  private Map<ModuleToImport, ModuleValidationState> myModules;
  private Multimap<ModuleToImport, ModuleToImport> myRequiredModules;
  @Nullable private VirtualFile mySelectedDirectory;
  private Map<ModuleToImport, String> myNameOverrides = Maps.newHashMap();
  private ModuleToImport myPrimaryModule;
  private Map<ModuleToImport, Boolean> myExplicitSelection = Maps.newHashMap();

  public ModuleListModel(@Nullable Project project) {
    myProject = project;
  }

  @Nullable
  private static ModuleToImport findPrimaryModule(@Nullable VirtualFile directory, @NotNull Iterable<ModuleToImport> modules) {
    if (directory == null) {
      return null;
    }
    for (ModuleToImport module : modules) {
      if (Objects.equal(module.location, directory)) {
        return module;
      }
    }
    return null;
  }

  private static boolean isValidModuleName(String moduleName) {
    if (StringUtil.isEmpty(moduleName)) {
      return false;
    }
    int previousSegmentStart = 0;
    for (int segmentSeparator = moduleName.indexOf(':', previousSegmentStart);
         segmentSeparator >= 0;
         segmentSeparator = moduleName.indexOf(':', previousSegmentStart)) {
      if (!isValidPathSegment(moduleName, previousSegmentStart, segmentSeparator)) {
        return false;
      }
      previousSegmentStart = segmentSeparator + 1;
    }
    return isValidPathSegment(moduleName, previousSegmentStart, moduleName.length());
  }

  private static boolean isValidPathSegment(String string, int segmentStart, int segmentEnd) {
    if (segmentEnd == segmentStart) {
      return segmentStart == 0; // Only allowed at string start to allow for absolute paths
    }
    String segment = string.substring(segmentStart, segmentEnd);
    return !StringUtil.isEmpty(segment) && GradleUtil.isValidGradlePath(segment) < 0;
  }

  private static String getNameErrorMessage(String moduleName) {
    if (StringUtil.isEmptyOrSpaces(moduleName)) {
      return "Module name is empty";
    }
    else {
      return "Module name is not valid";
    }
  }

  private Multimap<ModuleToImport, ModuleToImport> computeRequiredModules(Set<ModuleToImport> modules) {
    Map<String, ModuleToImport> namesToModules = Maps.newHashMapWithExpectedSize(modules.size());
    // We only care about modules we are actually going to import.
    for (ModuleToImport module : modules) {
      namesToModules.put(module.name, module);
    }
    Multimap<ModuleToImport, ModuleToImport> requiredModules = LinkedListMultimap.create();
    Queue<ModuleToImport> queue = Lists.newLinkedList();

    for (ModuleToImport module : modules) {
      if (Objects.equal(module, myPrimaryModule) || !isUnselected(module, false)) {
        queue.add(module);
      }
    }
    while (!queue.isEmpty()) {
      ModuleToImport moduleToImport = queue.remove();
      for (ModuleToImport dep : Iterables.transform(moduleToImport.getDependencies(), Functions.forMap(namesToModules, null))) {
        if (dep != null) {
          if (!requiredModules.containsKey(dep)) {
            queue.add(dep);
          }
          requiredModules.put(dep, moduleToImport);
        }
      }
    }
    return requiredModules;
  }

  private boolean isUnselected(ModuleToImport module, boolean isSelected) {
    if (module.location == null) {
      return true;
    }
    else if (Objects.equal(myPrimaryModule, module)) {
      return false;
    }
    else if (myModules.get(module) == ModuleValidationState.ALREADY_EXISTS) {
      return !Objects.equal(true, myExplicitSelection.get(module));
    }
    else {
      return !isSelected && isExplicitlyUnselected(module);
    }
  }

  private ModuleValidationState validateModule(ModuleToImport module) {
    VirtualFile location = module.location;
    if (location == null || !location.exists()) {
      return ModuleValidationState.NOT_FOUND;
    }
    String moduleName = getModuleName(module);
    if (!isValidModuleName(moduleName)) {
      return ModuleValidationState.INVALID_NAME;
    }
    else if (GradleUtil.hasModule(myProject, moduleName)) {
      return ModuleValidationState.ALREADY_EXISTS;
    }
    else {
      return ModuleValidationState.OK;
    }
  }

  public void setContents(@Nullable VirtualFile selectedDirectory, @NotNull Iterable<ModuleToImport> modules) {
    mySelectedDirectory = selectedDirectory;
    myPrimaryModule = findPrimaryModule(selectedDirectory, modules);
    revalidate(modules);
  }

  private void checkForDuplicateNames() {
    Collection<ModuleToImport> modules = getSelectedModules();
    ImmutableMultiset<String> names = ImmutableMultiset.copyOf(Iterables.transform(modules, new Function<ModuleToImport, String>() {
      @Override
      public String apply(@Nullable ModuleToImport input) {
        return input == null ? null : getModuleName(input);
      }
    }));
    for (ModuleToImport module : modules) {
      ModuleValidationState state = myModules.get(module);
      if (state == ModuleValidationState.OK) {
        if (names.count(getModuleName(module)) > 1) {
          myModules.put(module, ModuleValidationState.DUPLICATE_MODULE_NAME);
        }
      }
    }
  }

  public Set<ModuleToImport> getSelectedModules() {
    return ImmutableSet.copyOf(Iterables.filter(myModules.keySet(), new Predicate<ModuleToImport>() {
      @Override
      public boolean apply(@Nullable ModuleToImport input) {
        assert input != null;
        return isSelected(input);
      }
    }));
  }

  public boolean hasPrimary() {
    return myPrimaryModule != null;
  }

  public String getModuleName(ModuleToImport module) {
    if (myNameOverrides.containsKey(module) && !isRequiredModule(module)) {
      return myNameOverrides.get(module);
    }
    return module.name;
  }

  @VisibleForTesting
  public ModuleValidationState getModuleState(ModuleToImport module) {
    if (module == null) {
      return ModuleValidationState.NULL;
    }
    ModuleValidationState state = myModules.get(module);
    if (state == ModuleValidationState.OK && isRequiredModule(module)) {
      return ModuleValidationState.REQUIRED;
    }
    else {
      return state;
    }
  }

  private boolean isRequiredModule(ModuleToImport module) {
    return myRequiredModules.containsKey(module);
  }

  private Map<ModuleToImport, ModuleValidationState> validateModules(Iterable<ModuleToImport> modules) {
    Map<ModuleToImport, ModuleValidationState> result = Maps.newHashMap();
    for (ModuleToImport module : modules) {
      result.put(module, validateModule(module));
    }
    return result;
  }

  public void setSelected(ModuleToImport module, boolean isSelected) {
    myExplicitSelection.put(module, isSelected);
    revalidate(myModules.keySet());
  }

  private void revalidate(Iterable<ModuleToImport> modules) {
    myModules = validateModules(modules);
    myRequiredModules = computeRequiredModules(myModules.keySet());
    for (ModuleToImport module : myRequiredModules.keySet()) {
      myNameOverrides.remove(module);
    }
    checkForDuplicateNames();
  }

  public void setModuleName(ModuleToImport module, @Nullable String newName) {
    if (!isExplicitlyUnselected(module)) {
      if (newName == null) {
        myNameOverrides.remove(module);
      }
      else {
        myNameOverrides.put(module, newName);
      }
      revalidate(myModules.keySet());
    }
  }

  private boolean isExplicitlyUnselected(ModuleToImport module) {
    return Objects.equal(false, myExplicitSelection.get(module));
  }

  @Nullable
  public MessageType getStatusSeverity(ModuleToImport module) {
    ModuleValidationState state = getModuleState(module);
    switch (state) {
      case OK:
      case NULL:
        return null;
      case NOT_FOUND:
      case DUPLICATE_MODULE_NAME:
      case INVALID_NAME:
        return MessageType.ERROR;
      case ALREADY_EXISTS:
        return getSelectedModules().contains(module) ? MessageType.ERROR : MessageType.WARNING;
      case REQUIRED:
        return MessageType.INFO;
    }
    throw new IllegalArgumentException(state.name());
  }

  @Nullable
  public String getStatusDescription(@NotNull ModuleToImport module) {
    ModuleValidationState state = getModuleState(module);
    switch (state) {
      case OK:
      case NULL:
        return null;
      case NOT_FOUND:
        return "Module sources not found";
      case ALREADY_EXISTS:
        if (isSelected(module) && isRequiredModule(module)) {
          return "Cannot rename module required by another";
        }
        return "Project already contains module with this name";
      case DUPLICATE_MODULE_NAME:
        return "More then one module with this name is selected";
      case REQUIRED:
        Iterable<String> requiredBy = Iterables.transform(myRequiredModules.get(module), new Function<ModuleToImport, String>() {
          @Override
          public String apply(ModuleToImport input) {
            return "'" + getModuleName(input) + "'";
          }
        });
        return ImportUIUtil.formatElementListString(requiredBy, "Required by module %s", "Required by modules %s and %s",
                                                    "Required by modules %s and %d more");
      case INVALID_NAME:
        return getNameErrorMessage(getModuleName(module));
    }
    throw new IllegalStateException(state.name());
  }

  @Nullable
  public ModuleToImport getPrimary() {
    return myPrimaryModule;
  }

  @Nullable
  public VirtualFile getCurrentPath() {
    return mySelectedDirectory;
  }

  public Collection<ModuleToImport> getAllModules() {
    return ImmutableList.copyOf(myModules.keySet());
  }

  public boolean isSelected(@NotNull ModuleToImport module) {
    return !isUnselected(module, isRequiredModule(module));
  }

  public boolean canToggleModuleSelection(ModuleToImport module) {
    ModuleValidationState state = getModuleState(module);
    return state != ModuleValidationState.NOT_FOUND && !isRequiredModule(module);
  }

  public boolean canRename(ModuleToImport module) {
    return !isRequiredModule(module) && isSelected(module);
  }

  @VisibleForTesting
  public enum ModuleValidationState {
    OK, NULL, NOT_FOUND, ALREADY_EXISTS, REQUIRED, DUPLICATE_MODULE_NAME, INVALID_NAME
  }
}
