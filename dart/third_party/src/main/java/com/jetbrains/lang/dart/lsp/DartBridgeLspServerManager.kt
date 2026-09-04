/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.dartlsp.api.LspServerManager
import com.intellij.platform.dartlsp.api.ensureServerStarted
import com.intellij.platform.dartlsp.api.stopServers
import com.jetbrains.lang.dart.logging.PluginLogger
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Future

/**
 * Project-level service that manages the lifecycle of the LSP bridge server.
 *
 * This manager sets up a local TCP [ServerSocket] on a dynamically allocated port
 * and spawns a background thread to listen for incoming connection requests from the JetBrains
 * native LSP client. When [startBridgeServer] is called, it opens the socket and manually
 * triggers the JetBrains LSP client framework via [LspServerManager.ensureServerStarted]
 * to connect to this bridge.
 */
@Service(Service.Level.PROJECT)
class DartBridgeLspServerManager(private val project: Project) : Disposable {
    companion object {
        private val logger = PluginLogger.createLogger(DartBridgeLspServerManager::class.java)
    }

    private var serverSocket: ServerSocket? = null
    private var listenFuture: Future<*>? = null
    private var activeConnection: ActiveConnection? = null
    
    val port: Int
        get() = serverSocket?.localPort ?: -1


    fun startBridgeServer() {
        if (serverSocket != null) {
            logger.warn("Bridge LSP Server is already running")
            return
        }
        try {
            // Bind to a random free port on localhost. The OS will assign an available port.
            // This port is then queried by DartLspServerDescriptor to tell the JetBrains LSP client where to connect.
            serverSocket = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
            logger.info("Bridge LSP Server socket listening on port $port")
            startListening()
            
            // Manually trigger the JetBrains LSP client to connect to our bridge.
            // This replaces the automatic file-opened trigger.
            LspServerManager.getInstance(project).ensureServerStarted<DartLspServerSupportProvider>(DartLspServerDescriptor(project))
            logger.info("Triggered LSP client start via ensureServerStarted")
        } catch (e: IOException) {
            logger.error("Failed to start Bridge ServerSocket", e)
        }
    }

    fun stopBridgeServer() {
        logger.info("Stopping Bridge LSP Server")
        // Disconnect the JetBrains LSP client.
        LspServerManager.getInstance(project).stopServers<DartLspServerSupportProvider>()
        
        serverSocket?.close()
        serverSocket = null
        
        listenFuture?.cancel(true)
        listenFuture = null
        
        activeConnection?.close()
        activeConnection = null
    }

    private fun startListening() {
        // Start a background thread to listen for incoming connections from the JetBrains LSP client.
        listenFuture = ApplicationManager.getApplication().executeOnPooledThread {
            while (serverSocket?.isClosed == false) {
                try {
                    // accept() is a blocking call. The thread suspends here and consumes no CPU
                    // until the JetBrains LSP client initiates a connection.
                    val socket = serverSocket?.accept() ?: continue
                    logger.info("Accepted connection from client: ${socket.remoteSocketAddress}")
                    handleClientConnection(socket)
                } catch (e: IOException) {
                    if (serverSocket?.isClosed == false) {
                        logger.error("Error accepting connection", e)
                    }
                }
            }
        }
    }


    private fun handleClientConnection(socket: Socket) {
        // Close existing connection if any.
        activeConnection?.close()

        var bridgeServer: DartBridgeLspServer? = null
        try {
            bridgeServer = DartBridgeLspServer(project)
            val launcher = Launcher.createLauncher(bridgeServer, LanguageClient::class.java, socket.getInputStream(), socket.getOutputStream())
            
            bridgeServer.connect(launcher.remoteProxy)
            val startListeningFuture = launcher.startListening()

            activeConnection = ActiveConnection(socket, bridgeServer, startListeningFuture)
        } catch (e: Exception) {
            logger.error("Failed to handle client connection", e)
            bridgeServer?.stop()
            try {
                socket.close()
            } catch (ex: IOException) {
                // Ignore
            }
        }
    }

    override fun dispose() {
        logger.info("Disposing DartBridgeLspServerManager")
        stopBridgeServer()
    }

    private inner class ActiveConnection(
        val socket: Socket,
        val bridgeServer: DartBridgeLspServer,
        val listeningFuture: Future<*>
    ) {
        fun close() {
            logger.info("Closing active connection")
            bridgeServer.stop()
            listeningFuture.cancel(true)
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }
}
