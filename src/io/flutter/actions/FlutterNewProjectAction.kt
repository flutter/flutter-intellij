/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions

import com.intellij.ide.impl.createNewProjectAsync
import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen
import io.flutter.FlutterBundle
import io.flutter.FlutterUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This classes was re-implemented with Kotlin from FlutterNewProjectAction.java to resolve the New Flutter Project action from hanging,
 * https://github.com/flutter/flutter-intellij/issues/8310.
 *
 * See https://github.com/JetBrains/intellij-community/blob/master/java/idea-ui/src/com/intellij/ide/impl/NewProjectUtil.kt
 */
class FlutterNewProjectAction : AnAction(FlutterBundle.message("action.new.project.title")), DumbAware {
  override fun update(e: AnActionEvent) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.presentation.setText(FlutterBundle.message("welcome.new.project.compact"))
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun actionPerformed(e: AnActionEvent) {
    if (FlutterUtils.isAndroidStudio()) {
      System.setProperty("studio.projectview", "true")
    }

    GlobalScope.launch {
      val wizard = withContext(Dispatchers.EDT) {
        NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null)
      }
      createNewProjectAsync(wizard)
    }

  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
