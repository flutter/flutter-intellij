package com.jetbrains.lang.dart.ide.runner.server.vmService;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.BitUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.base.DartDebuggerEditorsProvider;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceStackFrame;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceSuspendContext;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import gnu.trove.THashSet;
import io.flutter.FlutterBundle;
import io.flutter.run.FlutterLaunchMode;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.*;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.logging.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Frame;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TODO(messick) Add ObservatoryConnector parameter to superclass then delete this class.
 *
 * <p>Doesn't do remote debugging. (The Flutter plugin doesn't need it.)</p>
 */
public class DartVmServiceDebugProcessZ extends DartVmServiceDebugProcess {
  private static final Logger LOG = Logger.getInstance(DartVmServiceDebugProcessZ.class);

  private boolean myVmConnected = false;

  @NotNull private final XBreakpointHandler[] myBreakpointHandlers;
  private final IsolatesInfo myIsolatesInfo;
  private VmServiceWrapper myVmServiceWrapper;
  private VmOpenSourceLocationListener myVmOpenSourceLocationListener;

  @NotNull private final Set<String> mySuspendedIsolateIds = Collections.synchronizedSet(new THashSet<>());
  private String myLatestCurrentIsolateId;

  @NotNull private final ObservatoryConnector myConnector;

  private boolean remoteDebug = false;

  @NotNull
  private final ExecutionEnvironment executionEnvironment;

  @NotNull
  private final PositionMapper mapper;

  public DartVmServiceDebugProcessZ(@NotNull final ExecutionEnvironment executionEnvironment,
                                    @NotNull final XDebugSession session,
                                    @NotNull final ExecutionResult executionResult,
                                    @NotNull final DartUrlResolver dartUrlResolver,
                                    @NotNull final ObservatoryConnector connector,
                                    @NotNull final PositionMapper mapper) {
    super(session, "localhost", 0, executionResult, dartUrlResolver, "fakeExecutionIdNotUsed",
          false, 0, null);

    this.executionEnvironment = executionEnvironment;
    this.mapper = mapper;
    myConnector = connector;

    myIsolatesInfo = new IsolatesInfo();
    final DartVmServiceBreakpointHandler breakpointHandler = new DartVmServiceBreakpointHandler(this);
    myBreakpointHandlers = new XBreakpointHandler[]{breakpointHandler};

    setLogger();

    final Runnable resumeCallback = () -> {
      if (session.isPaused()) {
        session.resume();
      }
    };

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        stackFrameChanged();
        connector.onDebuggerPaused(resumeCallback);
      }

      @Override
      public void sessionResumed() {
        connector.onDebuggerResumed();
      }

      @Override
      public void stackFrameChanged() {
        final XStackFrame stackFrame = getSession().getCurrentStackFrame();
        myLatestCurrentIsolateId =
          stackFrame instanceof DartVmServiceStackFrame ? ((DartVmServiceStackFrame)stackFrame).getIsolateId() : null;
      }
    });

    scheduleConnectNew();
  }

  @Override
  public boolean isRemoteDebug() {
    // The flutter plugin doesn't do remote debugging, but we set this value to
    // whatever is needed to fake out the superclass.
    return remoteDebug;
  }

  public VmServiceWrapper getVmServiceWrapper() {
    return myVmServiceWrapper;
  }

  public Collection<IsolatesInfo.IsolateInfo> getIsolateInfos() {
    return myIsolatesInfo.getIsolateInfos();
  }

  private void setLogger() {
    Logging.setLogger(new org.dartlang.vm.service.logging.Logger() {
      @Override
      public void logError(final String message) {
        if (message.contains("\"code\":102,")) { // Cannot add breakpoint, already logged in logInformation()
          return;
        }

        if (message.contains("\"method\":\"removeBreakpoint\"")) { // That's expected because we set one breakpoint twice
          return;
        }

        if (message.contains("\"type\":\"Sentinel\"")) { // Ignore unwanted message
          return;
        }

        getSession().getConsoleView().print(message.trim() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        LOG.warn(message);
      }

      @Override
      public void logError(final String message, final Throwable exception) {
        if (!getVmConnected() || getSession() == null) {
          return;
        }
        if (message != null) {
          getSession().getConsoleView().print(message.trim() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
        LOG.warn(message, exception);
      }

      @Override
      public void logInformation(String message) {
        if (message.length() > 500) {
          message = message.substring(0, 300) + "..." + message.substring(message.length() - 200);
        }
        LOG.debug(message);
      }

      @Override
      public void logInformation(final String message, final Throwable exception) {
        LOG.debug(message, exception);
      }
    });
  }

  /**
   * We override the parent with a no-op implementation; our preferred implementation
   * (scheduleConnectNew) is called elsewhere.
   */
  @Override
  protected void scheduleConnect(@NotNull final String url) {
    // This page intentionally left blank.

  }

  public void scheduleConnectNew() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      // Poll, waiting for "flutter run" to give us a websocket.
      // Don't use a timeout - the user can cancel manually the operation.
      String url = myConnector.getWebSocketUrl();

      while (url == null) {
        if (getSession().isStopped()) return;

        TimeoutUtil.sleep(100);

        url = myConnector.getWebSocketUrl();
      }

      if (getSession().isStopped()) {
        return;
      }

      // "Flutter run" has given us a websocket; we can assume it's ready immediately,
      // because "flutter run" has already connected to it.
      final VmService vmService;
      final VmOpenSourceLocationListener vmOpenSourceLocationListener;
      try {
        vmService = VmService.connect(url);
        vmOpenSourceLocationListener = VmOpenSourceLocationListener.connect(url);
      }
      catch (IOException | RuntimeException e) {
        onConnectFailed("Failed to connect to the VM observatory service at: " + url + "\n"
                        + e.toString() + "\n" +
                        formatStackTraces(e));
        return;
      }
      onConnectSucceeded(vmService, vmOpenSourceLocationListener);
    });
  }

  @NotNull
  private String formatStackTraces(Throwable e) {
    final StringBuilder out = new StringBuilder();
    Throwable cause = e.getCause();
    while (cause != null) {
      out.append("Caused by: ").append(cause.toString()).append("\n");
      cause = cause.getCause();
    }
    return out.toString();
  }

  private void onConnectFailed(@NotNull String message) {
    if (!message.endsWith("\n")) {
      message = message + "\n";
    }
    getSession().getConsoleView().print(message, ConsoleViewContentType.ERROR_OUTPUT);
    getSession().stop();
  }

  private void onConnectSucceeded(VmService vmService,
                                  VmOpenSourceLocationListener vmOpenSourceLocationListener) {
    final DartVmServiceListener vmServiceListener =
      new DartVmServiceListener(this, (DartVmServiceBreakpointHandler)myBreakpointHandlers[0]);
    final DartVmServiceBreakpointHandler breakpointHandler = (DartVmServiceBreakpointHandler)myBreakpointHandlers[0];

    myVmOpenSourceLocationListener = vmOpenSourceLocationListener;
    myVmServiceWrapper = new VmServiceWrapper(this, vmService, vmServiceListener, myIsolatesInfo, breakpointHandler);

    final ScriptProvider provider =
      (isolateId, scriptId) -> myVmServiceWrapper.getScriptSync(isolateId, scriptId);

    mapper.onConnect(provider, myConnector.getRemoteBaseUrl());

    // We disable the remote debug flag so that handleDebuggerConnected() does not echo the stdout and
    // stderr streams (this would duplicate what we get over daemon logging).
    remoteDebug = false;

    final FlutterLaunchMode launchMode = FlutterLaunchMode.fromEnv(executionEnvironment);
    if (launchMode.supportsDebugConnection()) {
      myVmServiceWrapper.handleDebuggerConnected();

      // TODO(jacobr): the following code is a workaround for issues
      // auto-resuming isolates paused at their creation while running in
      // debug mode.
      // The ideal fix would by to fix VMServiceWrapper so that it checks
      // for already running isolates like we do here or to refactor where we
      // create our VmServiceWrapper so we can listen for isolate creation soon
      // enough that we never miss an isolate creation message.
      vmService.getVM(new VMConsumer() {
        @Override
        public void received(VM vm) {
          final ElementList<IsolateRef> isolates = vm.getIsolates();
          // There is a risk the isolate we care about loaded before the call
          // to handleDebuggerConnected was made and so
          for (IsolateRef isolateRef : isolates) {
            vmService.getIsolate(isolateRef.getId(), new VmServiceConsumers.GetIsolateConsumerWrapper() {
              public void received(Isolate isolate) {
                final Event event = isolate.getPauseEvent();
                final EventKind eventKind = event.getKind();
                if (eventKind == EventKind.PauseStart) {
                  ApplicationManager.getApplication().invokeLater(() -> {
                    // We are assuming it is safe to call handleIsolate multiple times.
                    myVmServiceWrapper.handleIsolate(isolateRef, true);
                  });
                }
              }
            });
          }
        }

        @Override
        public void onError(RPCError error) {
          LOG.error(error.toString());
        }
      });
    }

    // We re-enable the remote debug flag so that the service wrapper will call our guessRemoteProjectRoot()
    // method with the list of loaded libraries for the isolate.
    remoteDebug = true;

    vmService.addVmServiceListener(vmServiceListener);
    myVmOpenSourceLocationListener.addListener(
      this::onOpenSourceLocationRequest);

    myVmConnected = true;
    getSession().rebuildViews();
    onVmConnected(vmService);
  }

  private void onOpenSourceLocationRequest(@NotNull String isolateId, @NotNull String scriptId, int tokenPos) {
    myVmServiceWrapper.getObject(isolateId, scriptId, new GetObjectConsumer() {
      @Override
      public void received(Obj response) {
        if (response instanceof Script) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final XSourcePosition source =
              getSourcePosition(isolateId, (Script)response, tokenPos);
            if (source != null) {
              final Project project = getSession().getProject();
              final OpenFileHyperlinkInfo
                info = new OpenFileHyperlinkInfo(project, source.getFile(), source.getLine());
              ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
                info.navigate(project);

                if (SystemInfo.isLinux) {
                  // TODO(cbernaschina): remove when ProjectUtil.focusProjectWindow(project, true); works as expected.
                  focusProject(project);
                }
                else {
                  ProjectUtil.focusProjectWindow(project, true);
                }
              }));
            }
          });
        }
      }

      @Override
      public void received(Sentinel response) {
        // ignore
      }

      @Override
      public void onError(RPCError error) {
        // ignore
      }
    });
  }

  private static void focusProject(@NotNull Project project) {
    final JFrame projectFrame = WindowManager.getInstance().getFrame(project);
    final int frameState = projectFrame.getExtendedState();

    if (BitUtil.isSet(frameState, Frame.ICONIFIED)) {
      // restore the frame if it is minimized
      projectFrame.setExtendedState(frameState ^ Frame.ICONIFIED);
      projectFrame.toFront();
    }
    else {
      final JFrame anchor = new JFrame();
      anchor.setType(Window.Type.UTILITY);
      anchor.setUndecorated(true);
      anchor.setSize(0, 0);
      anchor.addWindowListener(new WindowListener() {
        @Override
        public void windowOpened(WindowEvent e) {
        }

        @Override
        public void windowClosing(WindowEvent e) {
        }

        @Override
        public void windowClosed(WindowEvent e) {
        }

        @Override
        public void windowIconified(WindowEvent e) {
        }

        @Override
        public void windowDeiconified(WindowEvent e) {
        }

        @Override
        public void windowActivated(WindowEvent e) {
          projectFrame.setVisible(true);
          anchor.dispose();
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
        }
      });
      anchor.pack();
      anchor.setVisible(true);
      anchor.toFront();
    }
  }

  /**
   * Callback for subclass.
   */
  protected void onVmConnected(@NotNull VmService vmService) {
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return new DartDebuggerEditorsProvider();
  }

  @Override
  @NotNull
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  public void guessRemoteProjectRoot(@NotNull final ElementList<LibraryRef> libraries) {
    // After connecting (with remote debugging enabled), this is called once per isolate.
    // TODO(skybrian) do we need to handle multiple isolates?
    mapper.onLibrariesDownloaded(libraries);
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    if (myLatestCurrentIsolateId != null && mySuspendedIsolateIds.contains(myLatestCurrentIsolateId)) {
      final DartVmServiceSuspendContext suspendContext = (DartVmServiceSuspendContext)context;
      final StepOption stepOption = suspendContext != null && suspendContext.getAtAsyncSuspension() ? StepOption.OverAsyncSuspension
                                                                                                    : StepOption.Over;
      myVmServiceWrapper.resumeIsolate(myLatestCurrentIsolateId, stepOption);
    }
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    if (myLatestCurrentIsolateId != null && mySuspendedIsolateIds.contains(myLatestCurrentIsolateId)) {
      myVmServiceWrapper.resumeIsolate(myLatestCurrentIsolateId, StepOption.Into);
    }
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    if (myLatestCurrentIsolateId != null && mySuspendedIsolateIds.contains(myLatestCurrentIsolateId)) {
      myVmServiceWrapper.resumeIsolate(myLatestCurrentIsolateId, StepOption.Out);
    }
  }

  @Override
  public void dropFrame(DartVmServiceStackFrame frame) {
    myVmServiceWrapper.dropFrame(frame.getIsolateId(), frame.getFrameIndex() + 1);
  }

  @Override
  public void stop() {
    myVmConnected = false;
    mapper.shutdown();

    if (myVmServiceWrapper != null) {
      Disposer.dispose(myVmServiceWrapper);
    }
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    for (String isolateId : new ArrayList<>(mySuspendedIsolateIds)) {
      myVmServiceWrapper.resumeIsolate(isolateId, null);
    }
  }

  @Override
  public void startPausing() {
    for (IsolatesInfo.IsolateInfo info : getIsolateInfos()) {
      if (!mySuspendedIsolateIds.contains(info.getIsolateId())) {
        myVmServiceWrapper.pauseIsolate(info.getIsolateId());
      }
    }
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
    if (myLatestCurrentIsolateId != null && mySuspendedIsolateIds.contains(myLatestCurrentIsolateId)) {
      // Set a temporary breakpoint and resume.
      myVmServiceWrapper.addTemporaryBreakpoint(position, myLatestCurrentIsolateId);
      myVmServiceWrapper.resumeIsolate(myLatestCurrentIsolateId, null);
    }
  }

  public void isolateSuspended(@NotNull final IsolateRef isolateRef) {
    mySuspendedIsolateIds.add(isolateRef.getId());
  }

  public boolean isIsolateSuspended(@NotNull final String isolateId) {
    return mySuspendedIsolateIds.contains(isolateId);
  }

  public boolean isIsolateAlive(@NotNull final String isolateId) {
    for (IsolatesInfo.IsolateInfo isolateInfo : myIsolatesInfo.getIsolateInfos()) {
      if (isolateId.equals(isolateInfo.getIsolateId())) {
        return true;
      }
    }
    return false;
  }

  public void isolateResumed(@NotNull final IsolateRef isolateRef) {
    mySuspendedIsolateIds.remove(isolateRef.getId());
  }

  public void isolateExit(@NotNull final IsolateRef isolateRef) {
    myIsolatesInfo.deleteIsolate(isolateRef);
    mySuspendedIsolateIds.remove(isolateRef.getId());

    if (isolateRef.getId().equals(myLatestCurrentIsolateId)) {
      resume(getSession().getSuspendContext()); // otherwise no way no resume them from UI
    }
  }

  public void handleWriteEvent(String base64Data) {
    final String message = new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
    getSession().getConsoleView().print(message, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Override
  public String getCurrentStateMessage() {
    return getSession().isStopped()
           ? XDebuggerBundle.message("debugger.state.message.disconnected")
           : myVmConnected
             ? XDebuggerBundle.message("debugger.state.message.connected")
             : FlutterBundle.message("waiting.for.flutter");
  }

  @NotNull
  public Collection<String> getUrisForFile(@NotNull final VirtualFile file) {
    return mapper.getBreakpointUris(file);
  }

  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final ScriptRef scriptRef, int tokenPos) {
    return mapper.getSourcePosition(isolateId, scriptRef, tokenPos);
  }

  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final Script script, int tokenPos) {
    return mapper.getSourcePosition(isolateId, script, tokenPos);
  }

  public boolean getVmConnected() {
    return myVmConnected;
  }

  // TODO(skybrian) when we merge this back to the Dart plugin,
  // I expect we will copy PositionMapper's implementation and ObservatoryFile as well.
  // But we should keep these interfaces to allow them to be overridden by the Flutter plugin if needed.

  /**
   * Converts positions between Dart files in Observatory and local Dart files.
   * <p>
   * Used when setting breakpoints and stepping through code while debugging.
   */
  public interface PositionMapper {
    void onConnect(ScriptProvider provider, String remoteBaseUrl);

    // TODO(skybrian) this is called once per isolate. Should we pass in the isolate id?

    /**
     * Just after connecting, the debugger downloads the list of Dart libraries from Observatory and reports it here.
     */
    void onLibrariesDownloaded(Iterable<LibraryRef> libraries);

    /**
     * Returns all possible Observatory URI's corresponding to a local file.
     * <p>
     * (A breakpoint will be set in all of them that exist.)
     */
    Collection<String> getBreakpointUris(VirtualFile file);

    /**
     * Returns the local position (to display to the user) corresponding to a token position in Observatory.
     */
    XSourcePosition getSourcePosition(String isolateId, ScriptRef scriptRef, int tokenPos);

    /**
     * Returns the local position (to display to the user) corresponding to a token position in Observatory.
     */
    XSourcePosition getSourcePosition(String isolateId, Script script, int tokenPos);

    void shutdown();
  }

  public interface ScriptProvider {
    /**
     * Downloads a script from observatory. Blocks until it's available.
     */
    @Nullable
    Script downloadScript(@NotNull String isolateId, @NotNull String scriptId);
  }
}
