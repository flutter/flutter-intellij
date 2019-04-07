/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InspectorMemoryTab extends JPanel implements InspectorTabPanel {
  private static final Logger LOG = Logger.getInstance(InspectorMemoryTab.class);
  private @NotNull final FlutterApp app;

  InspectorMemoryTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BorderLayout());

    // Dynamically load, instantiate the Flutter Profiler classes (only
    // available in Android Studio) then add the component to the the inspector
    // Memory tab.
    String CLASS_FlutterStudioProfilers = "io.flutter.profiler.FlutterStudioProfilers";
    String CLASS_FlutterStudioProfilersView = "io.flutter.profiler.FlutterStudioProfilersView";

    try {
      // The below dynamic code mimics:
      //
      //        FlutterStudioProfilers fsp =
      //          new FlutterStudioProfilers(parentDisposable, app);
      //        FlutterStudioProfilersView view =
      //          new FlutterStudioProfilersView(fsp);
      //        add(view.getComponent(), BorderLayout.CENTER);

      final Class flutterStudioProfilers_class = Class.forName(CLASS_FlutterStudioProfilers);

      @SuppressWarnings("unchecked") final Constructor flutterStudioProfilers_constructor =
        flutterStudioProfilers_class.getConstructor(Disposable.class, FlutterApp.class);
      final Object flutterStudioProfilers_instance =
        flutterStudioProfilers_constructor.newInstance(parentDisposable, app);

      final Class flutterStudioProfilersView_class =
        Class.forName(CLASS_FlutterStudioProfilersView);

      @SuppressWarnings("unchecked") final Constructor flutterStudioProfilersView_constructor =
        flutterStudioProfilersView_class.getConstructor(flutterStudioProfilers_class);
      final Object flutterStudioProfilersView_instance =
        flutterStudioProfilersView_constructor.newInstance(flutterStudioProfilers_instance);

      final Class[] noArguments = new Class[]{ };
      @SuppressWarnings("unchecked") final Method getComponentMethod =
        flutterStudioProfilersView_class.getMethod("getComponent", noArguments);
      // call getComponent()
      @SuppressWarnings("JavaReflectionInvocation") final Component component =
        (Component)getComponentMethod.invoke(flutterStudioProfilersView_instance, (Object[])noArguments);

      final JPanel labels = new JPanel(new BorderLayout(6, 0));
      labels.setBorder(JBUI.Borders.empty(3, 10));
      add(labels, BorderLayout.NORTH);

      labels.add(
        new JBLabel("Running in " + app.getLaunchMode() + " mode"),
        BorderLayout.WEST);

      if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
        final JBLabel label = new JBLabel("(note: for best results, re-run in profile mode)");
        label.setForeground(JBColor.RED);
        labels.add(label, BorderLayout.CENTER);
      }

      add(component, BorderLayout.CENTER);

      // Start collecting immediately if memory profiling is enabled.
      assert app.getVMServiceManager() != null;
      app.getVMServiceManager().addPollingClient();
    }
    catch (ClassNotFoundException | NoSuchMethodException | NoClassDefFoundError |
      InstantiationException | IllegalAccessException |
      InvocationTargetException e) {
      LOG.info("Unable to load Flutter Memory Profiler: " + e.getMessage());

      final JLabel warningLabel = new JLabel(
        "Memory profiling is only available in Android Studio.", null, SwingConstants.CENTER);
      warningLabel.setFont(JBFont.create(warningLabel.getFont()).asBold());
      warningLabel.setBorder(JBUI.Borders.empty(3, 10));
      add(warningLabel, BorderLayout.CENTER);
    }
  }

  @Override
  public void finalize() {
    // Done collecting for the memory profiler - if this instance is GC'd.
    assert app.getVMServiceManager() != null;
    app.getVMServiceManager().removePollingClient();
  }

  @Override
  public void setVisibleToUser(boolean visible) {
  }
}
