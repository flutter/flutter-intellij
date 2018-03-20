/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Support for displaying a component selection.
 */
public class SelectionEditPolicy {
  private final JPanel myHandleLayer;
  private final JComponent myComponent;

  private boolean myIsActive = false;
  private final List<Handle> myHandles = new ArrayList<>();

  public SelectionEditPolicy(JPanel handleLayer, JComponent component) {
    this.myHandleLayer = handleLayer;
    this.myComponent = component;
  }

  public void activate() {
    if (!myIsActive) {
      myIsActive = true;

      myHandles.add(new MoveHandle());

      final Rectangle componentBoundsInLayer =
        SwingUtilities.convertRectangle(myComponent.getParent(), myComponent.getBounds(), myHandleLayer);

      for (Handle handle : myHandles) {
        handle.updateBounds(componentBoundsInLayer);
        myHandleLayer.add(handle);
      }
    }
  }

  public void deactivate() {
    if (myIsActive) {
      myIsActive = false;

      for (Handle handle : myHandles) {
        final Container parent = handle.getParent();
        if (parent != null) {
          parent.remove(handle);
        }
      }
      myHandles.clear();
    }
  }
}
