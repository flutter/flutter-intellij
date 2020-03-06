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
package com.android.tools.idea.npwOld.template;

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * A handle to various relevant information related to a target template.xml file.
 */
public final class TemplateHandle {
  private final File myRootPath;
  private final Template myTemplate;
  private final TemplateMetadata myMetadata;

  public TemplateHandle(@NotNull File rootPath) {
    myRootPath = rootPath;
    myTemplate = Template.createFromPath(rootPath);
    myMetadata = TemplateManager.getInstance().getTemplateMetadata(rootPath);
  }

  @NotNull
  public File getRootPath() {
    return myRootPath;
  }

  @NotNull
  public Template getTemplate() {
    return myTemplate;
  }

  @NotNull
  public TemplateMetadata getMetadata() {
    return myMetadata;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myRootPath, myMetadata.getTitle());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    else if (obj == this) {
      return true;
    }
    else {
      TemplateHandle another = (TemplateHandle)obj;
      return Objects.equal(myRootPath, another.myRootPath) && Objects.equal(myMetadata.getTitle(), another.myMetadata.getTitle());
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).
      add("title", myMetadata.getTitle()).
      add("path", myRootPath.getAbsolutePath()).toString();
  }
}
