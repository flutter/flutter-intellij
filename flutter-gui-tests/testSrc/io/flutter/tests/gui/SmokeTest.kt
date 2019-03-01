package io.flutter.tests.gui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.MultiLineLabelUI
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import org.junit.Assert
import org.junit.Test

// Inspired by CommandLineProjectGuiTest
@RunWithIde(CommunityIde::class)
class SmokeTest : GuiTestCase() {
  private val LOG = Logger.getInstance(this.javaClass)

  @Test
  fun testStartup() {
    ProjectCreator.createProject(projectName = "guitest")
    ideFrame {
      editor {
        // Wait until current file has appeared in current editor and set focus to editor.
        moveTo(1)
      }
      val editorCode = editor.getCurrentFileContents(false)
      Assert.assertTrue(editorCode!!.unifyCode().startsWith(codeText.unifyCode()))
      closeProjectAndWaitWelcomeFrame()
    }
  }

  // Copied from CommandLineProjectGuiTest, just for safety.
  private fun String.unifyCode(): String =
    MultiLineLabelUI.convertTabs(StringUtil.convertLineSeparators(this), 2)

  private val codeText: String = """import 'package:flutter/material.dart';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // Try running your application with "flutter run". You'll see the
        // application has a blue toolbar. Then, without quitting the app, try
        // changing the primarySwatch below to Colors.green and then invoke
        // "hot reload" (press "r" in the console where you ran "flutter run",
        // or simply save your changes to "hot reload" in a Flutter IDE).
        // Notice that the counter didn't reset back to zero; the application
        // is not restarted.
        primarySwatch: Colors.blue,
      ),
      home: MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}
"""
}