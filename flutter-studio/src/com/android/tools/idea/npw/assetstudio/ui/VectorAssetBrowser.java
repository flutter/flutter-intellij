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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A text field with a browse button which wraps a {@link VectorAsset}, which allows the user
 * to specify a path to an SVG or PSD file.
 */
public final class VectorAssetBrowser extends TextFieldWithBrowseButton implements AssetComponent<VectorAsset>, Disposable {
  private final VectorAsset myAsset = new VectorAsset();
  private final BindingsManager myBindings = new BindingsManager();

  private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  public VectorAssetBrowser() {
    addBrowseFolderListener(null, null, null, createMultiFileDescriptor("svg", "psd"));

    StringProperty absolutePath = new TextProperty(getTextField());
    myBindings.bind(absolutePath, myAsset.path().transform(File::getAbsolutePath));
    myBindings.bind(myAsset.path(), absolutePath.transform(File::new));

    myAsset.path().addListener(sender -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myAssetListeners) {
        listener.actionPerformed(e);
      }
    });
  }

  @Override
  @NotNull
  public VectorAsset getAsset() {
    return myAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener l) {
    myAssetListeners.add(l);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myAssetListeners.clear();
  }

  private static FileChooserDescriptor createMultiFileDescriptor(@NotNull String... extensions) {
    return new FileChooserDescriptor(true, false, false, false, false, false).withFileFilter(
        file -> Arrays.stream(extensions).anyMatch(e -> Comparing.equal(file.getExtension(), e, SystemInfo.isFileSystemCaseSensitive)));
  }
}
