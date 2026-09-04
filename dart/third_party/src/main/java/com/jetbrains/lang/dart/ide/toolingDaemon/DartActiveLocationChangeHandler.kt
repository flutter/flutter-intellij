package com.jetbrains.lang.dart.ide.toolingDaemon

import com.google.gson.JsonObject
import com.intellij.openapi.application.readAction
import com.jetbrains.lang.dart.logging.PluginLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class DartActiveLocationChangeHandler(private val dtdService: DartToolingDaemonService, cs: CoroutineScope) {
  private var activeLocationNullSent: Boolean = false

  private val activeLocationChangeFlow: MutableSharedFlow<Unit> =
    MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    cs.launch {
      activeLocationChangeFlow
        .debounce(300.milliseconds)
        .collectLatest {
          doSendActiveLocationChangeEvent()
        }
    }

    val listener = object : DocumentListener, CaretListener, SelectionListener, FileEditorManagerListener {
      private fun emitActiveLocationChange() {
        activeLocationChangeFlow.tryEmit(Unit)
      }

      override fun documentChanged(event: DocumentEvent) = emitActiveLocationChange()
      override fun caretPositionChanged(event: CaretEvent) = emitActiveLocationChange()
      override fun caretAdded(event: CaretEvent) = emitActiveLocationChange()
      override fun caretRemoved(event: CaretEvent) = emitActiveLocationChange()
      override fun selectionChanged(e: SelectionEvent) = emitActiveLocationChange()
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) = emitActiveLocationChange()
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) = emitActiveLocationChange()
      override fun selectionChanged(event: FileEditorManagerEvent) = emitActiveLocationChange()
    }

    EditorFactory.getInstance().eventMulticaster.apply {
      addDocumentListener(listener, dtdService)
      addCaretListener(listener, dtdService)
      addSelectionListener(listener, dtdService)
    }

    dtdService.project.messageBus.connect(dtdService)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener)
  }

  private suspend fun doSendActiveLocationChangeEvent() {
    if (!dtdService.webSocketReady) return

    val paramsObject = readAction { calcActiveLocationChangeParams() }
    val activeLocationNull = paramsObject.getAsJsonObject("eventData")?.getAsJsonObject("textDocument") == null
    if (!activeLocationNull || !activeLocationNullSent) {
      activeLocationNullSent = activeLocationNull
      dtdService.sendRequest("postEvent", paramsObject, false) { }
    }
  }

  @RequiresReadLock
  private fun calcActiveLocationChangeParams(): JsonObject {
    val paramsObject = JsonObject()
    paramsObject.addProperty("streamId", "Editor")
    paramsObject.addProperty("eventKind", "activeLocationChanged")

    val activeLocation = getActiveLocation(dtdService.project, dtdService)
    paramsObject.add("eventData", activeLocation)

    return paramsObject
  }

  companion object {
    private val logger = PluginLogger.createLogger(DartActiveLocationChangeHandler::class.java)
  }
}
