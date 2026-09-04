package com.jetbrains.lang.dart.analyzer

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.lang.dart.DartBundle
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * A CachesInvalidator for the Dart IntelliJ plugin, on the execution of this invalidator,
 * the `~./dartServer/` directory (or equivalent on Windows machines), is deleted.
 */
private class DASSCacheInvalidator : CachesInvalidator() {

    override fun getComment(): String = DartBundle.message("analysis.server.cache.invalidate.comment")

    override fun getDescription(): String = DartBundle.message("analysis.server.cache.invalidate.description")

    override fun optionalCheckboxDefaultValue(): Boolean = false

    override fun invalidateCaches() {
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            val serverService = DartAnalysisServerService.getInstance(project)
            serverService.stopServer()
        }
        val dartServerCacheDirectory = getDartServerCacheDirectory()
        if (dartServerCacheDirectory != null && dartServerCacheDirectory.exists()) {
            FileUtil.asyncDelete(dartServerCacheDirectory)
        }
    }

    private fun getDartServerCacheDirectory(): File? {
        return if (SystemInfoRt.isWindows) {
            val appData = System.getenv("LOCALAPPDATA")
            if (appData != null) File(appData, ".dartServer") else null
        } else {
            val userHome = System.getProperty("user.home")
            if (userHome != null) File(userHome, ".dartServer") else null
        }
    }
}
