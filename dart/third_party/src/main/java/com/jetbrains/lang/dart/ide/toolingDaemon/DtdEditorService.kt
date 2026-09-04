// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.toolingDaemon

import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.impl.XSourcePositionImpl
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException

internal class DtdEditorService(private val project: Project, private val dtdService: DartToolingDaemonService) {
    fun setUpService() {
      dtdService.registerServiceMethod("Editor", "getActiveLocation", JsonObject()) {
        val result = ReadAction.nonBlocking<JsonObject> {
          getActiveLocation(project, dtdService)
        }.executeSynchronously()
        result.addProperty("type", "ActiveLocation")

        DartToolingDaemonResponse(result, null)
      }

      dtdService.registerServiceMethod("Editor", "navigateToCode", JsonObject()) handler@{ request ->
        val fileUri: String? = request.get("uri").asString
        if (fileUri == null) {
          val params = JsonObject()
          params.addProperty("message", "No uri provided")
          return@handler DartToolingDaemonResponse(null, params)
        }

        var path: String? = null
        try {
          path = URI(fileUri).toURL().file
        } catch (_: MalformedURLException) {
          // A null path will cause an early return.
        } catch (_: URISyntaxException) {
        }
        if (path == null) {
          val params = JsonObject()
          params.addProperty("message", "Path could not be found from fileUri: $fileUri")
          return@handler DartToolingDaemonResponse(null, params)
        }

        val file = LocalFileSystem.getInstance().findFileByPath(path)
        val line: Int = request.get("line").asInt
        val column: Int = request.get("column").asInt

        ApplicationManager.getApplication().invokeLater {
          if (file != null && line >= 0 && column >= 0) {
            val position = XSourcePositionImpl.create(file, line - 1, column - 1)
            position.createNavigatable(project).navigate(false)
          }
        }

        val params = JsonObject()
        params.addProperty("success", true)
        DartToolingDaemonResponse(params, null)
      }
    }
}
