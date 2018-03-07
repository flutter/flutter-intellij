/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RenderHelper {
  private final Project myProject;
  private final Listener myListener;

  private String myTesterPath = null;
  private final RenderThread myRenderThread = new RenderThread();

  private VirtualFile myPackages;
  private VirtualFile myFile;
  private FlutterOutline myFileOutline;
  private String myInstrumentedCode;

  private FlutterOutline myWidgetOutline;

  private int myWidth = 0;
  private int myHeight = 0;

  public RenderHelper(@NotNull Project project, @NotNull Listener listener) {
    myProject = project;
    myListener = listener;
    initializeTesterPath();

    myRenderThread.start();
  }

  /**
   * Set a new file, with or without outline.
   */
  public void setFile(@Nullable VirtualFile file,
                      @Nullable FlutterOutline fileOutline,
                      @Nullable String instrumentedCode) {
    myFile = file;
    myFileOutline = fileOutline;
    myInstrumentedCode = instrumentedCode;

    myPackages = null;
    final PubRoot pubRoot = PubRoot.forFile(file);
    if (pubRoot != null) {
      myPackages = pubRoot.getPackages();
    }

    myWidgetOutline = null;
  }

  /**
   * Set the size and render the current widget with this size.
   */
  public void setSize(int width, int height) {
    myWidth = width;
    myHeight = height;
    scheduleRendering();
  }

  /**
   * Set the offset in the current file, and render if a new widget.
   */
  public void setOffset(int offset) {
    final FlutterOutline widgetOutline = getContainingWidgetOutline(offset);
    if (widgetOutline == myWidgetOutline) {
      return;
    }

    myWidgetOutline = widgetOutline;
    if (myWidgetOutline != null) {
      scheduleRendering();
    }
    else {
      myListener.noWidget();
    }
  }

  /**
   * Return the outline for the widget class that is associated with the given offset, and can be rendered.
   * <p>
   * Return null if there is not such widget class outline.
   */
  private FlutterOutline getContainingWidgetOutline(int offset) {
    if (myFileOutline != null && myFileOutline.getChildren() != null) {
      for (FlutterOutline outline : myFileOutline.getChildren()) {
        final int outlineStart = getConvertedFileOffset(outline.getOffset());
        final int outlineEnd = getConvertedFileOffset(outline.getOffset() + outline.getLength());

        int stateStart = -1;
        int stateEnd = -1;
        if (outline.getStateOffset() != null && outline.getStateLength() != null) {
          stateStart = getConvertedFileOffset(outline.getStateOffset());
          stateEnd = getConvertedFileOffset(outline.getStateOffset() + outline.getStateLength());
        }

        if (outlineStart < offset && offset < outlineEnd ||
            stateStart < offset && offset < stateEnd) {
          if (outline.getRenderConstructor() != null) {
            return outline;
          }
        }
      }
    }
    return null;
  }

  private void initializeTesterPath() {
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(myProject);
    if (flutterSdk != null) {
      final VirtualFile tester = flutterSdk.getTester();
      if (tester != null) {
        myTesterPath = tester.getPath();
      }
    }
  }

  private int getConvertedFileOffset(int offset) {
    return DartAnalysisServerService.getInstance(myProject).getConvertedOffset(myFile, offset);
  }

  private void scheduleRendering() {
    if (myPackages == null || myInstrumentedCode == null || myWidgetOutline == null || myWidth == 0 || myHeight == 0) {
      return;
    }

    final String widgetClass = myWidgetOutline.getDartElement().getName();
    final String constructor = myWidgetOutline.getRenderConstructor();
    final RenderRequest request =
      new RenderRequest(myTesterPath, myPackages, myFile, myInstrumentedCode, widgetClass, constructor, myWidth, myHeight, myListener);
    myRenderThread.setRequest(request);
  }

  private static void render(@NotNull RenderRequest request) {
    try {
      final String toRenderPath = request.file.getParent().getPath() + File.separator + ".to_render._dart";
      final File toRenderFile = new File(toRenderPath);
      Files.write(request.codeToRender, toRenderFile, StandardCharsets.UTF_8);

      final String widgetCreation = "new " + request.widgetClass + "." + request.widgetConstructor + "();";

      final URL templateUri = RenderHelper.class.getResource("render_server_template.txt");
      String template = Resources.toString(templateUri, StandardCharsets.UTF_8);
      template = template.replace("// TEMPLATE_VALUE: import library to render", "import '" + toRenderFile.toURI() + "';");
      template = template.replace("new Container(); // TEMPLATE_VALUE: create widget", widgetCreation);
      template = template.replace("{}; // TEMPLATE_VALUE: use flutterDesignerWidgets", "flutterDesignerWidgets;");
      template = template.replace("350.0 /*TEMPLATE_VALUE: width*/", request.width + ".0");
      template = template.replace("400.0 /*TEMPLATE_VALUE: height*/", request.height + ".0");

      final String renderServerPath = request.file.getParent().getPath() + File.separator + ".render_server._dart";
      final File renderServerFile = new File(renderServerPath);
      Files.write(template, renderServerFile, StandardCharsets.UTF_8);

      final Process process =
        new ProcessBuilder(request.testerPath,
                           "--non-interactive",
                           "--use-test-fonts",
                           "--packages=" + request.packages.getPath(),
                           renderServerPath)
          .start();

      final int exitCode = process.waitFor();
      if (exitCode != 0) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        request.listener.onFailure(null);
        return;
      }

      // The output stream must have a single line with the JSON response.
      JsonObject response = null;
      final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (true) {
        final String responseLine = reader.readLine();
        if (responseLine == null) {
          break;
        }
        if (responseLine.startsWith("Observatory listening")) {
          continue;
        }
        // The line must be a JSON response.
        try {
          response = new Gson().fromJson(responseLine, JsonObject.class);
        }
        catch (Throwable ignored) {
        }
        break;
      }

      // Fail if unable to find the valid response.
      if (response == null) {
        request.listener.onFailure(null);
        return;
      }

      request.listener.onResponse(response);
    }
    catch (Throwable e) {
      request.listener.onFailure(e);
    }
  }

  public interface Listener {
    void noWidget();

    void onResponse(JsonObject response);

    void onFailure(Throwable e);
  }

  private class RenderThread extends Thread {
    final Object myRequestLock = new Object();
    RenderRequest myRequest;

    RenderThread() {
      setDaemon(true);
    }

    void setRequest(RenderRequest request) {
      synchronized (myRequestLock) {
        myRequest = request;
        myRequestLock.notifyAll();
      }
    }

    @Override
    public void run() {
      //noinspection InfiniteLoopStatement
      while (true) {
        final RenderRequest request;
        try {
          synchronized (myRequestLock) {
            if (myRequest == null) {
              myRequestLock.wait();
            }
            request = myRequest;
            myRequest = null;
            if (request == null) {
              continue;
            }
          }
        }
        catch (InterruptedException ignored) {
          continue;
        }

        render(request);
      }
    }
  }
}

/**
 * Container with a set of parameters for rendering.
 */
class RenderRequest {
  static int nextId = 0;

  final int id = nextId++;

  final String testerPath;

  final VirtualFile packages;
  final VirtualFile file;
  final String codeToRender;
  final String widgetClass;
  final String widgetConstructor;
  final int width;
  final int height;

  final RenderHelper.Listener listener;

  RenderRequest(String testerPath, VirtualFile packages, VirtualFile file,
                String codeToRender,
                String widgetClass,
                String widgetConstructor,
                int width, int height,
                RenderHelper.Listener listener) {
    this.testerPath = testerPath;
    this.packages = packages;
    this.file = file;
    this.codeToRender = codeToRender;
    this.widgetClass = widgetClass;
    this.widgetConstructor = widgetConstructor;
    this.width = width;
    this.height = height;
    this.listener = listener;
  }
}
