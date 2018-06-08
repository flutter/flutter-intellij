/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.indexing;

import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.indexing.IndexableSetContributor;
import io.flutter.android.GradleDependencyFetcher;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidModuleRootsProvider extends IndexableSetContributor {
  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
    GradleDependencyFetcher fetcher = new GradleDependencyFetcher(project);
    fetcher.run();
    Map<String, List<String>> deps = fetcher.getDependencies();
    Set<String> paths = new HashSet<>();
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    List<String> single = Collections.emptyList();
    for (List<String> list : deps.values()) {
      if (list.size() > single.size()) {
        single = list;
      }
    }
    single.forEach(path -> {
      if (path.startsWith("/")) { //TODO(messick): Windows?
        paths.add(path);
      }
    });
    Set<VirtualFile> roots = new HashSet<>();
    paths.forEach(path -> {
      VirtualFile vf = lfs.findFileByPath(path);
      if (vf != null) {
        roots.add(vf);
      }
    });
    return roots;
  }

  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    return Collections.emptySet();
  }
}
