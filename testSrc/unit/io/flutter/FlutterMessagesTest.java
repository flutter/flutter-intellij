/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link FlutterMessages}.
 */
public class FlutterMessagesTest {

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void showDialogDelegatesToMessagesAndReturnsSelectedOptionIndex() throws Exception {
    final AtomicReference<String> shownMessage = new AtomicReference<>();
    final TestDialog previous = TestDialogManager.setTestDialog(message -> {
      shownMessage.set(message);
      return 1;
    });
    try {
      final int result = Testing.computeOnDispatchThread(() -> FlutterMessages.showDialog(
        fixture.getProject(),
        "Configure Flutter SDK?",
        "Flutter SDK",
        new String[]{"Cancel", "Open Settings"},
        0
      ));
      assertEquals(1, result);
      assertEquals("Configure Flutter SDK?", shownMessage.get());
    }
    finally {
      TestDialogManager.setTestDialog(previous);
    }
  }
}
