/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.ReloadReportConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenderHelper {
  private final Project myProject;
  private final Listener myListener;

  private String myTesterPath = null;
  private final RenderThread myRenderThread = new RenderThread();

  private VirtualFile myPackagesFile;
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

    myPackagesFile = null;
    final PubRoot pubRoot = PubRoot.forFile(file);
    if (pubRoot != null) {
      myPackagesFile = pubRoot.getPackagesFile();
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
    if (myPackagesFile == null || myInstrumentedCode == null || myWidgetOutline == null || myWidth == 0 || myHeight == 0) {
      return;
    }

    // TODO: Expose this as an error to the user.
    if (myTesterPath == null) {
      return;
    }

    final String widgetClass = myWidgetOutline.getDartElement().getName();
    final String constructor = myWidgetOutline.getRenderConstructor();
    final RenderRequest request =
      new RenderRequest(myTesterPath, myPackagesFile, myFile, myInstrumentedCode,
                        myWidgetOutline, widgetClass, constructor,
                        myWidth, myHeight, myListener);
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

  final String testerPath;

  final VirtualFile packages;
  final VirtualFile file;
  final String codeToRender;
  final FlutterOutline widget;
  final String widgetClass;
  final String widgetConstructor;
  final int width;
  final int height;

  final RenderHelper.Listener listener;

  RenderRequest(String testerPath, VirtualFile packages, VirtualFile file,
                String codeToRender,
                FlutterOutline widget,
                String widgetClass,
                String widgetConstructor,
                int width,
                int height,
                RenderHelper.Listener listener) {
    this.testerPath = testerPath;
    this.packages = packages;
    this.file = file;
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
  File myTemporaryDirectory;

  final Object myRequestLock = new Object();
  RenderRequest myRequest;

  RenderRequest myProcessRequest;
  Process myProcess;
  BufferedReader myProcessReader;
  PrintStream myProcessWriter;
  VmService myVmService;

  RenderThread() {
    setDaemon(true);
    try {
      myTemporaryDirectory = FileUtil.createTempDirectory("flutterDesigner", null);
    }
    catch (IOException ignored) {
    }
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

    if (myTemporaryDirectory == null) {
      request.listener.onFailure(RenderProblemKind.NO_TEMPORARY_DIRECTORY, widget);
      return;
    }

    try {
      final File toRenderFile = new File(myTemporaryDirectory, "to_render.dart");
      Files.write(request.codeToRender, toRenderFile, StandardCharsets.UTF_8);

      final String widgetCreation = "new " + request.widgetClass + "." + request.widgetConstructor + "();";

      final URL templateUri = RenderHelper.class.getResource("render_server_template.txt");
      String template = Resources.toString(templateUri, StandardCharsets.UTF_8);
      template = template.replace("// TEMPLATE_VALUE: import library to render", "import '" + toRenderFile.toURI() + "';");
      template = template.replace("new Container(); // TEMPLATE_VALUE: create widget", widgetCreation);
      template = template.replace("{}; // TEMPLATE_VALUE: use flutterDesignerWidgets", "flutterDesignerWidgets;");
      template = template.replace("350.0 /*TEMPLATE_VALUE: width*/", request.width + ".0");
      template = template.replace("400.0 /*TEMPLATE_VALUE: height*/", request.height + ".0");

      final File renderServerFile = new File(myTemporaryDirectory, "render_server.dart");
      final String renderServerPath = renderServerFile.getPath();
      Files.write(template, renderServerFile, StandardCharsets.UTF_8);

      // If the process is dead, clear the instance.
      if (myProcess != null && !myProcess.isAlive()) {
        myProcess = null;
      }

      // Check if the current render server process is compatible with the new request.
      // If it is, attempt to perform hot reload.
      // If not successful, terminate the process.
      boolean canRenderWithCurrentProcess = false;
      if (myProcessRequest != null && myProcess != null && myProcess.isAlive() && myVmService != null) {
        if (Objects.equals(myProcessRequest.packages, request.packages)) {
          final boolean success = performReload();
          if (success) {
            canRenderWithCurrentProcess = true;
          }
        }

        if (!canRenderWithCurrentProcess) {
          terminateCurrentProcess("Project root or file changed, or reload failed");
        }
      }

      // If the is no rendering server process, start a new one.
      boolean newProcess = false;
      if (myProcess == null) {
        myProcessRequest = request;
        final ProcessBuilder processBuilder =
          new ProcessBuilder(request.testerPath,
                             "--enable-checked-mode",
                             "--non-interactive",
                             "--use-test-fonts",
                             "--packages=" + request.packages.getPath(),
                             renderServerPath);
        processBuilder.environment().put("FLUTTER_TEST", "true");
        myProcess = processBuilder.start();
        myProcessReader = new BufferedReader(new InputStreamReader(myProcess.getInputStream()));
        myProcessWriter = new PrintStream(myProcess.getOutputStream(), true);
        newProcess = true;
      }

      // Terminate the process if it does not respond fast enough.
      final CountDownLatch responseReceivedLatch = new CountDownLatch(1);
      final Process processToTerminate = myProcess;
      new Thread(() -> {
        boolean success = Uninterruptibles.awaitUninterruptibly(responseReceivedLatch, 2000, TimeUnit.MILLISECONDS);
        if (!success) {
          processToTerminate.destroyForcibly();
        }
      }).start();

      // Get the Observatory URI and connect VmService.
      if (newProcess) {
        final String line = myProcessReader.readLine();
        if (line == null || !line.startsWith("Observatory listening on ")) {
          terminateCurrentProcess("Observatory URI expected");
          return;
        }
        String uri = line.substring("Observatory listening on ".length());
        uri = uri.replace("http://", "ws://") + "ws";
        myVmService = VmService.connect(uri);
      }

      // Ask to render the widget.
      myProcessWriter.println("render");

      // Read the response.
      final JsonObject response;
      {
        final String line = myProcessReader.readLine();
        if (line == null) {
          terminateCurrentProcess("Response expected");
          request.listener.onFailure(RenderProblemKind.TIMEOUT, widget);
          return;
        }
        // The line must be a JSON response.
        try {
          response = new Gson().fromJson(line, JsonObject.class);
        }
        catch (Throwable e) {
          terminateCurrentProcess("JSON response expected");
          request.listener.onFailure(RenderProblemKind.INVALID_JSON, widget);
          return;
        }
      }

      // Fail if unable to find the valid response.
      if (response == null) {
        request.listener.onFailure(RenderProblemKind.INVALID_JSON, widget);
        return;
      }

      // OK, we got all what we need, the process is OK, we can continue using it.
      responseReceivedLatch.countDown();

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

  /**
   * Attempt to perform hot reload.
   *
   * @return true if successful, or false if unsuccessful or timeout.
   */
  private boolean performReload() {
    final CountDownLatch reloadLatch = new CountDownLatch(1);
    final AtomicBoolean reloadSuccess = new AtomicBoolean(false);

    myVmService.getVM(new VMConsumer() {
      @Override
      public void received(VM vm) {
        final ElementList<IsolateRef> isolates = vm.getIsolates();
        if (!isolates.isEmpty()) {
          final String isolateId = isolates.get(0).getId();

          myVmService.reloadSources(isolateId, new ReloadReportConsumer() {
            @Override
            public void received(ReloadReport report) {
              reloadSuccess.set(report.getSuccess());
              reloadLatch.countDown();
            }

            @Override
            public void onError(RPCError error) {
              reloadLatch.countDown();
            }
          });
        }
      }

      @Override
      public void onError(RPCError error) {
        reloadLatch.countDown();
      }
    });

    return Uninterruptibles.awaitUninterruptibly(reloadLatch, 2000, TimeUnit.MILLISECONDS) && reloadSuccess.get();
  }

  private void terminateCurrentProcess(String reason) {
    myProcessRequest = null;

    if (myProcess != null) {
      myProcess.destroyForcibly();
      myProcess = null;
    }
    myProcessReader = null;
    myProcessWriter = null;

    if (myVmService != null) {
      myVmService.disconnect();
      myVmService = null;
    }
  }
}
