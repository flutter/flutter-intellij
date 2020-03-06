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
package com.android.tools.idea.npwOld.project;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Class holding the UI needed for each tab of {@link ChooseAndroidProjectStep}
 */
public class ChooseAndroidProjectPanel<T> {
  final ASGallery<T> myGallery;

  JPanel myRootPanel;
  JBScrollPane myGalleryPanel;
  JBLabel myTemplateName;
  JBLabel myTemplateDesc;
  HyperlinkLabel myDocumentationLink;

  ChooseAndroidProjectPanel(@NotNull ASGallery<T> gallery) {
    myGallery = gallery;
    myGalleryPanel.setViewportView(gallery);

    myDocumentationLink.setHyperlinkText(message("android.wizard.activity.add.cpp.docslinktext"));
    myDocumentationLink.setHyperlinkTarget("https://developer.android.com/ndk/guides/cpp-support.html");
  }
}
