/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

/** How IntelliJ highlights an occurrence of the element under the caret. */
enum class UsageAccess(val attributesKey: String) {
  /** A read, drawn with the "Identifier under caret" colors. */
  READ("IDENTIFIER_UNDER_CARET_ATTRIBUTES"),

  /** A write (declaration with initializer, assignment), drawn with the "Identifier under caret (write)" colors. */
  WRITE("WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES"),
}

/** One highlighted occurrence: the text range `[start, end)` in the document and whether it's a read or a write. */
data class UsageHighlight(val start: Int, val end: Int, val access: UsageAccess)

/**
 * The occurrences IntelliJ currently highlights for the element under the caret ("Highlight usages of element at
 * caret"), read from the editor's markup models, sorted by offset. Reads and writes are told apart by the text
 * attributes key of each highlighter.
 */
fun JEditorUiComponent.usageHighlights(): List<UsageHighlight> = driver.withContext(OnDispatcher.EDT) {
  val editorEx = cast(editor, EditorWithMarkupRef::class)
  val highlighters = editorEx.getMarkupModel().getAllHighlighters().toList() +
                     editorEx.getFilteredDocumentMarkupModel().getAllHighlighters().toList()
  highlighters.mapNotNull { highlighter ->
    val key = runCatching { highlighter.getTextAttributesKey()?.getExternalName() }.getOrNull() ?: return@mapNotNull null
    val access = UsageAccess.entries.firstOrNull { it.attributesKey == key } ?: return@mapNotNull null
    UsageHighlight(highlighter.getStartOffset(), highlighter.getEndOffset(), access)
  }.distinct().sortedBy { it.start }
}

@Remote("com.intellij.openapi.editor.ex.EditorEx")
internal interface EditorWithMarkupRef {
  fun getMarkupModel(): MarkupModelRef
  fun getFilteredDocumentMarkupModel(): MarkupModelRef
}

@Remote("com.intellij.openapi.editor.markup.MarkupModel")
internal interface MarkupModelRef {
  fun getAllHighlighters(): Array<RangeHighlighterRef>
}

@Remote("com.intellij.openapi.editor.markup.RangeHighlighter")
internal interface RangeHighlighterRef {
  fun getStartOffset(): Int
  fun getEndOffset(): Int
  fun getTextAttributesKey(): TextAttributesKeyRef?
}

@Remote("com.intellij.openapi.editor.colors.TextAttributesKey")
internal interface TextAttributesKeyRef {
  fun getExternalName(): String
}
