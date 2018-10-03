/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class InspectorMemoryTab extends JPanel implements InspectorTabPanel {
  private static final Logger LOG = Logger.getInstance(FlutterView.class);
  private @NotNull FlutterApp app;

  InspectorMemoryTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    add(Box.createVerticalStrut(6));

    Box labelBox = Box.createHorizontalBox();
    labelBox.add(new JLabel("Running in " + app.getLaunchMode() + " mode"));
    labelBox.add(Box.createHorizontalGlue());
    labelBox.setBorder(JBUI.Borders.empty(3, 10));
    add(labelBox);


    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      labelBox = Box.createHorizontalBox();
      labelBox.add(new JLabel("Note: for best results, re-run in profile mode"));
      labelBox.add(Box.createHorizontalGlue());
      labelBox.setBorder(JBUI.Borders.empty(3, 10));
      add(labelBox);
    }

    add(Box.createVerticalStrut(6));

    // Dynamically load, instantiate the Flutter Profiler classes (only
    // available in Android Studio) then add the component to the the inspector
    // Memory tab.
    String CLASS_FlutterStudioProfilers =
      "io.flutter.profiler.FlutterStudioProfilers";
    String CLASS_FlutterStudioProfilersView =
      "io.flutter.profiler.FlutterStudioProfilersView";

    try {
      // The below dynamic code mimics:
      //
      //        FlutterStudioProfilers fsp =
      //          new FlutterStudioProfilers(parentDisposable, app);
      //        FlutterStudioProfilersView view =
      //          new FlutterStudioProfilersView(fsp);
      //        add(view.getComponent(), BorderLayout.CENTER);

      Class flutterStudioProfilers_class = Class.forName(CLASS_FlutterStudioProfilers);

      Constructor flutterStudioProfilers_constructor =
        flutterStudioProfilers_class.getConstructor(Disposable.class, FlutterApp.class);
      Object flutterStudioProfilers_instance =
        flutterStudioProfilers_constructor.newInstance(parentDisposable, app);

      Class flutterStudioProfilersView_class =
        Class.forName(CLASS_FlutterStudioProfilersView);

      Constructor flutterStudioProfilersView_constructor =
        flutterStudioProfilersView_class.getConstructor(flutterStudioProfilers_class);
      Object flutterStudioProfilersView_instance =
        flutterStudioProfilersView_constructor.newInstance(flutterStudioProfilers_instance);

      Class noArguments[] = new Class[] {};
      Method getComponentMethod =
        flutterStudioProfilersView_class.getMethod("getComponent", noArguments);
      // call getComponent()
      Component component =
        (Component)getComponentMethod.invoke(flutterStudioProfilersView_instance, (Object[]) noArguments);

      add(component, BorderLayout.CENTER);

      // Start collecting immediately if memory profiling is enabled.
      assert app.getVMServiceManager() != null;
      app.getVMServiceManager().addPollingClient();
    }
    catch (ClassNotFoundException | NoSuchMethodException |
      InstantiationException | IllegalAccessException |
      InvocationTargetException e) {
      LOG.warn("Problem loading Flutter Memory Profiler - " + e.getMessage());

      labelBox = Box.createHorizontalBox();
      JLabel warningLabel = new JLabel(
          "WARNING: Flutter Memory Profiler only available in AndroidStudio.");

      // Bold the message.
      Font font = warningLabel.getFont();
      Font boldFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
      warningLabel.setFont(boldFont);

      labelBox.add(warningLabel);
      labelBox.add(Box.createHorizontalGlue());
      labelBox.setBorder(JBUI.Borders.empty(3, 10));
      add(labelBox);
    }
  }
  @Override
  public void finalize() {
    // Done collecting for the memory profiler - if this instance is GC'd.
    assert app.getVMServiceManager() != null;
    app.getVMServiceManager().removePollingClient();
  }

  @Override
  public void setVisibleToUser(boolean visible) { }

}
