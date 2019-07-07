/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.logging;

import com.google.gson.JsonObject;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.QueueProcessor;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.ServiceExtensions;
import io.flutter.vmService.VmServiceConsumers;
import org.apache.commons.lang3.text.WordUtils;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class FlutterConsoleLogManager {
  private static final Logger LOG = Logger.getInstance(FlutterConsoleLogManager.class);

  private static final boolean SHOW_STRUCTURED_ERRORS = true;

  private static final ConsoleViewContentType SUBTLE_CONTENT_TYPE =
    new ConsoleViewContentType("subtle", SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes());

  @NotNull final FlutterApp app;

  private static QueueProcessor<Runnable> queue;
  private CompletableFuture<InspectorService.ObjectGroup> objectGroup;

  public FlutterConsoleLogManager(@NotNull FlutterApp app) {
    this.app = app;

    if (queue == null) {
      queue = QueueProcessor.createRunnableQueueProcessor();
    }

    if (SHOW_STRUCTURED_ERRORS) {
      assert (app.getFlutterDebugProcess() != null);
      assert (app.getVmService() != null);
      objectGroup = InspectorService.createGroup(app, app.getFlutterDebugProcess(), app.getVmService(), "run-console-group");

      // Calling this will override the default Flutter stdout error display.
      app.hasServiceExtension(ServiceExtensions.toggleShowStructuredErrors.getExtension(), (present) -> {
        if (present) {
          app.callBooleanExtension(ServiceExtensions.toggleShowStructuredErrors.getExtension(), true);
        }
      });
    }
  }

  private DiagnosticsNode parseDiagnosticsNode(@NotNull JsonObject json) {
    return new DiagnosticsNode(json, objectGroup, app, false, null);
  }

  public void handleFlutterErrorEvent(@NotNull Event event) {
    if (SHOW_STRUCTURED_ERRORS) {
      queue.add(() -> {
        try {
          processFlutterErrorEvent(event);
        }
        catch (Throwable t) {
          LOG.warn(t);
        }
      });
    }
  }

  /**
   * Pretty print the error using the available console syling attributes.
   */
  private void processFlutterErrorEvent(@NotNull Event event) {
    final ConsoleView console = app.getConsole();
    assert console != null;

    final ExtensionData extensionData = event.getExtensionData();
    final DiagnosticsNode diagnosticsNode = parseDiagnosticsNode(extensionData.getJson().getAsJsonObject());
    final String description = diagnosticsNode.toString();

    // TODO(devoncarew): Create a hyperlink to widget - ala 'widget://inspector-1347'.
    console.print("\n" + description + ":\n", ConsoleViewContentType.ERROR_OUTPUT);

    final String indent = "  ";

    for (DiagnosticsNode property : diagnosticsNode.getInlineProperties()) {
      printDiagnosticsNodeProperty(console, indent, property, null);
    }
  }

  private static final int COL_WIDTH = 102;

  private void printDiagnosticsNodeProperty(ConsoleView console, String indent, DiagnosticsNode property,
                                            ConsoleViewContentType contentType) {
    if (contentType == null) {
      contentType = SUBTLE_CONTENT_TYPE;

      if (property.getLevel().compareTo(DiagnosticLevel.error) >= 0) {
        contentType = ConsoleViewContentType.ERROR_OUTPUT;
      }
      else if (property.getLevel().compareTo(DiagnosticLevel.summary) >= 0) {
        contentType = ConsoleViewContentType.NORMAL_OUTPUT;
      }
    }

    console.print(indent, contentType);

    int col = 0;
    if (property.getShowName()) {
      console.print(property.getName(), contentType);
      col += property.getName().length();
    }
    if (property.getShowName() && property.getShowSeparator()) {
      console.print(property.getSeparator() + " ", contentType);
      col += property.getSeparator().length() + 1;
    }

    // Wrap long descriptions.
    if (property.getDescription() != null) {
      final String description = property.getDescription();

      if (property.getAllowWrap()) {
        final String[] lines = wrap(description, COL_WIDTH - indent.length(), col);

        if (lines.length == 1) {
          console.print(lines[0] + "\n", contentType);
        }
        else {
          console.print(lines[0], contentType);
          console.print("  <show more>\n", SUBTLE_CONTENT_TYPE);

          for (int i = 1; i < lines.length; i++) {
            console.print(indent + "  " + lines[i] + "\n", contentType);
          }
        }
      }
      else {
        final boolean isSpacer = description.startsWith("◢◤◢◤◢◤◢◤◢◤◢◤") || StringUtil.equals("ErrorSpacer", property.getType());
        console.print(description + "\n", isSpacer ? SUBTLE_CONTENT_TYPE : contentType);
      }
    }
    else {
      console.print("\n", contentType);
    }

    if (property.hasInlineProperties()) {
      final String childIndent = indent + "  ";
      for (DiagnosticsNode childProperty : property.getInlineProperties()) {
        printDiagnosticsNodeProperty(console, childIndent, childProperty, contentType);
      }
    }
  }

  private String[] wrap(String line, int colWidth, int firstLineIndent) {
    final String trimmed = StringUtil.trimLeading(line);
    final int leading = line.length() - trimmed.length();
    // WordUtils.wrap does not preserve leading spaces.
    final String result = WordUtils.wrap(StringUtil.repeat(" ", firstLineIndent) + line, colWidth, "\n", false);
    if (leading == 0) {
      return result.split("\n");
    }
    else {
      final String leadingStr = StringUtil.repeat(" ", leading);
      return (leadingStr + result.replaceAll("\n", "\n" + leadingStr)).split("\n");
    }
  }

  public void handleLoggingEvent(@NotNull Event event) {
    queue.add(() -> {
      try {
        processEvent(event);
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    });
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
