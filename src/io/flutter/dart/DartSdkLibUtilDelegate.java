/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * This class lets us delegate to either <code>DartSdkGlobalLibUtil</code> or
 * <code>DartSdkLibUtil</code>, depending on the version of the Dart plugin we're running with.
 */
public class DartSdkLibUtilDelegate {
  private Class delegate;
  private boolean isGlobalSdk = true;

  DartSdkLibUtilDelegate() {
    delegate = classForName("com.jetbrains.lang.dart.sdk.DartSdkGlobalLibUtil");
    if (delegate == null) {
      delegate = classForName("com.jetbrains.lang.dart.sdk.DartSdkLibUtil");
      isGlobalSdk = false;
    }
    assert delegate != null;
  }

  public boolean isDartSdkEnabled(Module module) {
    try {
      //noinspection unchecked
      final Method method = delegate.getMethod("isDartSdkEnabled", Module.class);
      return (Boolean)method.invoke(null, module);
    }
    catch (IllegalAccessException | NoSuchMethodException e) {
      return false;
    }
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IllegalArgumentException) {
        throw (IllegalArgumentException)e.getTargetException();
      }
      else {
        throw new RuntimeException(e.getTargetException());
      }
    }
  }

  public void enableDartSdk(Module module) {
    assert ApplicationManager.getApplication().isWriteAccessAllowed();
    try {
      //noinspection unchecked
      final Method method = delegate.getMethod("enableDartSdk", Module.class);
      method.invoke(null, module);
    }
    catch (IllegalAccessException | NoSuchMethodException ignored) {
    }
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IllegalArgumentException) {
        throw (IllegalArgumentException)e.getTargetException();
      }
      else {
        throw new RuntimeException(e.getTargetException());
      }
    }
  }

  public void disableDartSdk(Collection<Module> modules) {
    assert ApplicationManager.getApplication().isWriteAccessAllowed();
    try {
      //noinspection unchecked
      final Method method = delegate.getMethod("disableDartSdk", Collection.class);
      method.invoke(null, modules);
    }
    catch (IllegalAccessException | NoSuchMethodException ignored) {
    }
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IllegalArgumentException) {
        throw (IllegalArgumentException)e.getTargetException();
      }
      else {
        throw new RuntimeException(e.getTargetException());
      }
    }
  }

  public void ensureDartSdkConfigured(@Nullable Project project, String sdkHomePath) {
    try {
      if (isGlobalSdk) {
        //noinspection unchecked
        final Method method = delegate.getMethod("ensureDartSdkConfigured", String.class);
        method.invoke(null, sdkHomePath);
      }
      else {
        //noinspection unchecked
        final Method method = delegate.getMethod("ensureDartSdkConfigured", Project.class, String.class);
        method.invoke(null, project, sdkHomePath);
      }
    }
    catch (IllegalAccessException | NoSuchMethodException ignored) {
    }
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IllegalArgumentException) {
        throw (IllegalArgumentException)e.getTargetException();
      }
      else {
        throw new RuntimeException(e.getTargetException());
      }
    }
  }

  private static Class classForName(String name) {
    try {
      return Class.forName(name);
    }
    catch (ClassNotFoundException cnfe) {
      return null;
    }
  }
}
