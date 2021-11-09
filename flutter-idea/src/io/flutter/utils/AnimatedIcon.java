/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.icons.AllIcons;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public class AnimatedIcon implements Icon {
  public interface Frame {
    @NotNull
    Icon getIcon();

    int getDelay();
  }

  public static final class Default extends AnimatedIcon {
    public Default() {
      super(100,
            AllIcons.Process.Step_1,
            AllIcons.Process.Step_2,
            AllIcons.Process.Step_3,
            AllIcons.Process.Step_4,
            AllIcons.Process.Step_5,
            AllIcons.Process.Step_6,
            AllIcons.Process.Step_7,
            AllIcons.Process.Step_8);
    }
  }

  public static final class Big extends AnimatedIcon {
    public Big() {
      super(100,
            AllIcons.Process.Big.Step_1,
            AllIcons.Process.Big.Step_2,
            AllIcons.Process.Big.Step_3,
            AllIcons.Process.Big.Step_4,
            AllIcons.Process.Big.Step_5,
            AllIcons.Process.Big.Step_6,
            AllIcons.Process.Big.Step_7,
            AllIcons.Process.Big.Step_8);
    }
  }

  public static final class Grey extends AnimatedIcon {
    public Grey() {
      super(150,
            FlutterIcons.State.GreyProgr_1,
            FlutterIcons.State.GreyProgr_2,
            FlutterIcons.State.GreyProgr_3,
            FlutterIcons.State.GreyProgr_4,
            FlutterIcons.State.GreyProgr_5,
            FlutterIcons.State.GreyProgr_6,
            FlutterIcons.State.GreyProgr_7,
            FlutterIcons.State.GreyProgr_8);
    }
  }

  public static final class FS extends AnimatedIcon {
    public FS() {
      super(50,
            AllIcons.Process.FS.Step_1,
            AllIcons.Process.FS.Step_2,
            AllIcons.Process.FS.Step_3,
            AllIcons.Process.FS.Step_4,
            AllIcons.Process.FS.Step_5,
            AllIcons.Process.FS.Step_6,
            AllIcons.Process.FS.Step_7,
            AllIcons.Process.FS.Step_8,
            AllIcons.Process.FS.Step_9,
            AllIcons.Process.FS.Step_10,
            AllIcons.Process.FS.Step_11,
            AllIcons.Process.FS.Step_12,
            AllIcons.Process.FS.Step_13,
            AllIcons.Process.FS.Step_14,
            AllIcons.Process.FS.Step_15,
            AllIcons.Process.FS.Step_16,
            AllIcons.Process.FS.Step_17,
            AllIcons.Process.FS.Step_18);
    }
  }


  private final Frame[] frames;
  private boolean requested;
  private long time;
  private int index;
  private Frame frame;

  public AnimatedIcon(int delay, @NotNull Icon... icons) {
    this(getFrames(delay, icons));
  }

  public AnimatedIcon(@NotNull Frame... frames) {
    this.frames = frames;
    assert frames.length > 0 : "empty array";
    for (Frame frame : frames) assert frame != null : "null animation frame";
    updateFrameAt(java.lang.System.currentTimeMillis());
  }

  private static Frame[] getFrames(int delay, @NotNull Icon... icons) {
    final int length = icons.length;
    assert length > 0 : "empty array";
    final Frame[] frames = new Frame[length];
    for (int i = 0; i < length; i++) {
      final Icon icon = icons[i];
      assert icon != null : "null icon";
      frames[i] = new Frame() {
        @NotNull
        @Override
        public Icon getIcon() {
          return icon;
        }

        @Override
        public int getDelay() {
          return delay;
        }
      };
    }
    return frames;
  }

  private void updateFrameAt(long current) {
    if (frames.length <= index) index = 0;
    frame = frames[index++];
    time = current;
  }

  private Icon getUpdatedIcon() {
    final long current = java.lang.System.currentTimeMillis();
    if (frame.getDelay() <= (current - time)) updateFrameAt(current);
    return frame.getIcon();
  }

  @Override
  public final void paintIcon(Component c, Graphics g, int x, int y) {
    final Icon icon = getUpdatedIcon();
    if (!requested && canRefresh(c)) {
      final int delay = frame.getDelay();
      if (delay > 0) {
        requested = true;
        final Timer timer = new Timer(delay, event -> {
          requested = false;
          if (canRefresh(c)) {
            doRefresh(c);
          }
        });
        timer.setRepeats(false);
        timer.start();
      }
      else {
        doRefresh(c);
      }
    }
    icon.paintIcon(c, g, x, y);
  }

  @Override
  public final int getIconWidth() {
    return getUpdatedIcon().getIconWidth();
  }

  @Override
  public final int getIconHeight() {
    return getUpdatedIcon().getIconHeight();
  }

  protected boolean canRefresh(Component component) {
    return component != null && component.isShowing();
  }

  protected void doRefresh(Component component) {
    if (component != null) component.repaint();
  }
}
