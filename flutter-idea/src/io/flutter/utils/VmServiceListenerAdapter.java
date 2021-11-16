/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.element.Event;

public abstract class VmServiceListenerAdapter implements VmServiceListener {
  @Override
  public void connectionOpened() {
  }

  @Override
  public void received(String streamId, Event event) {
  }

  @Override
  public void connectionClosed() {
  }
}
