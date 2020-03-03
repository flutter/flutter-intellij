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

import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.utils.SdkUtils;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;

/**
 * Panel which wraps a {@link ImageAsset}, allowing the user to browse for an image file to use as
 * an asset.
 */
public final class ImageAssetBrowser extends TextFieldWithBrowseButton implements AssetComponent<ImageAsset> {
  private static final String[] IMAGE_FILE_SUFFIXES = getImageFileSuffixes();

  @NotNull private final ImageAsset myImageAsset = new ImageAsset();
  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final List<ActionListener> myListeners = new ArrayList<>(1);

  public ImageAssetBrowser() {
    addBrowseFolderListener(null, null, null,
                            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withFileFilter(ImageAssetBrowser::isImageFile));

    TextProperty imagePathText = new TextProperty(getTextField());
    myBindings.bind(imagePathText, myImageAsset.imagePath().transform(file -> file.map(File::getAbsolutePath).orElse("")));
    myBindings.bind(myImageAsset.imagePath(),
                    imagePathText.transform(s -> StringUtil.isEmptyOrSpaces(s) ? Optional.empty() : Optional.of(new File(s.trim()))));

    InvalidationListener onImageChanged = sender -> {
      ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myListeners) {
        listener.actionPerformed(event);
      }
    };
    myImageAsset.imagePath().addListener(onImageChanged);
  }

  @Override
  @NotNull
  public ImageAsset getAsset() {
    return myImageAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.clear();
  }

  private static boolean isImageFile(@NotNull VirtualFile file) {
    String path = file.getPath();
    for (String suffix : IMAGE_FILE_SUFFIXES) {
      if (SdkUtils.endsWithIgnoreCase(path, suffix) &&
          path.length() > suffix.length() && path.charAt(path.length() - suffix.length() - 1) == '.') {
        return true;
      }
    }
    return false;
  }

  private static String[] getImageFileSuffixes() {
    String[] rasterSuffixes = ImageIO.getReaderFileSuffixes();
    String[] suffixes = new String[rasterSuffixes.length + 2];
    suffixes[0] = "svg";
    suffixes[1] = "xml";
    System.arraycopy(rasterSuffixes, 0, suffixes, 2, rasterSuffixes.length);
    return suffixes;
  }
}
