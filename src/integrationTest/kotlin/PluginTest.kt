/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.Starter
import org.junit.Test

class PluginTest {
  @Test
  fun simpleTestWithoutProject() {
    println("In integration test")
//    Starter.newContext(
//      testName = "testExample",
//      TestCase(IdeProductProvider.IC, projectInfo = NoProject)
//        .withVersion("2024.3")
//    ).apply {
//      val pathToPlugin = System.getProperty("path.to.build.plugin")
//      PluginConfigurator(this).installPluginFromFolder(File(pathToPlugin))
//    }.runIdeWithDriver().useDriverAndCloseIde {
//    }
  }
}