/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.JsonObject;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.DaemonApi;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RenderHelper {
  private final Project myProject;
  private final Listener myListener;

  private final FlutterSdk myFlutterSdk;
  private final RenderThread myRenderThread = new RenderThread();

  private PubRoot myPubRoot;
  private Module myModule;

  private VirtualFile myFile;
  private FlutterOutline myFileOutline;
  private String myInstrumentedCode;

  private FlutterOutline myWidgetOutline;

  private int myWidth = 0;
  private int myHeight = 0;

  public RenderHelper(@NotNull Project project, @NotNull Listener listener) {
    myProject = project;
    myListener = listener;

    myFlutterSdk = FlutterSdk.getFlutterSdk(myProject);

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

    myPubRoot = PubRoot.forFile(file);
    if (myPubRoot != null) {
      myModule = myPubRoot.getModule(myProject);
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
    final FlutterOutline previousWidgetOutline = myWidgetOutline;
    myWidgetOutline = getContainingWidgetOutline(offset);
    if (myWidgetOutline == null) {
      myListener.onFailure(RenderProblemKind.NO_WIDGET, null);
    }
    else if (myWidgetOutline.getRenderConstructor() == null) {
      myListener.onFailure(RenderProblemKind.NOT_RENDERABLE_WIDGET, myWidgetOutline);
      myWidgetOutline = null;
    }
    else if (myWidgetOutline != previousWidgetOutline) {
      myListener.onRenderableWidget(myWidgetOutline);
      scheduleRendering();
    }
  }

  /**
   * Return the outline for the widget class that is associated with the given offset.
   * <p>
   * Return null if there is no associated widget class outline.
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
          if (outline.isWidgetClass()) {
            return outline;
          }
          else {
            return null;
          }
        }
      }
    }
    return null;
  }

  private int getConvertedFileOffset(int offset) {
    return DartAnalysisServerService.getInstance(myProject).getConvertedOffset(myFile, offset);
  }

  private void scheduleRendering() {
    if (myPubRoot == null ||
        myModule == null ||
        myInstrumentedCode == null ||
        myWidgetOutline == null ||
        myWidth == 0 || myHeight == 0) {
      return;
    }

    // TODO: Expose this as an error to the user.
    if (myFlutterSdk == null) {
      return;
    }

    final String widgetClass = myWidgetOutline.getDartElement().getName();
    final String constructor = myWidgetOutline.getRenderConstructor();
    final RenderRequest request =
      new RenderRequest(myFlutterSdk,
                        myProject, myPubRoot, myModule,
                        myInstrumentedCode,
                        myWidgetOutline, widgetClass, constructor,
                        myWidth, myHeight,
                        myListener);
    myRenderThread.setRequest(request);
  }

  public interface Listener {
    void onResponse(@NotNull FlutterOutline widget, @NotNull JsonObject response);

    void onFailure(@NotNull RenderProblemKind kind, @Nullable FlutterOutline widget);

    void onRenderableWidget(@NotNull FlutterOutline widget);

    void onLocalException(@NotNull FlutterOutline widget, @NotNull Throwable localException);

    void onRemoteException(@NotNull FlutterOutline widget, @NotNull JsonObject remoteException);
  }
}

/**
 * Container with a set of parameters for rendering.
 */
class RenderRequest {
  static int nextId = 0;

  final int id = nextId++;

  @NotNull final FlutterSdk flutterSdk;

  @NotNull final Project project;
  @NotNull final PubRoot pubRoot;
  @NotNull final Module module;

  @NotNull final String codeToRender;
  @NotNull final FlutterOutline widget;
  @NotNull final String widgetClass;
  @NotNull final String widgetConstructor;

  final int width;
  final int height;

  final RenderHelper.Listener listener;

  RenderRequest(@NotNull FlutterSdk flutterSdk,
                @NotNull Project project,
                @NotNull PubRoot pubRoot,
                @NotNull Module module,
                @NotNull String codeToRender,
                @NotNull FlutterOutline widget,
                @NotNull String widgetClass,
                @NotNull String widgetConstructor,
                int width,
                int height,
                RenderHelper.Listener listener) {
    this.flutterSdk = flutterSdk;
    this.project = project;
    this.pubRoot = pubRoot;
    this.module = module;
    this.codeToRender = codeToRender;
    this.widget = widget;
    this.widgetClass = widgetClass;
    this.widgetConstructor = widgetConstructor;
    this.width = width;
    this.height = height;
    this.listener = listener;
  }
}

class RenderThread extends Thread {
  final Object myRequestLock = new Object();
  RenderRequest myRequest;

  RenderRequest myProcessRequest;
  FlutterApp myApp;

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

  private void render(@NotNull RenderRequest request) {
    final FlutterOutline widget = request.widget;

    try {
      final String packagePath = request.pubRoot.getPath();
      final File dartToolDirectory = new File(packagePath, ".dart_tool");
      final File flutterDirectory = new File(dartToolDirectory, "flutter");
      final File designerDirectory = new File(flutterDirectory, "designer");

      final File toRenderFile = new File(designerDirectory, "to_render.dart");
      FileUtil.writeToFile(toRenderFile, request.codeToRender);

      final String widgetCreation = "new " + request.widgetClass + "." + request.widgetConstructor + "();";

      final URL templateUri = RenderHelper.class.getResource("render_server_template.txt");
      String template = Resources.toString(templateUri, StandardCharsets.UTF_8);
      template = template.replace("// TEMPLATE_VALUE: import library to render", "import '" + toRenderFile.getName() + "';");
      template = template.replace("new Container(); // TEMPLATE_VALUE: create widget", widgetCreation);
      template = template.replace("{}; // TEMPLATE_VALUE: use flutterDesignerWidgets", "flutterDesignerWidgets;");
      template = template.replace("350.0 /*TEMPLATE_VALUE: width*/", request.width + ".0");
      template = template.replace("400.0 /*TEMPLATE_VALUE: height*/", request.height + ".0");

      final File renderServerFile = new File(designerDirectory, "render_server.dart");
      final String renderServerPath = renderServerFile.getPath();
      FileUtil.writeToFile(renderServerFile, template);

      // If the process is dead, clear the instance.
      if (myApp != null && !myApp.isConnected()) {
        myApp = null;
      }

      // Check if the current render server process is compatible with the new request.
      // If it is, attempt to perform hot reload.
      // If not successful, terminate the process.
      boolean canRenderWithCurrentProcess = false;
      if (myProcessRequest != null && myApp != null) {
        if (Objects.equals(myProcessRequest.pubRoot.getPath(), packagePath)) {
          try {
            final DaemonApi.RestartResult restartResult = myApp.performHotReload(null, false).get(5000, TimeUnit.MILLISECONDS);
            if (restartResult.ok()) {
              canRenderWithCurrentProcess = true;
            }
          }
          catch (Throwable ignored) {
          }
        }

        if (!canRenderWithCurrentProcess) {
          terminateCurrentProcess("Project root or file changed, or reload failed");
        }
      }

      // If there is no rendering server process, start a new one.
      // Wait for it to start.
      if (myApp == null) {
        myProcessRequest = request;

        final FlutterCommand command = request.flutterSdk.flutterRunOnTester(request.pubRoot, renderServerPath);
        final FlutterApp app = FlutterApp.start(
          new ExecutionEnvironment(),
          request.project,
          request.module,
          RunMode.DEBUG,
          FlutterDevice.getTester(),
          command.createGeneralCommandLine(request.project),
          null,
          null);

        final CountDownLatch startedLatch = new CountDownLatch(1);
        app.addStateListener(new FlutterApp.FlutterAppListener() {
          @Override
          public void stateChanged(FlutterApp.State newState) {
            if (newState == FlutterApp.State.STARTED) {
              myApp = app;
              startedLatch.countDown();
            }
            if (newState == FlutterApp.State.TERMINATING) {
              myApp = null;
            }
          }
        });

        final boolean started = Uninterruptibles.awaitUninterruptibly(startedLatch, 10000, TimeUnit.MILLISECONDS);
        if (!started) {
          terminateCurrentProcess("Initial start timeout.");
          request.listener.onFailure(RenderProblemKind.TIMEOUT, request.widget);
          return;
        }

        myApp = app;
      }

      // Ask to render the widget.
      final CountDownLatch responseReceivedLatch = new CountDownLatch(1);
      final AtomicReference<JsonObject> responseRef = new AtomicReference<>();
      myApp.callServiceExtension("ext.flutter.designer.render").thenAccept((response) -> {
        responseRef.set(response);
        responseReceivedLatch.countDown();
      });

      // Wait for the response.
      final boolean responseReceived = Uninterruptibles.awaitUninterruptibly(responseReceivedLatch, 4000, TimeUnit.MILLISECONDS);
      if (!responseReceived) {
        terminateCurrentProcess("Render response timeout.");
        request.listener.onFailure(RenderProblemKind.TIMEOUT, widget);
        return;
      }
      final JsonObject response = responseRef.get();

      if (response.has("exception")) {
        request.listener.onRemoteException(widget, response);
        return;
      }

      // Send the respose to the client.
      request.listener.onResponse(widget, response);
    }
    catch (Throwable e) {
      terminateCurrentProcess("Exception");
      request.listener.onLocalException(widget, e);
    }
  }

  private void terminateCurrentProcess(String reason) {
    myProcessRequest = null;

    if (myApp != null) {
      myApp.getProcessHandler().destroyProcess();
      myApp = null;
    }
  }
}
