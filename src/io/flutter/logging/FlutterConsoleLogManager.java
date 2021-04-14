/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.QueueProcessor;
import io.flutter.FlutterInitializer;
import io.flutter.devtools.DevToolsUtils;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.DiagnosticsTreeStyle;
import io.flutter.inspector.InspectorService;
import io.flutter.jxbrowser.EmbeddedBrowser;
import io.flutter.jxbrowser.JxBrowserManager;
import io.flutter.jxbrowser.JxBrowserStatus;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.JsonUtils;
import io.flutter.view.FlutterView;
import io.flutter.vmService.VmServiceConsumers;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handle displaying dart:developer log messages and Flutter.Error messages in the Run and Debug
 * console.
 */
public class FlutterConsoleLogManager {
  private static final Logger LOG = Logger.getInstance(FlutterConsoleLogManager.class);

  private static final String consolePreferencesSetKey = "io.flutter.console.preferencesSet";
  private static final String DEEP_LINK_GROUP_ID = "deeplink";

  private static final ConsoleViewContentType TITLE_CONTENT_TYPE =
    new ConsoleViewContentType("title", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.toTextAttributes());
  private static final ConsoleViewContentType NORMAL_CONTENT_TYPE = ConsoleViewContentType.NORMAL_OUTPUT;
  private static final ConsoleViewContentType SUBTLE_CONTENT_TYPE =
    new ConsoleViewContentType("subtle", SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes());
  private static final ConsoleViewContentType ERROR_CONTENT_TYPE = ConsoleViewContentType.ERROR_OUTPUT;

  private static QueueProcessor<Runnable> queue;
  private static final AtomicInteger queueLength = new AtomicInteger();

  /**
   * Set our preferred settings for the run console.
   */
  public static void initConsolePreferences() {
    final PropertiesComponent properties = PropertiesComponent.getInstance();
    if (!properties.getBoolean(consolePreferencesSetKey)) {
      properties.setValue(consolePreferencesSetKey, true);

      // Set our preferred default settings for console text wrapping.
      final EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
      editorSettings.setUseSoftWraps(true, SoftWrapAppliancePlaces.CONSOLE);
    }
  }

  @NotNull final ConsoleView console;
  @NotNull final FlutterApp app;

  private int frameErrorCount = 0;

  private CompletableFuture<InspectorService.ObjectGroup> objectGroup;

  public FlutterConsoleLogManager(@NotNull ConsoleView console, @NotNull FlutterApp app) {
    this.console = console;
    this.app = app;

    app.addStateListener(new FlutterApp.FlutterAppListener() {
      @Override
      public void notifyFrameRendered() {
        frameErrorCount = 0;
      }

      @Override
      public void stateChanged(FlutterApp.State newState) {
        frameErrorCount = 0;
      }

      @Override
      public void notifyAppReloaded() {
        frameErrorCount = 0;
      }

      @Override
      public void notifyAppRestarted() {
        frameErrorCount = 0;
      }
    });

    if (queue == null) {
      queue = QueueProcessor.createRunnableQueueProcessor();
    }
  }

  /**
   * This method is used to delay construction of the InspectorService ObjectGroup instance until its first used.
   * <p>
   * This ensures that the app's VMService field has been populated.
   */
  @Nullable
  private CompletableFuture<InspectorService.ObjectGroup> getCreateInspectorGroup() {
    if (objectGroup == null) {
      if (app.getFlutterDebugProcess() == null || app.getVmService() == null) {
        return null;
      }

      // TODO(devoncarew): This creates a new InspectorService but may not dispose of it.
      objectGroup = InspectorService.createGroup(app, app.getFlutterDebugProcess(), app.getVmService(), "console-group");
      objectGroup.whenCompleteAsync((group, error) -> {
        if (group != null) {
          Disposer.register(app, group.getInspectorService());
        }
      });
    }

    return objectGroup;
  }

  public void handleFlutterErrorEvent(@NotNull Event event) {
    final CompletableFuture<InspectorService.ObjectGroup> objectGroup = getCreateInspectorGroup();
    if (objectGroup == null) {
      return;
    }

    try {
      final ExtensionData extensionData = event.getExtensionData();
      final JsonObject jsonObject = extensionData.getJson().getAsJsonObject();
      final DiagnosticsNode diagnosticsNode = new DiagnosticsNode(jsonObject, objectGroup, app, false, null);

      // Send analytics for the diagnosticsNode.
      if (isFirstErrorForFrame()) {
        final String errorId = FlutterErrorHelper.getAnalyticsId(diagnosticsNode);
        if (errorId != null) {
          FlutterInitializer.getAnalytics().sendEvent(
            "flutter-error", errorId,
            // Note: this can be null from tests.
            app.getProject() == null ? null : FlutterSdk.getFlutterSdk(app.getProject()));
        }
      }

      if (FlutterSettings.getInstance().isShowStructuredErrors()) {
        queueLength.incrementAndGet();

        queue.add(() -> {
          try {
            processFlutterErrorEvent(diagnosticsNode);
          }
          catch (Throwable t) {
            LOG.warn(t);
          }
          finally {
            queueLength.decrementAndGet();

            synchronized (queueLength) {
              queueLength.notifyAll();
            }
          }
        });
      }
    }
    catch (Throwable t) {
      LOG.warn(t);
    }
  }

  /**
   * Wait until all pending work has completed.
   */
  public void flushFlutterErrorQueue() {
    // If the queue isn't empty, then wait until the all the items have been processed.
    if (queueLength.get() > 0) {
      try {
        while (queueLength.get() > 0) {
          synchronized (queueLength) {
            queueLength.wait();
          }
        }
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private static final int errorSeparatorLength = 100;
  private static final String errorSeparatorChar = "=";

  private static final ArrayList<DiagnosticsNode> emptyList = new ArrayList<>();

  /**
   * Pretty print the error using the available console syling attributes.
   */
  private void processFlutterErrorEvent(@NotNull DiagnosticsNode diagnosticsNode) {
    final String description = " " + diagnosticsNode.toString() + " ";

    final boolean terseError = !isFirstErrorForFrame();

    frameErrorCount++;

    final String prefix = "========";
    final String suffix = "==";

    console.print("\n" + prefix, TITLE_CONTENT_TYPE);
    console.print(description, NORMAL_CONTENT_TYPE);
    console.print(
      StringUtil.repeat(errorSeparatorChar, Math.max(
        errorSeparatorLength - prefix.length() - description.length() - suffix.length(), 0)),
      TITLE_CONTENT_TYPE);
    console.print(suffix + "\n", TITLE_CONTENT_TYPE);

    // TODO(devoncarew): Create a hyperlink to a widget - ala 'widget://inspector-1347'.

    if (terseError) {
      for (DiagnosticsNode property : diagnosticsNode.getInlineProperties()) {
        printTerseNodeProperty(console, "", property);
      }
    }
    else {
      DiagnosticLevel lastLevel = null;
      String errorSummary = null;

      for (DiagnosticsNode property : diagnosticsNode.getInlineProperties()) {
        // Add blank line between hint and non-hint properties.
        if (lastLevel != property.getLevel()) {
          if (lastLevel == DiagnosticLevel.hint || property.getLevel() == DiagnosticLevel.hint) {
            console.print("\n", NORMAL_CONTENT_TYPE);
          }
        }

        lastLevel = property.getLevel();

        if (StringUtil.equals("ErrorSummary", property.getType())) {
          errorSummary = property.getDescription();
        } else if (StringUtil.equals("DevToolsDeepLinkProperty", property.getType()) &&
                FlutterSettings.getInstance().isEnableEmbeddedBrowsers() &&
                JxBrowserManager.getInstance().getStatus().equals(JxBrowserStatus.INSTALLED)) {
          showDeepLinkNotification(property, errorSummary);
          continue;
        }

        printDiagnosticsNodeProperty(console, "", property, null, false);
      }
    }

    console.print(StringUtil.repeat(errorSeparatorChar, errorSeparatorLength) + "\n", TITLE_CONTENT_TYPE);
  }

  private boolean isFirstErrorForFrame() {
    return frameErrorCount == 0;
  }

  private void printTerseNodeProperty(ConsoleView console, String indent, DiagnosticsNode property) {
    boolean skip = true;

    if (property.getLevel() == DiagnosticLevel.summary) {
      skip = false;
    }
    else if (property.hasChildren()) {
      final CompletableFuture<ArrayList<DiagnosticsNode>> future = property.getChildren();
      final ArrayList<DiagnosticsNode> children = future.getNow(emptyList);
      if (children.stream().noneMatch(DiagnosticsNode::hasChildren)) {
        skip = false;
      }
    }

    if (skip) {
      return;
    }

    final ConsoleViewContentType contentType = getContentTypeFor(property.getLevel());

    console.print(indent, contentType);

    if (property.getShowName()) {
      console.print(property.getName(), contentType);

      if (property.getShowSeparator()) {
        console.print(property.getSeparator() + " ", contentType);
      }
    }

    final String description = property.getDescription() == null ? "" : property.getDescription();
    console.print(description + "\n", contentType);

    final String childIndent = getChildIndent(indent, property);

    if (property.hasInlineProperties()) {
      for (DiagnosticsNode childProperty : property.getInlineProperties()) {
        printDiagnosticsNodeProperty(console, childIndent, childProperty, contentType, false);
      }
    }

    if (property.hasChildren()) {
      final CompletableFuture<ArrayList<DiagnosticsNode>> future = property.getChildren();
      final ArrayList<DiagnosticsNode> children = future.getNow(emptyList);

      for (DiagnosticsNode child : children) {
        printDiagnosticsNodeProperty(console, childIndent, child, contentType, false);
      }
    }
  }

  private void printDiagnosticsNodeProperty(ConsoleView console, String indent, DiagnosticsNode property,
                                            ConsoleViewContentType contentType,
                                            boolean isInChild) {
    // TODO(devoncarew): Change the error message display in the framework.
    if (property.getDescription() != null && property.getLevel() == DiagnosticLevel.info) {
      // Elide framework blank styling lines.
      if (StringUtil.equals("ErrorSpacer", property.getType())) {
        return;
      }
    }

    if (contentType == null) {
      contentType = getContentTypeFor(property.getLevel());
    }

    console.print(indent, contentType);

    if (property.getShowName()) {
      final String name = property.getName();
      console.print(name == null ? "" : name, contentType);

      if (property.getShowSeparator()) {
        console.print(property.getSeparator() + " ", contentType);
      }
    }

    final String description = property.getDescription() == null ? "" : property.getDescription();
    console.print(description + "\n", contentType);

    if (property.hasInlineProperties()) {
      String childIndent = getChildIndent(indent, property);
      if (property.getStyle() == DiagnosticsTreeStyle.shallow && !indent.startsWith("...")) {
        // Render properties of shallow nodes as collapesed.
        childIndent = "...  " + indent;
      }
      for (DiagnosticsNode childProperty : property.getInlineProperties()) {
        printDiagnosticsNodeProperty(console, childIndent, childProperty, contentType, isInChild);
      }
    }

    if (property.hasChildren()) {
      final CompletableFuture<ArrayList<DiagnosticsNode>> future = property.getChildren();
      final ArrayList<DiagnosticsNode> children = future.getNow(emptyList);

      // Don't collapse children if it's just a flat list of children.
      if (!isInChild && children.stream().noneMatch(DiagnosticsNode::hasChildren)) {
        final String childIndent = getChildIndent(indent, property);
        for (DiagnosticsNode child : children) {
          printDiagnosticsNodeProperty(console, childIndent, child, contentType, false);
        }
      }
      else {
        if (property.getStyle() != DiagnosticsTreeStyle.shallow) {
          // For deep trees, we show the text as collapsed.
          final String childIndent = isInChild ? getChildIndent(indent, property) : "...  " + indent;

          for (DiagnosticsNode child : children) {
            printDiagnosticsNodeProperty(console, childIndent, child, contentType, true);
          }
        }
      }
    }

    // Print an extra line after the summary.
    if (property.getLevel() == DiagnosticLevel.summary) {
      console.print("\n", contentType);
    }
  }

  private void showDeepLinkNotification(DiagnosticsNode property, String errorSummary) {
    // TODO(helin24): We can register a notification group in plugin.xml for 2020.3
    // (see https://plugins.jetbrains.com/docs/intellij/notifications.html?from=jetbrains.org#top-level-notifications)
    final NotificationGroup notificationGroup = new NotificationGroup(DEEP_LINK_GROUP_ID, NotificationDisplayType.STICKY_BALLOON);
    final Notification notification = new Notification(
      DEEP_LINK_GROUP_ID,
      "",
      errorSummary,
      NotificationType.INFORMATION);
    notification.setIcon(AllIcons.General.BalloonWarning);
    notification.addAction(new AnAction("Inspect Widget") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        // Show inspector window if it's not already visible.
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(app.getProject());
        if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
          return;
        }

        final ToolWindow toolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
        if (toolWindow != null && !toolWindow.isVisible()) {
          toolWindow.show();
        }

        final String widgetId = DevToolsUtils.findWidgetId(property.getValue());
        EmbeddedBrowser.getInstance(app.getProject()).updatePanelToWidget(widgetId);
        notification.expire();

        FlutterInitializer.getAnalytics().sendEvent(
          "deep-link-clicked",
          errorSummary.contains("RenderFlex overflowed") ? "overflow" : "unknown",
          FlutterSdk.getFlutterSdk(app.getProject())
        );
      }
    });
    Notifications.Bus.notify(notification, app.getProject());
    Executors.newSingleThreadScheduledExecutor().schedule(notification::expire, 25, TimeUnit.SECONDS);
  }

  private String getChildIndent(String indent, DiagnosticsNode property) {
    if (property.getStyle() == DiagnosticsTreeStyle.flat) {
      return indent;
    }
    else {
      return indent + "  ";
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

  private ConsoleViewContentType getContentTypeFor(DiagnosticLevel level) {
    switch (level) {
      case error:
      case summary:
        return ERROR_CONTENT_TYPE;
      case hint:
        return NORMAL_CONTENT_TYPE;
      default:
        return SUBTLE_CONTENT_TYPE;
    }
  }

  @VisibleForTesting
  public void processLoggingEvent(@NotNull Event event) {
    final LogRecord logRecord = event.getLogRecord();
    if (logRecord == null) return;

    final VmService service = app.getVmService();
    if (service == null) {
      return;
    }

    final IsolateRef isolateRef = event.getIsolate();

    final InstanceRef message = logRecord.getMessage();
    @NotNull final InstanceRef loggerName = logRecord.getLoggerName();

    final String name = loggerName.getValueAsString().isEmpty() ? "log" : loggerName.getValueAsString();
    final String prefix = "[" + name + "] ";
    final String messageStr = getFullStringValue(service, isolateRef.getId(), message);

    console.print(prefix, SUBTLE_CONTENT_TYPE);
    console.print(messageStr + "\n", NORMAL_CONTENT_TYPE);

    @NotNull final InstanceRef error = logRecord.getError();
    @NotNull final InstanceRef stackTrace = logRecord.getStackTrace();

    if (!error.isNull()) {
      final String padding = StringUtil.repeat(" ", prefix.length());

      if (error.getKind() == InstanceKind.String) {
        String string = getFullStringValue(service, isolateRef.getId(), error);

        // Handle json in the error payload.
        boolean isJson = false;
        try {
          final JsonElement json = JsonUtils.parseString(string);
          isJson = true;

          string = new GsonBuilder().setPrettyPrinting().create().toJson(json);
          string = string.replaceAll("\n", "\n" + padding);
        }
        catch (JsonSyntaxException ignored) {
        }

        console.print(padding + string + "\n", isJson ? ConsoleViewContentType.NORMAL_OUTPUT : ERROR_CONTENT_TYPE);
      }
      else {
        final CountDownLatch latch = new CountDownLatch(1);

        service.invoke(
          isolateRef.getId(), error.getId(),
          "toString", Collections.emptyList(),
          true,
          new VmServiceConsumers.InvokeConsumerWrapper() {
            @Override
            public void received(InstanceRef response) {
              console.print(padding + stringValueFromStringRef(response) + "\n", ERROR_CONTENT_TYPE);
              latch.countDown();
            }

            @Override
            public void noGoodResult() {
              console.print(padding + error.getClassRef().getName() + " " + error.getId() + "\n", ERROR_CONTENT_TYPE);
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
      final String out = stackTrace.getValueAsString() == null ? "" : stackTrace.getValueAsString().trim();

      console.print(
        padding + out.replaceAll("\n", "\n" + padding) + "\n", ERROR_CONTENT_TYPE);
    }
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
