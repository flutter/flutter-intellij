package com.intellij.platform.dartlsp.impl.connector

import com.intellij.platform.dartlsp.api.Lsp4jServer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface Lsp4jServerWrapperCreator {
  fun wrapLsp4jServer(lsp4jServer: Lsp4jServer): Lsp4jServer
}
