/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.refactoring

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.dartlsp.api.LspServer
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.customization.LspIntentionAction
import com.intellij.platform.dartlsp.util.getLsp4jRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider
import com.jetbrains.lang.dart.sdk.DartSdk
import io.flutter.FlutterBundle
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind

/**
 * Routes Flutter "Extract Widget" refactoring action to LSP `textDocument/codeAction`
 * requests when LSP code actions are enabled.
 */
object FlutterLspRefactoringHelper {
  const val EXTRACT_WIDGET_TITLE: String = "Extract Widget"

  private data class CodeActionLookupResult(
    val completed: Boolean,
    val serverResponded: Boolean,
    val matchingIntention: LspIntentionAction?,
    val disabledReason: String? = null,
  )

  @JvmStatic
  @JvmOverloads
  fun performExtractWidget(
    project: Project,
    editor: Editor?,
    file: PsiFile?,
    offset: Int = editor?.selectionModel?.selectionStart ?: 0,
    length: Int = (editor?.selectionModel?.selectionEnd ?: 0) - offset,
    lspServer: LspServer? = LspServerManager.getInstance(project)
      .getServersForProvider(DartLspServerSupportProvider::class.java)
      .firstOrNull(),
  ) {
    if (editor == null || file == null) return
    performRefactoring(
      project = project,
      editor = editor,
      file = file,
      offset = offset,
      length = length,
      codeActionKind = CodeActionKind.RefactorExtract,
      targetTitles = setOf(EXTRACT_WIDGET_TITLE),
      progressTitle = FlutterBundle.message("flutter.refactoring.extract.widget.progress"),
      errorTitle = FlutterBundle.message("flutter.refactoring.extract.widget.error"),
      lspServer = lspServer,
    )
  }

  @JvmStatic
  @JvmOverloads
  fun performRefactoring(
    project: Project,
    editor: Editor,
    file: PsiFile,
    offset: Int,
    length: Int,
    codeActionKind: String,
    targetTitles: Set<String>,
    @NlsContexts.DialogTitle progressTitle: String,
    @NlsContexts.DialogTitle errorTitle: String,
    lspServer: LspServer? = LspServerManager.getInstance(project)
      .getServersForProvider(DartLspServerSupportProvider::class.java)
      .firstOrNull(),
  ) {
    if (DartSdk.getDartSdk(project) == null) return
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) return
    val virtualFile = file.virtualFile ?: return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (lspServer == null) {
      showCannotRefactorErrorHint(project, editor, errorTitle, DartBundle.message("analysis.server.not.running"))
      return
    }

    val params = createCodeActionParams(lspServer, virtualFile, editor.document, offset, length, codeActionKind)
    val result = requestMatchingIntention(project, lspServer, params, targetTitles, progressTitle)
    if (!result.completed) return
    if (!result.serverResponded) {
      showCannotRefactorErrorHint(project, editor, errorTitle, FlutterBundle.message("flutter.refactoring.lsp.no.response"))
      return
    }
    if (result.disabledReason != null) {
      showCannotRefactorErrorHint(project, editor, errorTitle, result.disabledReason)
      return
    }
    val intentionToInvoke = result.matchingIntention ?: run {
      showCannotRefactorErrorHint(project, editor, errorTitle)
      return
    }
    intentionToInvoke.invoke(virtualFile)
  }

  private fun createCodeActionParams(
    lspServer: LspServer,
    virtualFile: VirtualFile,
    document: Document,
    offset: Int,
    length: Int,
    codeActionKind: String,
  ): CodeActionParams {
    val textLength = document.textLength
    val safeOffset = offset.coerceIn(0, textLength)
    val safeLength = length.coerceIn(0, textLength - safeOffset)
    val range = getLsp4jRange(document, safeOffset, safeLength)
    val context = CodeActionContext(emptyList(), listOf(codeActionKind)).apply {
      triggerKind = CodeActionTriggerKind.Invoked
    }
    return CodeActionParams(lspServer.getDocumentIdentifier(virtualFile), range, context)
  }

  private fun requestMatchingIntention(
    project: Project,
    lspServer: LspServer,
    params: CodeActionParams,
    targetTitles: Set<String>,
    @NlsContexts.DialogTitle progressTitle: String,
  ): CodeActionLookupResult {
    var serverResponded = false
    var matchingIntention: LspIntentionAction? = null
    var disabledReason: String? = null
    val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        val results = lspServer.sendRequestSync { it.textDocumentService.codeAction(params) }
          ?: return@runProcessWithProgressSynchronously
        serverResponded = true
        val matchingActions = results.asSequence()
          .mapNotNull { it.right }
          .filter { it.title in targetTitles }
          .toList()
        val validAction = matchingActions.firstOrNull { it.disabled == null }
        if (validAction != null) {
          val intention = LspIntentionAction(lspServer, validAction)
          ApplicationManager.getApplication().runReadAction {
            if (intention.isAvailable()) {
              matchingIntention = intention
            }
          }
        } else {
          disabledReason = matchingActions.firstOrNull { it.disabled != null }?.disabled?.reason
        }
      },
      progressTitle,
      true,
      project,
    )
    return CodeActionLookupResult(completed, serverResponded, matchingIntention, disabledReason)
  }

  private fun showCannotRefactorErrorHint(
    project: Project,
    editor: Editor,
    @NlsContexts.DialogTitle errorTitle: String,
    @NlsContexts.DialogMessage reason: String? = null,
  ) {
    val message = RefactoringBundle.getCannotRefactorMessage(reason)
    CommonRefactoringUtil.showErrorHint(project, editor, message, errorTitle, null)
  }
}
