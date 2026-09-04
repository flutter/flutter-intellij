// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.runner

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.runner.base.DartRunConfigurationBase
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfiguration
import com.jetbrains.lang.dart.ide.runner.server.DartRemoteDebugConfiguration
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess.DebugType
import com.jetbrains.lang.dart.ide.runner.server.webdev.DartWebdevConfiguration
import com.jetbrains.lang.dart.ide.runner.test.DartTestRunConfiguration
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.util.DartUrlResolver
import java.lang.reflect.Method

class DartRunner : GenericProgramRunner<RunnerSettings>() {
  override fun getRunnerId(): String = "DartRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean =
    executorId == DefaultDebugExecutor.EXECUTOR_ID && (
        profile is DartCommandLineRunConfiguration ||
            profile is DartTestRunConfiguration ||
            profile is DartRemoteDebugConfiguration ||
            profile is DartWebdevConfiguration
        )

  @Throws(ExecutionException::class)
  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val executorId = environment.executor.id
    if (executorId != DefaultDebugExecutor.EXECUTOR_ID) {
      LOG.error("Unexpected executor id: $executorId")
      return null
    }

    try {
      val runConfig = environment.runProfile
      val project = environment.project
      val analysisServer = DartAnalysisServerService.getInstance(project)
      val dasExecutionContextId = if (
        runConfig is DartRunConfigurationBase &&
        analysisServer.serverReadyForRequest()
      ) {
        val path = checkNotNull(runConfig.runnerParameters.filePath)
        analysisServer.execution_createContext(path)
      } else {
        // No context ID if remote debugging or can't start DAS.
        null
      }

      return doExecuteDartDebug(state, environment, dasExecutionContextId)
    } catch (e: RuntimeConfigurationError) {
      throw ExecutionException(e)
    }
  }

  @Throws(RuntimeConfigurationError::class, ExecutionException::class)
  private fun doExecuteDartDebug(
    state: RunProfileState,
    env: ExecutionEnvironment,
    dasExecutionContextId: String?
  ): RunContentDescriptor? {
    val runConfiguration = env.runProfile
    val project = env.project

    val executionInfo = when (runConfiguration) {
      is DartRunConfigurationBase -> {
        val contextFileOrDir = runConfiguration.runnerParameters.dartFileOrDirectory
        val cwd = runConfiguration.runnerParameters.computeProcessWorkingDirectory(project)
        val currentWorkingDirectory = LocalFileSystem.getInstance().findFileByPath(cwd)
        val executionResult = state.execute(env.executor, this) ?: return null
        ExecutionInfo(contextFileOrDir, currentWorkingDirectory, executionResult, DebugType.CLI)
      }

      is DartRemoteDebugConfiguration -> {
        val path = runConfiguration.parameters.dartProjectPath
        val contextDirectory = LocalFileSystem.getInstance().findFileByPath(path)
          ?: throw RuntimeConfigurationError(
            DartBundle.message(
              "dialog.message.folder.not.found",
              FileUtil.toSystemDependentName(path)
            )
          )
        ExecutionInfo(contextDirectory, contextDirectory, null, DebugType.REMOTE)
      }

      is DartWebdevConfiguration -> {
        val contextFile = runConfiguration.parameters.htmlFile
        val currentWorkingDirectory = runConfiguration.parameters.getWorkingDirectory(project)
        val executionResult = state.execute(env.executor, this) ?: return null
        ExecutionInfo(contextFile, currentWorkingDirectory, executionResult, DebugType.WEBDEV)
      }

      else -> {
        LOG.error("Unexpected run configuration: ${runConfiguration.name}")
        return null
      }
    }

    FileDocumentManager.getInstance().saveAllDocuments()

    val starter = object : XDebugProcessStarter() {
      override fun start(session: XDebugSession): XDebugProcess {
        val dartUrlResolver = DartUrlResolver
          .getInstance(project, executionInfo.contextFileOrDir)
        val dartVmServiceDebugProcess = DartVmServiceDebugProcess(
          session,
          executionInfo.executionResult,
          dartUrlResolver,
          dasExecutionContextId,
          executionInfo.debugType,
          VM_SERVICE_TIMEOUT_IN_MS,
          executionInfo.currentWorkingDirectory
        )

        dartVmServiceDebugProcess.start()
        return dartVmServiceDebugProcess
      }
    }

    val manager = XDebuggerManager.getInstance(project)
    val hooks = builderHooks
    if (hooks != null) {
      try {
        var builder = hooks.newSessionBuilderMethod.invoke(manager, starter)
        builder = hooks.environmentMethod.invoke(builder, env)
        if (hooks.contentToReuseMethod != null && env.contentToReuse != null) {
          builder = hooks.contentToReuseMethod.invoke(builder, env.contentToReuse)
        }
        if (hooks.sessionNameMethod != null) {
          builder = hooks.sessionNameMethod.invoke(builder, env.runProfile.name)
        }
        if (hooks.showTabMethod != null) {
          val showTab = env.executor.id != DefaultRunExecutor.EXECUTOR_ID
          builder = hooks.showTabMethod.invoke(builder, showTab)
        }
        val sessionResult = hooks.startSessionMethod.invoke(builder)
        val getDescriptorMethod = sessionResult.javaClass.getMethod("getRunContentDescriptor")
        return getDescriptorMethod.invoke(sessionResult) as? RunContentDescriptor
      } catch (e: Exception) {
        LOG.error("Failed to start debug session via reflection, falling back to legacy", e)
      }
    }

    @Suppress("DEPRECATION")
    val debugSession = manager.startSession(env, starter)
    @Suppress("DEPRECATION")
    return debugSession.runContentDescriptor
  }

  private data class ExecutionInfo(
    val contextFileOrDir: VirtualFile,
    val currentWorkingDirectory: VirtualFile?,
    val executionResult: ExecutionResult?,
    val debugType: DebugType,
  )

  companion object {
    private val LOG = PluginLogger.createLogger(DartRunner::class.java)
    private const val VM_SERVICE_TIMEOUT_IN_MS = 5000

    private class BuilderHooks(
      val newSessionBuilderMethod: Method,
      val environmentMethod: Method,
      val sessionNameMethod: Method?,
      val contentToReuseMethod: Method?,
      val showTabMethod: Method?,
      val startSessionMethod: Method
    )

    private val builderHooks: BuilderHooks? = findBuilderHooks()

    private fun findBuilderHooks(): BuilderHooks? {
      return try {
        val nsb = XDebuggerManager::class.java.getMethod("newSessionBuilder", XDebugProcessStarter::class.java)
        val builderClass = nsb.returnType
        val env = builderClass.getMethod("environment", ExecutionEnvironment::class.java)
        val ss = builderClass.getMethod("startSession")
        var sn: Method? = null
        var ctr: Method? = null
        var st: Method? = null
        try {
          sn = builderClass.getMethod("sessionName", String::class.java)
          ctr = builderClass.getMethod("contentToReuse", RunContentDescriptor::class.java)
          st = builderClass.getMethod("showTab", Boolean::class.javaPrimitiveType)
        } catch (_: NoSuchMethodException) {
        }
        BuilderHooks(nsb, env, sn, ctr, st, ss)
      } catch (_: NoSuchMethodException) {
        null
      }
    }
  }
}
