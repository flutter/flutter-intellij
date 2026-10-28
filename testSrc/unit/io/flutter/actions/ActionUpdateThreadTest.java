/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import io.flutter.android.AndroidEmulator;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that actions explicitly define {@link ActionUpdateThread#BGT}
 * for modern IntelliJ Platform (2024.1+) threading compliance.
 */
public class ActionUpdateThreadTest {

  @Test
  public void testFlutterGettingStartedAction_usesBgt() {
    final FlutterGettingStartedAction action = new FlutterGettingStartedAction();
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testFlutterSubmitFeedback_usesBgt() {
    final FlutterSubmitFeedback action = new FlutterSubmitFeedback();
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testRefreshToolWindowAction_usesBgt() {
    final RefreshToolWindowAction defaultAction = new RefreshToolWindowAction();
    assertEquals(ActionUpdateThread.BGT, defaultAction.getActionUpdateThread());

    final RefreshToolWindowAction idAction = new RefreshToolWindowAction("Flutter Inspector");
    assertEquals(ActionUpdateThread.BGT, idAction.getActionUpdateThread());
  }

  @Test
  public void testFlutterSdkActionBase_usesBgt() {
    final FlutterSdkAction action = new FlutterSdkAction() {
      @Override
      public void startCommand(@NotNull Project project,
                               @NotNull FlutterSdk sdk,
                               @Nullable PubRoot root,
                               @NotNull DataContext context) {
      }
    };
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testAllPluginXmlActions_declareExplicitUpdateThread() throws Exception {
    final InputStream stream = getClass().getClassLoader().getResourceAsStream("META-INF/plugin.xml");
    assertNotNull("META-INF/plugin.xml not found on test classpath", stream);

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    final Document doc = factory.newDocumentBuilder().parse(stream);

    final Set<String> classNames = new TreeSet<>();
    final NodeList actions = doc.getElementsByTagName("action");
    for (int i = 0; i < actions.getLength(); i++) {
      final String className = ((Element) actions.item(i)).getAttribute("class");
      if (!className.isEmpty()) {
        classNames.add(className);
      }
    }
    final NodeList groups = doc.getElementsByTagName("group");
    for (int i = 0; i < groups.getLength(); i++) {
      final String className = ((Element) groups.item(i)).getAttribute("class");
      if (!className.isEmpty()) {
        classNames.add(className);
      }
    }

    assertFalse("No action classes found in plugin.xml", classNames.isEmpty());

    for (String className : classNames) {
      final Class<?> clazz = Class.forName(className);
      final AnAction action = (AnAction) clazz.getDeclaredConstructor().newInstance();
      assertEquals("Action/Group " + className + " must return BGT",
                   ActionUpdateThread.BGT, action.getActionUpdateThread());
    }
  }

  @Test
  public void testDynamicDeviceActions_useBgt() {
    assertEquals(ActionUpdateThread.BGT, new OpenSimulatorAction(true).getActionUpdateThread());

    final AndroidEmulator emulator = mock(AndroidEmulator.class);
    when(emulator.getName()).thenReturn("Test_Emulator");
    final OpenEmulatorAction action = new OpenEmulatorAction(emulator);
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testRunFlutterAction_usesBgt() {
    final RunFlutterAction action = new RunFlutterAction("key", FlutterLaunchMode.DEBUG, "id") {};
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  @Test
  public void testFlutterAppActionHierarchy_usesBgt() {
    final FlutterAppAction action = mock(FlutterAppAction.class);
    when(action.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());

    final ReloadFlutterApp reloadAction = mock(ReloadFlutterApp.class);
    when(reloadAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, reloadAction.getActionUpdateThread());

    final RestartFlutterApp restartAction = mock(RestartFlutterApp.class);
    when(restartAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, restartAction.getActionUpdateThread());

    final ReloadAllFlutterApps reloadAllAction = mock(ReloadAllFlutterApps.class);
    when(reloadAllAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, reloadAllAction.getActionUpdateThread());

    final RestartAllFlutterApps restartAllAction = mock(RestartAllFlutterApps.class);
    when(restartAllAction.getActionUpdateThread()).thenCallRealMethod();
    assertEquals(ActionUpdateThread.BGT, restartAllAction.getActionUpdateThread());
  }

  @Test
  public void testFlutterExternalIdeActionGroup_usesEdt() {
    assertEquals(ActionUpdateThread.EDT, new FlutterExternalIdeActionGroup().getActionUpdateThread());
  }
}
