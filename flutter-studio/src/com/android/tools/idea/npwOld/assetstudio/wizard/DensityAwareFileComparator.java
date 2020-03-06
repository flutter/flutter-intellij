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
package com.android.tools.idea.npwOld.assetstudio.wizard;

import static com.android.tools.idea.npwOld.assetstudio.IconGenerator.pathToDensity;

import com.android.resources.Density;
import java.io.File;
import java.util.Comparator;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Orders files by "directory vs file" first, then by density, then by name.
 */
class DensityAwareFileComparator implements Comparator<File> {
  @NotNull private final Set<File> myDirectories;

  public DensityAwareFileComparator(@NotNull Set<File> directories) {
    myDirectories = directories;
  }

  @Override
  public int compare(@NotNull File file1, @NotNull File file2) {
    int c = -Boolean.compare(myDirectories.contains(file1), myDirectories.contains(file2));
    if (c != 0) {
      return c;
    }
    String path1 = file1.getAbsolutePath();
    String path2 = file2.getAbsolutePath();
    Density density1 = pathToDensity(path1 + File.separator);
    Density density2 = pathToDensity(path2 + File.separator);
    c = Boolean.compare(density1 != null, density2 != null); // Files and directories with a density suffix are sorted first.
    if (c != 0) {
      return c;
    }

    if (density1 != null && density2 != null && density1 != density2) {
      return Integer.compare(density2.getDpiValue(), density1.getDpiValue()); // Sort least dense to most dense.
    }

    return path1.compareTo(path2);
  }
}
