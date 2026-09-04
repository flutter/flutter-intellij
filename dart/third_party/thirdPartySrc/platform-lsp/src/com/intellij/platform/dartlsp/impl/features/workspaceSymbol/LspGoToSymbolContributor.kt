package com.intellij.platform.dartlsp.impl.features.workspaceSymbol

import org.eclipse.lsp4j.SymbolKind

internal class LspGoToSymbolContributor : LspWorkspaceSymbolContributor() {
  override fun shouldAcceptSymbolKind(symbolKind: SymbolKind): Boolean = true
}
