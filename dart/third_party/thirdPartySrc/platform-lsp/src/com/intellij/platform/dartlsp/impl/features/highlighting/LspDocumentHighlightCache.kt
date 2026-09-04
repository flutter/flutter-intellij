package com.intellij.platform.dartlsp.impl.features.highlighting

import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.impl.cache.LspPerFileCache

internal class LspDocumentHighlightCache(project: Project) : LspPerFileCache<Int, List<TextRangeAndHighlightKind>>(
  project,
  matches = { storedOffset, storedValue, queriedOffset ->
    storedOffset == queriedOffset || storedValue.any { it.textRange.contains(queriedOffset) }
  }
)
