/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.refactoring

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerDescriptor
import com.intellij.platform.dartlsp.api.LspServerState
import com.intellij.platform.dartlsp.api.LspServerSupportProvider
import com.intellij.platform.dartlsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.dartlsp.api.customization.LspCommandsSupport
import com.intellij.platform.dartlsp.api.customization.LspCustomization
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdk
import io.flutter.FlutterBundle
import io.flutter.ide.FlutterCodeInsightFixtureTestCase
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionDisabled
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture

class FlutterLspRefactoringHelperTest : FlutterCodeInsightFixtureTestCase() {

  private var mockedDartSdk: MockedStatic<DartSdk>? = null

  override fun setUp() {
    super.setUp()
    DartConfigurable.setExperimentalLspFeaturesEnabled(project, true)
    val fakeSdk = Mockito.mock(DartSdk::class.java)
    Mockito.`when`(fakeSdk.version).thenReturn("3.14.0-137.0.dev")
    mockedDartSdk = Mockito.mockStatic(DartSdk::class.java).also { mocked ->
      mocked.`when`<DartSdk?> { DartSdk.getDartSdk(project) }.thenReturn(fakeSdk)
    }
  }

  override fun tearDown() {
    try {
      mockedDartSdk?.close()
      mockedDartSdk = null
      PropertiesComponent.getInstance(project).unsetValue("dart.lsp.experimental.enabled")
    } catch (e: Throwable) {
      addSuppressedException(e)
    } finally {
      super.tearDown()
    }
  }

  fun testPerformExtractWidgetExecutesMatchingCodeActionCommand() {
    val file = myFixture.configureByText(
      "test.dart",
      """
      import 'package:flutter/widgets.dart';
      void main() {
        <selection>Container()</selection>;
      }
      """.trimIndent(),
    )

    var capturedCodeActionParams: CodeActionParams? = null
    var capturedExecuteCommandParams: ExecuteCommandParams? = null

    val fakeServer = createFakeLspServer(
      onCodeAction = { params ->
        capturedCodeActionParams = params
        val disabledAction = CodeAction("Extract Widget").apply {
          kind = CodeActionKind.RefactorExtract
          disabled = CodeActionDisabled("Not valid")
        }
        val validAction = CodeAction("Extract Widget").apply {
          kind = CodeActionKind.RefactorExtract
          command = Command(
            "Extract Widget",
            "refactor.perform",
            listOf("EXTRACT_WIDGET", file.virtualFile.path, 2, 2, 11),
          )
        }
        listOf(Either.forRight(disabledAction), Either.forRight(validAction))
      },
      onExecuteCommand = { params ->
        capturedExecuteCommandParams = params
      },
    )

    val selectionModel = myFixture.editor.selectionModel
    val offset = selectionModel.selectionStart
    val length = selectionModel.selectionEnd - offset

    FlutterLspRefactoringHelper.performExtractWidget(
      project = project,
      editor = myFixture.editor,
      file = file,
      offset = offset,
      length = length,
      lspServer = fakeServer,
    )

    val codeActionParams = checkNotNull(capturedCodeActionParams) { "codeAction should have been called" }
    assertEquals(
      listOf(CodeActionKind.RefactorExtract),
      codeActionParams.context.only,
    )
    assertEquals(CodeActionTriggerKind.Invoked, codeActionParams.context.triggerKind)
    assertEquals(2, codeActionParams.range.start.line)
    assertEquals(2, codeActionParams.range.start.character)
    assertEquals(2, codeActionParams.range.end.line)
    assertEquals(13, codeActionParams.range.end.character)

    val executeCommandParams = checkNotNull(capturedExecuteCommandParams) { "executeCommand should have been called" }
    assertEquals("refactor.perform", executeCommandParams.command)
    assertEquals("EXTRACT_WIDGET", executeCommandParams.arguments.first())
  }

  fun testPerformExtractWidgetReturnsEarlyWhenDartSdkIsNull() {
    val file = myFixture.configureByText(
      "test.dart",
      """
      import 'package:flutter/widgets.dart';
      void main() {
        <selection>Container()</selection>;
      }
      """.trimIndent(),
    )

    mockedDartSdk?.`when`<DartSdk?> { DartSdk.getDartSdk(project) }?.thenReturn(null)

    var codeActionCalled = false
    val fakeServer = createFakeLspServer(
      onCodeAction = {
        codeActionCalled = true
        emptyList()
      },
      onExecuteCommand = {},
    )

    val selectionModel = myFixture.editor.selectionModel
    val offset = selectionModel.selectionStart
    val length = selectionModel.selectionEnd - offset

    FlutterLspRefactoringHelper.performExtractWidget(
      project = project,
      editor = myFixture.editor,
      file = file,
      offset = offset,
      length = length,
      lspServer = fakeServer,
    )

    assertFalse("codeAction should not be called when DartSdk is null", codeActionCalled)
  }

  fun testPerformExtractWidgetShowsCannotRefactorErrorWhenServerNotRunning() {
    val file = myFixture.configureByText(
      "test.dart",
      """
      import 'package:flutter/widgets.dart';
      void main() {
        <selection>Container()</selection>;
      }
      """.trimIndent(),
    )

    val selectionModel = myFixture.editor.selectionModel
    val offset = selectionModel.selectionStart
    val length = selectionModel.selectionEnd - offset

    try {
      FlutterLspRefactoringHelper.performExtractWidget(
        project = project,
        editor = myFixture.editor,
        file = file,
        offset = offset,
        length = length,
        lspServer = null,
      )
      fail("Expected RefactoringErrorHintException when LSP server is not running")
    } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
      val expectedMessage = RefactoringBundle.getCannotRefactorMessage(
        DartBundle.message("analysis.server.not.running"),
      )
      assertEquals(expectedMessage, e.message)
    }
  }

  fun testPerformExtractWidgetShowsErrorHintWhenNoMatchingActionReturned() {
    val file = myFixture.configureByText(
      "test.dart",
      """
      import 'package:flutter/widgets.dart';
      void main() {
        <selection>Container()</selection>;
      }
      """.trimIndent(),
    )

    val fakeServer = createFakeLspServer(
      onCodeAction = {
        val action = CodeAction("Extract Method").apply {
          kind = CodeActionKind.RefactorExtract
          command = Command("Extract Method", "refactor.perform", listOf("EXTRACT_METHOD"))
        }
        listOf(Either.forRight(action))
      },
      onExecuteCommand = {},
    )

    val selectionModel = myFixture.editor.selectionModel
    val offset = selectionModel.selectionStart
    val length = selectionModel.selectionEnd - offset

    try {
      FlutterLspRefactoringHelper.performExtractWidget(
        project = project,
        editor = myFixture.editor,
        file = file,
        offset = offset,
        length = length,
        lspServer = fakeServer,
      )
      fail("Expected RefactoringErrorHintException when no matching CodeAction is returned")
    } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
      assertEquals(RefactoringBundle.getCannotRefactorMessage(null), e.message)
    }
  }

  fun testPerformExtractWidgetShowsDisabledReasonWhenActionIsDisabled() {
    val file = myFixture.configureByText(
      "test.dart",
      """
      import 'package:flutter/widgets.dart';
      void main() {
        <selection>123</selection>;
      }
      """.trimIndent(),
    )

    val disabledMessage = "Only expressions that evaluate to a Widget can be extracted."
    val fakeServer = createFakeLspServer(
      onCodeAction = {
        val action = CodeAction("Extract Widget").apply {
          kind = CodeActionKind.RefactorExtract
          disabled = CodeActionDisabled(disabledMessage)
        }
        listOf(Either.forRight(action))
      },
      onExecuteCommand = {},
    )

    val selectionModel = myFixture.editor.selectionModel
    val offset = selectionModel.selectionStart
    val length = selectionModel.selectionEnd - offset

    try {
      FlutterLspRefactoringHelper.performExtractWidget(
        project = project,
        editor = myFixture.editor,
        file = file,
        offset = offset,
        length = length,
        lspServer = fakeServer,
      )
      fail("Expected RefactoringErrorHintException when Extract Widget CodeAction is disabled")
    } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
      assertEquals(RefactoringBundle.getCannotRefactorMessage(disabledMessage), e.message)
    }
  }

  fun testPerformExtractWidgetShowsNoResponseErrorHintWhenServerReturnsNull() {
    val file = myFixture.configureByText(
      "test.dart",
      """
      import 'package:flutter/widgets.dart';
      void main() {
        <selection>Container()</selection>;
      }
      """.trimIndent(),
    )

    val fakeServer = createFakeLspServer(
      onCodeAction = { null },
      onExecuteCommand = {},
    )

    val selectionModel = myFixture.editor.selectionModel
    val offset = selectionModel.selectionStart
    val length = selectionModel.selectionEnd - offset

    try {
      FlutterLspRefactoringHelper.performExtractWidget(
        project = project,
        editor = myFixture.editor,
        file = file,
        offset = offset,
        length = length,
        lspServer = fakeServer,
      )
      fail("Expected RefactoringErrorHintException when server returns null")
    } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
      val expectedMessage = RefactoringBundle.getCannotRefactorMessage(
        FlutterBundle.message("flutter.refactoring.lsp.no.response"),
      )
      assertEquals(expectedMessage, e.message)
    }
  }

  private fun createFakeLspServer(
    onCodeAction: (CodeActionParams) -> List<Either<Command, CodeAction>>?,
    onExecuteCommand: (ExecuteCommandParams) -> Unit,
  ): LspServer {
    val textDocumentService = Proxy.newProxyInstance(
      TextDocumentService::class.java.classLoader,
      arrayOf(TextDocumentService::class.java),
    ) { _, method, args ->
      if (method.name == "codeAction") {
        val params = args[0] as CodeActionParams
        CompletableFuture.completedFuture(onCodeAction(params))
      } else {
        null
      }
    } as TextDocumentService

    val workspaceService = Proxy.newProxyInstance(
      WorkspaceService::class.java.classLoader,
      arrayOf(WorkspaceService::class.java),
    ) { _, method, args ->
      if (method.name == "executeCommand") {
        val params = args[0] as ExecuteCommandParams
        onExecuteCommand(params)
        CompletableFuture.completedFuture(null)
      } else {
        null
      }
    } as WorkspaceService

    val lsp4jServer = Proxy.newProxyInstance(
      LanguageServer::class.java.classLoader,
      arrayOf(LanguageServer::class.java),
    ) { _, method, _ ->
      when (method.name) {
        "getTextDocumentService" -> textDocumentService
        "getWorkspaceService" -> workspaceService
        else -> null
      }
    } as LanguageServer

    val serverDescriptor = object : ProjectWideLspServerDescriptor(project, "Dart (Bridge)") {
      override fun isSupportedFile(file: VirtualFile): Boolean = true
      override val lspCustomization = object : LspCustomization() {
        override val commandsCustomizer = LspCommandsSupport()
      }
    }

    return object : LspServer {
      override val providerClass: Class<out LspServerSupportProvider> = DartLspServerSupportProvider::class.java
      override val project = this@FlutterLspRefactoringHelperTest.project
      override val descriptor: LspServerDescriptor = serverDescriptor
      override val state: LspServerState = LspServerState.Running
      override val initializeResult: InitializeResult = InitializeResult(
        ServerCapabilities().apply {
          setCodeActionProvider(true)
        },
      )

      override fun sendNotification(lsp4jSender: (LanguageServer) -> Unit) {
        lsp4jSender(lsp4jServer)
      }

      override suspend fun <Lsp4jResponse> sendRequest(
        lsp4jSender: (LanguageServer) -> CompletableFuture<Lsp4jResponse>,
      ): Lsp4jResponse? {
        return lsp4jSender(lsp4jServer).get()
      }

      override fun <Lsp4jResponse> sendRequestSync(
        timeoutMs: Int,
        lsp4jSender: (LanguageServer) -> CompletableFuture<Lsp4jResponse>,
      ): Lsp4jResponse? {
        return lsp4jSender(lsp4jServer).get()
      }

      override fun getDocumentIdentifier(file: VirtualFile): TextDocumentIdentifier {
        return TextDocumentIdentifier(descriptor.getFileUri(file))
      }

      override fun getDocumentVersion(document: Document): Int = 1
    }
  }
}
