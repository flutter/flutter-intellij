/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.npwOld.project;

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import java.io.File;
import java.util.Collection;

public class SourceProviderAdapter implements SourceProvider {
  public SourceProviderAdapter(String a, AndroidModuleTemplate b){}

  @NonNull
  @Override
  public String getName() {
    return null;
  }

  @NonNull
  @Override
  public File getManifestFile() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getJavaDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getResourcesDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getAidlDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getRenderscriptDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getCDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getCppDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getResDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getAssetsDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getJniLibsDirectories() {
    return null;
  }

  @NonNull
  @Override
  public Collection<File> getShadersDirectories() {
    return null;
  }
}
