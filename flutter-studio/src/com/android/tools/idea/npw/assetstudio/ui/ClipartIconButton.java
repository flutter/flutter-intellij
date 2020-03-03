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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.ide.common.vectordrawable.VdIcon;
import com.android.tools.idea.npw.assetstudio.MaterialDesignIcons;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.observable.BindingsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.io.FileUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Button that allows user to select an icon from a list of icons each associated with a XML file
 * representing a vector asset.
 */
public final class ClipartIconButton extends JButton
    implements AssetComponent<ImageAsset>, Disposable, PersistentStateComponent<PersistentState> {
  private static final String URL_PROPERTY = "url";

  private final ImageAsset myXmlAsset = new ImageAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  @Nullable private VdIcon myIcon;

  public ClipartIconButton() {
    myXmlAsset.setClipart(true);
    myXmlAsset.setRole("clipart image");
    addActionListener(actionEvent -> {
      IconPickerDialog iconPicker = new IconPickerDialog(myIcon);
      if (iconPicker.showAndGet()) {
        VdIcon selectedIcon = iconPicker.getSelectedIcon();
        assert selectedIcon != null; // Not null if user pressed OK.
        updateIcon(selectedIcon);
      }
    });

    myXmlAsset.imagePath().addListener(sender -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myAssetListeners) {
        listener.actionPerformed(e);
      }
    });

    updateIcon(createIcon(MaterialDesignIcons.getDefaultIcon()));
  }

  private void updateIcon(@Nullable VdIcon selectedIcon) {
    myIcon = null;
    setIcon(null);
    if (selectedIcon != null) {
      try {
        // The VectorAsset class works with files, but IconPicker returns resources from a jar.
        // Adapt by saving the resource into a temporary file.
        File iconFile = new File(FileUtil.getTempDirectory(), selectedIcon.getName());
        InputStream iconStream = selectedIcon.getURL().openStream();
        FileOutputStream outputStream = new FileOutputStream(iconFile);
        FileUtil.copy(iconStream, outputStream);
        myXmlAsset.imagePath().setValue(iconFile);
        // Our icons are always square, so although renderIcon() expects width, we can pass in height.
        int h = getHeight() - getInsets().top - getInsets().bottom;
        BufferedImage image = selectedIcon.renderIcon(h, h);
        if (image != null) {
          // Switch foreground to white instead?
          image = VdIcon.adjustIconColor(this, image);
          setIcon(new ImageIcon(image));
        }
        myIcon = selectedIcon;
      }
      catch (IOException ignored) {
      }
    }
  }

  @NotNull
  @Override
  public ImageAsset getAsset() {
    return myXmlAsset;
  }

  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myAssetListeners.add(listener);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myAssetListeners.clear();
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    URL defaultIconUrl = MaterialDesignIcons.getDefaultIcon();
    URL iconUrl = myIcon == null ? defaultIconUrl : myIcon.getURL();
    state.set(URL_PROPERTY, iconUrl.toString(), defaultIconUrl.toString());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    URL defaultIconUrl = MaterialDesignIcons.getDefaultIcon();
    URL iconUrl;
    try {
      iconUrl = new URL(state.get(URL_PROPERTY, defaultIconUrl.toString()));
    }
    catch (MalformedURLException e) {
      iconUrl = defaultIconUrl;
    }

    updateIcon(createIcon(iconUrl));
    if (myIcon == null && iconUrl.equals(defaultIconUrl)) {
      updateIcon(createIcon(defaultIconUrl));
    }
  }

  @Nullable
  private static VdIcon createIcon(@NotNull URL url) {
    try {
      return new VdIcon(url);
    }
    catch (IOException e) {
      return null;
    }
  }
}
