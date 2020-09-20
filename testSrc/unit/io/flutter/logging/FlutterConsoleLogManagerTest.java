/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.JsonUtils;
import io.flutter.vmService.VMServiceManager;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

import javax.swing.*;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;

public class FlutterConsoleLogManagerTest {
  @Test
  public void testBasicLogging() {
    final ConsoleViewMock console = new ConsoleViewMock();
    final FlutterConsoleLogManager logManager = new FlutterConsoleLogManager(console, createFlutterApp());
    final Event event = new Event(JsonUtils.parseReader(
      new InputStreamReader(FlutterConsoleLogManagerTest.class.getResourceAsStream("console_log_1.json"))).getAsJsonObject());

    logManager.processLoggingEvent(event);

    assertEquals("[my.log] hello world\n", console.getText());
  }

  @Test
  public void testNoLoggerName() {
    final ConsoleViewMock console = new ConsoleViewMock();
    final FlutterConsoleLogManager logManager = new FlutterConsoleLogManager(console, createFlutterApp());
    final Event event = new Event(JsonUtils.parseReader(
      new InputStreamReader(FlutterConsoleLogManagerTest.class.getResourceAsStream("console_log_2.json"))).getAsJsonObject());

    logManager.processLoggingEvent(event);

    assertEquals("[log] hello world\n", console.getText());
  }

  @Test
  public void testWithError() {
    final ConsoleViewMock console = new ConsoleViewMock();
    final FlutterConsoleLogManager logManager = new FlutterConsoleLogManager(console, createFlutterApp());
    final Event event = new Event(JsonUtils.parseReader(
      new InputStreamReader(FlutterConsoleLogManagerTest.class.getResourceAsStream("console_log_3.json"))).getAsJsonObject());

    logManager.processLoggingEvent(event);

    assertEquals("[log] hello world\n      my sample error\n", console.getText());
  }

  @Test
  public void testWithStacktrace() {
    final ConsoleViewMock console = new ConsoleViewMock();
    final FlutterConsoleLogManager logManager = new FlutterConsoleLogManager(console, createFlutterApp());
    final Event event = new Event(JsonUtils.parseReader(
      new InputStreamReader(FlutterConsoleLogManagerTest.class.getResourceAsStream("console_log_4.json"))).getAsJsonObject());

    logManager.processLoggingEvent(event);

    assertEquals("[log] hello world\n" +
                 "      #0      MyApp.build.<anonymous closure> (package:xfactor_wod/main.dart:64:65)\n" +
                 "      #1      _InkResponseState._handleTap (package:flutter/src/material/ink_well.dart:635:14)\n" +
                 "      #2      _InkResponseState.build.<anonymous closure> (package:flutter/src/material/ink_well.dart:711:32)\n" +
                 "      #3      GestureRecognizer.invokeCallback (package:flutter/src/gestures/recognizer.dart:182:24)\n" +
                 "      #4      TapGestureRecognizer._checkUp (package:flutter/src/gestures/tap.dart:365:11)\n" +
                 "      #5      TapGestureRecognizer.handlePrimaryPointer (package:flutter/src/gestures/tap.dart:275:7)\n" +
                 "      #6      PrimaryPointerGestureRecognizer.handleEvent (package:flutter/src/gestures/recognizer.dart:455:9)\n" +
                 "      #7      PointerRouter._dispatch (package:flutter/src/gestures/pointer_router.dart:75:13)\n" +
                 "      #8      PointerRouter.route (package:flutter/src/gestures/pointer_router.dart:102:11)\n" +
                 "      #9      _WidgetsFlutterBinding&BindingBase&GestureBinding.handleEvent (package:flutter/src/gestures/binding.dart:218:19)\n" +
                 "      #10     _WidgetsFlutterBinding&BindingBase&GestureBinding.dispatchEvent (package:flutter/src/gestures/binding.dart:198:22)\n" +
                 "      #11     _WidgetsFlutterBinding&BindingBase&GestureBinding._handlePointerEvent (package:flutter/src/gestures/binding.dart:156:7)\n" +
                 "      #12     _WidgetsFlutterBinding&BindingBase&GestureBinding._flushPointerEventQueue (package:flutter/src/gestures/binding.dart:102:7)\n" +
                 "      #13     _WidgetsFlutterBinding&BindingBase&GestureBinding._handlePointerDataPacket (package:flutter/src/gestures/binding.dart:86:7)\n" +
                 "      #14     _rootRunUnary (dart:async/zone.dart:1136:13)\n" +
                 "      #15     _CustomZone.runUnary (dart:async/zone.dart:1029:19)\n" +
                 "      #16     _CustomZone.runUnaryGuarded (dart:async/zone.dart:931:7)\n" +
                 "      #17     _invoke1 (dart:ui/hooks.dart:250:10)\n" +
                 "      #18     _dispatchPointerDataPacket (dart:ui/hooks.dart:159:5)\n", console.getText());
  }

  private FlutterApp createFlutterApp() {
    final FlutterApp app = PowerMockito.mock(FlutterApp.class);

    PowerMockito.when(app.getVmService()).thenAnswer(mock -> PowerMockito.mock(VmService.class));
    PowerMockito.when(app.getFlutterDebugProcess()).thenAnswer(mock -> PowerMockito.mock(FlutterDebugProcess.class));
    PowerMockito.when(app.getVMServiceManager()).thenAnswer(mock -> PowerMockito.mock(VMServiceManager.class));

    return app;
  }
}

class ConsoleViewMock implements ConsoleView {
  StringBuilder builder = new StringBuilder();

  String getText() {
    return builder.toString();
  }

  @Override
  public void print(@NotNull String string, @NotNull ConsoleViewContentType type) {
    builder.append(string);
  }

  @Override
  public void clear() {
    builder = new StringBuilder();
  }

  @Override
  public void scrollTo(int i) {

  }

  @Override
  public void attachToProcess(ProcessHandler handler) {

  }

  @Override
  public void setOutputPaused(boolean b) {

  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {

  }

  @Override
  public void setHelpId(@NotNull String s) {

  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {

  }

  @Override
  public void printHyperlink(@NotNull String s, @Nullable HyperlinkInfo info) {

  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return new AnAction[0];
  }

  @Override
  public void allowHeavyFilters() {

  }

  @NotNull
  @Override
  public JComponent getComponent() {
    throw new Error("not supported");
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return null;
  }

  @Override
  public void dispose() {
  }
}