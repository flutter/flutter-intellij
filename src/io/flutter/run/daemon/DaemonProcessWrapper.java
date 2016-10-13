/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DaemonProcessWrapper extends ProcessHandler {
  private ProcessHandler myHandler;

  DaemonProcessWrapper(ProcessHandler handler) {
    super();
    myHandler = handler;
  }

  public void startNotify() {
    myHandler.startNotify();
  }

  @Override
  public void destroyProcessImpl() {
    reflectivelyInvoke("destroyProcessImpl");
  }

  @Override
  public void detachProcessImpl() {
    reflectivelyInvoke("detachProcessImpl");
  }

  @Override
  public boolean detachIsDefault() {
    return myHandler.detachIsDefault();
  }

  public boolean waitFor() {
    return myHandler.waitFor();
  }

  public boolean waitFor(long timeoutInMilliseconds) {
    return myHandler.waitFor(timeoutInMilliseconds);
  }

  public void destroyProcess() {
    myHandler.destroyProcess();
  }

  public void detachProcess() {
    myHandler.detachProcess();
  }

  public boolean isProcessTerminated() {
    return myHandler.isProcessTerminated();
  }

  public boolean isProcessTerminating() {
    return myHandler.isProcessTerminating();
  }

  public Integer getExitCode() {
    return myHandler.getExitCode();
  }

  public void addProcessListener(final ProcessListener listener) {
    myHandler.addProcessListener(listener);
  }

  public void removeProcessListener(final ProcessListener listener) {
    myHandler.removeProcessListener(listener);
  }

  public void notifyProcessDetached() {
    reflectivelyInvoke("notifyProcessDetached");
  }

  public void notifyProcessTerminated(final int exitCode) {
    reflectivelyInvoke("notifyProcessTerminated");
  }

  public void notifyTextAvailable(final String text, final Key outputType) {
    myHandler.notifyTextAvailable(text, outputType);
  }

  @Nullable
  @Override
  public OutputStream getProcessInput() {
    return myHandler.getProcessInput();
  }

  public boolean isStartNotified() {
    return myHandler.isStartNotified();
  }

  public boolean isSilentlyDestroyOnClose() {
    return myHandler.isSilentlyDestroyOnClose();
  }

  private void reflectivelyInvoke(String methodName) {
    try {
      Method method = myHandler.getClass().getDeclaredMethod(methodName);
      method.setAccessible(true);
      method.invoke(myHandler);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
      // ignore
    }
  }
}
