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
package com.android.tools.idea.npw.importing;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for common import UI code.
 */
public class ImportUIUtil {
  private ImportUIUtil() {
    // Do nothing
  }

  /**
   * Formats a message picking the format string depending on number of arguments
   *
   * @param values                       values that will be used as format argument.
   * @param oneElementMessage            message when only one value is in the list. Should accept one string argument.
   * @param twoOrThreeElementsMessage    message format when there's 2 or 3 values. Should accept two string arguments.
   * @param moreThenThreeElementsMessage message format for over 3 values. Should accept one string and one number.
   * @return formatted message string
   */
  public static String formatElementListString(Iterable<String> values,
                                               String oneElementMessage,
                                               String twoOrThreeElementsMessage,
                                               String moreThenThreeElementsMessage) {
    int size = Iterables.size(values);
    if (size <= 1) { // If there's 0 elements, some error happened
      return String.format(oneElementMessage, Iterables.getFirst(values, "<validation error>"));
    }
    else if (size <= 3) {
      return String.format(twoOrThreeElementsMessage, atMostTwo(values, size), Iterables.getLast(values));
    }
    else {
      return String.format(moreThenThreeElementsMessage, atMostTwo(values, size), size - 2);
    }
  }

  private static String atMostTwo(Iterable<String> names, int size) {
    return Joiner.on(", ").join(Iterables.limit(names, Math.min(size - 1, 2)));
  }

  /**
   * @deprecated Replaced by {@link com.android.tools.idea.ui.wizard.WizardUtils#toHtmlString(String)}
   */
  @Nullable
  public static String makeHtmlString(@Nullable String templateDescription) {
    if (!StringUtil.isEmpty(templateDescription) && !templateDescription.startsWith("<html>")) {
      templateDescription = String.format("<html>%1$s</html>", templateDescription.trim());
    }
    return templateDescription;
  }

  /**
   * Returns a relative path string to be shown in the UI. Wizard logic
   * operates with VirtualFile's so these paths are only for user. The paths
   * shown are relative to the file system location user specified, showing
   * relative paths will be easier for the user to read.
   */
  static String getRelativePath(@Nullable VirtualFile baseFile, @Nullable VirtualFile file) {
    if (file == null) {
      return "";
    }
    String path = file.getPath();
    if (baseFile == null) {
      return path;
    }
    else if (file.equals(baseFile)) {
      return ".";
    }
    else if (!baseFile.isDirectory()) {
      return getRelativePath(baseFile.getParent(), file);
    }
    else {
      String basePath = baseFile.getPath();
      if (path.startsWith(basePath + "/")) {
        return path.substring(basePath.length() + 1);
      }
      else if (file.getFileSystem().equals(baseFile.getFileSystem())) {
        StringBuilder builder = new StringBuilder(basePath.length());
        String prefix = Strings.commonPrefix(path, basePath);
        if (!prefix.endsWith("/")) {
          prefix = prefix.substring(0, prefix.lastIndexOf('/') + 1);
        }
        if (!path.startsWith(basePath)) {
          Iterable<String> segments = Splitter.on("/").split(basePath.substring(prefix.length()));
          Joiner.on("/").appendTo(builder, Iterables.transform(segments, Functions.constant("..")));
          builder.append("/");
        }
        builder.append(path.substring(prefix.length()));
        return builder.toString();
      }
      else {
        return path;
      }
    }
  }
}
