/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.VmServiceListener;
import org.jetbrains.annotations.NotNull;

import org.dartlang.vm.service.element.Event;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.JsonUtils;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class DevToolsUtils {
  public static String findWidgetId(String url) {
    final String searchFor = "inspectorRef=";
    final String[] split = url.split("&");
    for (String part : split) {
      if (part.startsWith(searchFor)) {
        return part.substring(searchFor.length());
      }
    }
    return null;
  }

  /**
   * Register a VM listener that listens for devtools "ToolEvent"s.
   * @param app the associated app
   */
  public static void registerDevToolsVmServiceListener(@NotNull FlutterApp app) {
    // This functionality lived originally in the `FlutterInspectorService` introduced in:
    // https://github.com/flutter/flutter-intellij/pull/6881
    //
    // It was mistakenly removed in: https://github.com/flutter/flutter-intellij/pull/7867
    //
    // TODO(pq): some follow-ups:
    //  * consider a better long-term home for this utility
    //  * do we need to de-register?

    VmService vmService = app.getVmService();
    if (vmService == null) return;

    vmService.addVmServiceListener(new VmServiceListener() {
      @Override
      public void connectionOpened() { }

      @Override
      public void received(String streamId, Event event) {
        if (streamId != null) {
          onVmServiceReceived(app, streamId, event);
        }
      }

      @Override
      public void connectionClosed() { }
    });
  }

  private static void onVmServiceReceived(@NotNull FlutterApp app, @NotNull String streamId, @Nullable Event event) {
    Application application = ApplicationManager.getApplication();
    if (application == null) return;

    if (streamId.equals("ToolEvent")) {
      Optional<Event> eventOrNull = Optional.ofNullable(event);
      if ("navigate".equals(eventOrNull.map(Event::getExtensionKind).orElse(null))) {
        JsonObject json = eventOrNull.map(Event::getExtensionData).map(ExtensionData::getJson).orElse(null);
        if (json == null) return;

        String fileUri = JsonUtils.getStringMember(json, "fileUri");
        if (fileUri == null) return;

        String path = null;
        try {
          path = new URI(fileUri).toURL().getFile();
        }
        catch (MalformedURLException | URISyntaxException e) {
          // A null path will cause an early return.
        }
        if (path == null) return;

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        int line = JsonUtils.getIntMember(json, "line");
        int column = JsonUtils.getIntMember(json, "column");

        application.invokeLater(() -> {
          if (file != null && line >= 0 && column >= 0) {
            XSourcePositionImpl position = XSourcePositionImpl.create(file, line - 1, column - 1);
            position.createNavigatable(app.getProject()).navigate(false);
          }
        });
      }
    }
  }

  public String getColorHexCode() {
    return ColorUtil.toHex(UIUtil.getEditorPaneBackground());
  }

  public Boolean getIsBackgroundBright() {
    return JBColor.isBright();
  }

  public @NotNull Float getFontSize() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    if (manager == null) {
      // Return the default normal font size if editor manager is not found.
      return UIUtil.getFontSize(UIUtil.FontSize.NORMAL);
    }
    return (float)manager.getGlobalScheme().getEditorFontSize();
  }
}
