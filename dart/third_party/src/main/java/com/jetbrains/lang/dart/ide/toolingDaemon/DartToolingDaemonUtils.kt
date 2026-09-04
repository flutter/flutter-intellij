// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.toolingDaemon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLock

@RequiresReadLock
fun getActiveLocation(project: Project, dtdService: DartToolingDaemonService): JsonObject {
    val activeLocationData = JsonObject()
    val selectionsArray = JsonArray()
    activeLocationData.add("selections", selectionsArray)

    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return activeLocationData
    val document = editor.document
    val virtualFile = editor.virtualFile?.takeIf { it.extension == "dart" } ?: return activeLocationData
    val uri = dtdService.getFileUri(virtualFile)

    val textDocumentObject = JsonObject()
    textDocumentObject.addProperty("uri", uri)
    activeLocationData.add("textDocument", textDocumentObject)

    for (caret in editor.caretModel.allCarets) {
        val selectionStart = caret.selectionStart
        val selectionEnd = caret.selectionEnd
        val anchorOffset = if (caret.offset == selectionStart) selectionEnd else selectionStart
        val activeOffset = caret.offset

        val (anchorLine, anchorColumn) = getLineAndColumn(document, anchorOffset)
        val (activeLine, activeColumn) = getLineAndColumn(document, activeOffset)

        val selectionObject = JsonObject()

        selectionObject.add("anchor", JsonObject().apply {
            addProperty("line", anchorLine)
            addProperty("character", anchorColumn)
        })

        selectionObject.add("active", JsonObject().apply {
            addProperty("line", activeLine)
            addProperty("character", activeColumn)
        })

        selectionsArray.add(selectionObject)
    }

    return activeLocationData
}

private fun getLineAndColumn(document: Document, offset: Int): Pair<Int, Int> {
    val lineNumber = document.getLineNumber(offset)
    return lineNumber to offset - document.getLineStartOffset(lineNumber)
}
