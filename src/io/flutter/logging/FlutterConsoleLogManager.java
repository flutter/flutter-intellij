/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.logging;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.QueueProcessor;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.ServiceExtensions;
import io.flutter.vmService.VmServiceConsumers;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;

public class FlutterConsoleLogManager {
  private static final boolean SHOW_STRUCTURED_ERRORS = false;

  private static final ConsoleViewContentType SUBTLE_CONTENT_TYPE =
    new ConsoleViewContentType("subtle", SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes());

  private static QueueProcessor<Runnable> queue;

  @NotNull final FlutterApp app;

  public FlutterConsoleLogManager(@NotNull FlutterApp app) {
    this.app = app;

    if (queue == null) {
      queue = QueueProcessor.createRunnableQueueProcessor();
    }

    if (SHOW_STRUCTURED_ERRORS) {
      // Calling this will override the default Flutter stdout error display.
      app.hasServiceExtension(ServiceExtensions.toggleShowStructuredErrors.getExtension(), (present) -> {
        if (present) {
          app.callBooleanExtension(ServiceExtensions.toggleShowStructuredErrors.getExtension(), true);
        }
      });
    }
  }

  public void handleFlutterErrorEvent(@NotNull Event event) {
    if (SHOW_STRUCTURED_ERRORS) {
      assert app.getConsole() != null;

      // TODO(devoncarew): Pretty print the error (using the available console syling attributes).
      app.getConsole().print(
        event.getExtensionData().getJson().toString() + "\n",
        new ConsoleViewContentType("subtle", SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes()));
    }
  }

  public void handleLoggingEvent(@NotNull Event event) {
    queue.add(() -> processEvent(event));
  }

  private void processEvent(@NotNull Event event) {
    final LogRecord logRecord = event.getLogRecord();

    @NotNull final InstanceRef message = logRecord.getMessage();
    @NotNull final InstanceRef loggerName = logRecord.getLoggerName();

    // TODO(devoncarew): Handle truncated 'message' values.
    final String name = loggerName.getValueAsString().isEmpty() ? "log" : loggerName.getValueAsString();
    final String prefix = "[" + name + "] ";
    final String output = prefix + stringValueFromStringRef(message) + "\n";

    assert app.getConsole() != null;
    final ConsoleView console = app.getConsole();

    console.print(prefix, SUBTLE_CONTENT_TYPE);
    console.print(message.getValueAsString() + "\n", ConsoleViewContentType.NORMAL_OUTPUT);

    // TODO(devoncarew): Handle json in the error payload.

    @NotNull final InstanceRef error = logRecord.getError();
    @NotNull final InstanceRef stackTrace = logRecord.getStackTrace();

    // TODO(devoncarew): Add an 'isNull' method to InstanceRef.
    if (error.getKind() != InstanceKind.Null) {
      final String padding = StringUtil.repeat(" ", prefix.length());

      if (error.getKind() == InstanceKind.String) {
        // TODO(devoncarew): Handle cases where the string value is truncated.
        console.print(padding + stringValueFromStringRef(error) + "\n", ConsoleViewContentType.ERROR_OUTPUT);
      }
      else {
        final CountDownLatch latch = new CountDownLatch(1);

        final IsolateRef isolateRef = event.getIsolate();
        app.getFlutterDebugProcess().getVmServiceWrapper().callToString(
          isolateRef.getId(), error.getId(),
          new VmServiceConsumers.InvokeConsumerWrapper() {
            @Override
            public void received(InstanceRef response) {
              console.print(padding + stringValueFromStringRef(response) + "\n",
                            ConsoleViewContentType.ERROR_OUTPUT);
              latch.countDown();
            }

            @Override
            public void noGoodResult() {
              console.print(padding + error.getClassRef().getName() + " " + error.getId() + "\n",
                            ConsoleViewContentType.ERROR_OUTPUT);
              latch.countDown();
            }
          });

        try {
          latch.await();
        }
        catch (InterruptedException ignored) {
        }
      }
    }

    if (stackTrace.getKind() != InstanceKind.Null) {
      final String padding = StringUtil.repeat(" ", prefix.length());
      final String out = stackTrace.getValueAsString().trim();

      console.print(
        padding + out.replaceAll("\n", "\n" + padding) + "\n",
        ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  private void printStackTraceToConsole(
    @NotNull ConsoleView console, String padding, @NotNull InstanceRef stackTrace) {
    if (stackTrace.getKind() == InstanceKind.Null) return;

    final String out = stackTrace.getValueAsString();
    console.print(
      padding + out.replaceAll("\n", "\n" + padding),
      ConsoleViewContentType.ERROR_OUTPUT);
  }

  private String stringValueFromStringRef(InstanceRef ref) {
    return ref.getValueAsStringIsTruncated() ? ref.getValueAsString() + "..." : ref.getValueAsString();
  }
}
