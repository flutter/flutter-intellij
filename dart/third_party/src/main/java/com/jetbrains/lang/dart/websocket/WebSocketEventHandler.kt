/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.lang.dart.websocket

interface WebSocketEventHandler {
  fun onOpen()
  fun onMessage(message: WebSocketMessage)
  fun onClose()
  fun onPing()
  fun onPong()
}
