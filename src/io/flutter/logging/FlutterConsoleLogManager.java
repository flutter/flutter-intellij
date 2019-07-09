/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.logging;


import com.google.common.annotations.VisibleForTesting;
import com.google.gson.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.QueueProcessor;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.vmService.VmServiceConsumers;
import org.apache.commons.lang3.text.WordUtils;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Handle displaying dart:developer log messages and Flutter.Error messages in the Run and Debug
 * console.
 */
public class FlutterConsoleLogManager {
  private static final Logger LOG = Logger.getInstance(FlutterConsoleLogManager.class);

  public static final boolean SHOW_STRUCTURED_ERRORS = true;

  private static final ConsoleViewContentType SUBTLE_CONTENT_TYPE =
    new ConsoleViewContentType("subtle", SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes());

  private static QueueProcessor<Runnable> queue;
  private CompletableFuture<InspectorService.ObjectGroup> objectGroup;

  @NotNull final VmService service;
  @NotNull final ConsoleView console;

  public FlutterConsoleLogManager(@NotNull VmService service, @NotNull ConsoleView console) {
    this.service = service;
    this.console = console;

    if (queue == null) {
      queue = QueueProcessor.createRunnableQueueProcessor();
    }
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

  private DiagnosticsNode parseDiagnosticsNode(@NotNull JsonObject json) {
    // TODO(devoncarew): What are the implications of passing in null for the the app?
    return new DiagnosticsNode(json, objectGroup, null, false, null);
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
        processLoggingEvent(event);
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    });
  }

  @VisibleForTesting
  public void processLoggingEvent(@NotNull Event event) {
    final LogRecord logRecord = event.getLogRecord();
    if (logRecord == null) return;

    final IsolateRef isolateRef = event.getIsolate();

    final InstanceRef message = logRecord.getMessage();
    @NotNull final InstanceRef loggerName = logRecord.getLoggerName();

    final String name = loggerName.getValueAsString().isEmpty() ? "log" : loggerName.getValueAsString();
    final String prefix = "[" + name + "] ";
    final String messageStr = getFullStringValue(service, isolateRef.getId(), message);

    console.print(prefix, SUBTLE_CONTENT_TYPE);
    console.print(messageStr + "\n", ConsoleViewContentType.NORMAL_OUTPUT);

    @NotNull final InstanceRef error = logRecord.getError();
    @NotNull final InstanceRef stackTrace = logRecord.getStackTrace();

    if (!error.isNull()) {
      final String padding = StringUtil.repeat(" ", prefix.length());

      if (error.getKind() == InstanceKind.String) {
        String string = getFullStringValue(service, isolateRef.getId(), error);

        // Handle json in the error payload.
        boolean isJson = false;
        try {
          final JsonElement json = new JsonParser().parse(string);
          isJson = true;

          string = new GsonBuilder().setPrettyPrinting().create().toJson(json);
          string = string.replaceAll("\n", "\n" + padding);
        }
        catch (JsonSyntaxException ignored) {
        }

        console.print(padding + string + "\n", isJson ? ConsoleViewContentType.NORMAL_OUTPUT : ConsoleViewContentType.ERROR_OUTPUT);
      }
      else {
        final CountDownLatch latch = new CountDownLatch(1);

        service.invoke(
          isolateRef.getId(), error.getId(),
          "toString", Collections.emptyList(),
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

    if (!stackTrace.isNull()) {
      final String padding = StringUtil.repeat(" ", prefix.length());
      final String out = stackTrace.getValueAsString().trim();

      console.print(
        padding + out.replaceAll("\n", "\n" + padding) + "\n",
        ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  private void printStackTraceToConsole(
    @NotNull ConsoleView console, String padding, @NotNull InstanceRef stackTrace) {
    if (stackTrace.isNull()) return;

    final String out = stackTrace.getValueAsString();
    console.print(
      padding + out.replaceAll("\n", "\n" + padding),
      ConsoleViewContentType.ERROR_OUTPUT);
  }

  private String stringValueFromStringRef(InstanceRef ref) {
    return ref.getValueAsStringIsTruncated() ? formatTruncatedString(ref) : ref.getValueAsString();
  }

  private String stringValueFromStringRef(Instance instance) {
    return instance.getValueAsStringIsTruncated() ? instance.getValueAsString() + "..." : instance.getValueAsString();
  }

  private String formatTruncatedString(InstanceRef ref) {
    return ref.getValueAsString() + "...";
  }

  private String getFullStringValue(@NotNull VmService service, String isolateId, @Nullable InstanceRef ref) {
    if (ref == null) return null;

    if (!ref.getValueAsStringIsTruncated()) {
      return ref.getValueAsString();
    }

    final CountDownLatch latch = new CountDownLatch(1);
    final String[] result = new String[1];

    service.getObject(isolateId, ref.getId(), 0, ref.getLength(), new GetObjectConsumer() {
      @Override
      public void onError(RPCError error) {
        result[0] = formatTruncatedString(ref);
        latch.countDown();
      }

      @Override
      public void received(Obj response) {
        if (response instanceof Instance && ((Instance)response).getKind() == InstanceKind.String) {
          result[0] = stringValueFromStringRef((Instance)response);
        }
        else {
          result[0] = formatTruncatedString(ref);
        }

        latch.countDown();
      }

      @Override
      public void received(Sentinel response) {
        result[0] = formatTruncatedString(ref);
        latch.countDown();
      }
    });

    try {
      latch.await(1, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      return null;
    }

    return result[0];
  }
}
