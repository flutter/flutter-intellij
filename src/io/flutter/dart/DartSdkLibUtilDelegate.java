/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.openapi.module.Module;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

public class DartSdkLibUtilDelegate {
  private Class delegate;

  DartSdkLibUtilDelegate() {
    delegate = classForName("com.jetbrains.lang.dart.sdk.DartSdkGlobalLibUtil");
    if (delegate == null) {
      delegate = classForName("com.jetbrains.lang.dart.sdk.DartSdkLibUtil");
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

  private static Class classForName(String name) {
    try {
      return Class.forName(name);
    }
    catch (ClassNotFoundException cnfe) {
      return null;
    }
  }
}
