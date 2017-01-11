package com.jetbrains.lang.dart.ide.runner.server.vmService;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.ide.runner.base.DartDebuggerEditorsProvider;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceStackFrame;
import com.jetbrains.lang.dart.ide.runner.server.vmService.frame.DartVmServiceSuspendContext;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import io.flutter.FlutterBundle;
import io.flutter.actions.HotReloadFlutterApp;
import io.flutter.actions.OpenObservatoryAction;
import io.flutter.actions.RestartFlutterApp;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.*;
import org.dartlang.vm.service.logging.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TODO(messick) Add ObservatoryConnector parameter to superclass then delete this class.
 */
public class DartVmServiceDebugProcessZ extends DartVmServiceDebugProcess {
  private static final Logger LOG = Logger.getInstance(DartVmServiceDebugProcess.class.getName());

  @Nullable private final ExecutionResult myExecutionResult;
  @NotNull private final DartUrlResolver myDartUrlResolver;
  private String myObservatoryWsUrl;

  private boolean myVmConnected = false;

  @NotNull private final XBreakpointHandler[] myBreakpointHandlers;
  private final IsolatesInfo myIsolatesInfo;
  private VmServiceWrapper myVmServiceWrapper;

  @NotNull private final Set<String> mySuspendedIsolateIds = Collections.synchronizedSet(new THashSet<String>());
  private String myLatestCurrentIsolateId;

  private final Map<String, LightVirtualFile> myScriptIdToContentMap = new THashMap<>();
  private final Map<String, TIntObjectHashMap<Pair<Integer, Integer>>> myScriptIdToLinesAndColumnsMap = new THashMap<>();

  @Nullable private final String myDASExecutionContextId;
  private final int myTimeout;
  @Nullable private final VirtualFile myCurrentWorkingDirectory;
  @Nullable private final ObservatoryConnector myConnector;
  private boolean baseUriWasInited = false;

  public DartVmServiceDebugProcessZ(@NotNull final XDebugSession session,
                                    @Nullable final ExecutionResult executionResult,
                                    @NotNull final DartUrlResolver dartUrlResolver,
                                    @Nullable final String dasExecutionContextId,
                                    final boolean remoteDebug,
                                    final int timeout,
                                    @Nullable final VirtualFile currentWorkingDirectory,
                                    @Nullable final ObservatoryConnector connector) {
    super(session, "localhost", 0, executionResult, dartUrlResolver, dasExecutionContextId,
          remoteDebug, timeout, currentWorkingDirectory);

    myExecutionResult = executionResult;
    myDartUrlResolver = dartUrlResolver;
    myTimeout = timeout;
    myCurrentWorkingDirectory = currentWorkingDirectory;
    myConnector = connector;

    myIsolatesInfo = new IsolatesInfo();
    final DartVmServiceBreakpointHandler breakpointHandler = new DartVmServiceBreakpointHandlerZ(this);
    myBreakpointHandlers = new XBreakpointHandler[]{breakpointHandler};

    setLogger();

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        stackFrameChanged();
        if (connector != null) {
          connector.sessionPaused(session);
        }
      }

      @Override
      public void sessionResumed() {
        if (connector != null) {
          connector.sessionResumed();
        }
      }

      @Override
      public void stackFrameChanged() {
        final XStackFrame stackFrame = getSession().getCurrentStackFrame();
        myLatestCurrentIsolateId =
          stackFrame instanceof DartVmServiceStackFrame ? ((DartVmServiceStackFrame)stackFrame).getIsolateId() : null;
      }
    });

    myDASExecutionContextId = dasExecutionContextId;

    scheduleConnectNew();

    if (remoteDebug) {
      LOG.assertTrue(myExecutionResult == null && myDASExecutionContextId == null, myDASExecutionContextId + myExecutionResult);
    }
    else {
      LOG.assertTrue(myExecutionResult != null && myDASExecutionContextId != null, myDASExecutionContextId + myExecutionResult);
    }

    // We disable the service protocol library logger because of a user facing NPE in a
    // DartVmServiceListener from the Dart plugin.
    Logging.setLogger(org.dartlang.vm.service.logging.Logger.NULL);
  }

  @Nullable
  public ObservatoryConnector getConnector() {
    return myConnector;
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
        getSession().getConsoleView().print(message.trim() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
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
  public void scheduleConnect() {
    // This page intentionally left blank.

  }

  public void scheduleConnectNew() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (myConnector != null) {
        final long timeout = (long)myTimeout;
        final long startTime = System.currentTimeMillis();
        while (!getSession().isStopped() && !myConnector.isConnectionReady()) {
          if (System.currentTimeMillis() > startTime + timeout) {
            final String message = "Observatory connection never became ready.\n";
            getSession().getConsoleView().print(message, ConsoleViewContentType.ERROR_OUTPUT);
            getSession().stop();
            return;
          }
          else {
            TimeoutUtil.sleep(50);
          }
        }

        if (getSession().isStopped()) {
          return;
        }

        myObservatoryWsUrl = myConnector.getObservatoryWsUrl();
      }

      final long timeout = (long)myTimeout;
      final long startTime = System.currentTimeMillis();

      try {
        while (true) {
          try {
            connect();
            break;
          }
          catch (IOException e) {
            if (System.currentTimeMillis() > startTime + timeout) {
              throw e;
            }
            else {
              TimeoutUtil.sleep(50);
            }
          }
        }
      }
      catch (IOException e) {
        String message = "Failed to connect to the VM observatory service: " + e.toString() + "\n";
        Throwable cause = e.getCause();
        while (cause != null) {
          message += "Caused by: " + cause.toString() + "\n";
          final Throwable cause1 = cause.getCause();
          if (cause1 != cause) {
            cause = cause1;
          }
        }

        getSession().getConsoleView().print(message, ConsoleViewContentType.ERROR_OUTPUT);
        getSession().stop();
      }
    });
  }

  private void connect() throws IOException {
    final VmService vmService = VmService.connect(myObservatoryWsUrl);
    final DartVmServiceListener vmServiceListener =
      new DartVmServiceListener(this, (DartVmServiceBreakpointHandler)myBreakpointHandlers[0]);
    final DartVmServiceBreakpointHandler breakpointHandler = (DartVmServiceBreakpointHandler)myBreakpointHandlers[0];

    myVmServiceWrapper = new VmServiceWrapper(this, vmService, vmServiceListener, myIsolatesInfo, breakpointHandler);
    myVmServiceWrapper.handleDebuggerConnected();

    vmService.addVmServiceListener(vmServiceListener);

    myVmConnected = true;
    getSession().rebuildViews();
  }

  @Override
  protected ProcessHandler doGetProcessHandler() {
    return myExecutionResult == null ? super.doGetProcessHandler() : myExecutionResult.getProcessHandler();
  }

  @NotNull
  @Override
  public ExecutionConsole createConsole() {
    return myExecutionResult == null ? super.createConsole() : myExecutionResult.getExecutionConsole();
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
    final VirtualFile pubspec = myDartUrlResolver.getPubspecYamlFile();
    final VirtualFile projectRoot = pubspec != null ? pubspec.getParent() : myCurrentWorkingDirectory;

    if (projectRoot == null) return;

    for (LibraryRef library : libraries) {
      final String remoteUri = library.getUri();
      if (remoteUri.startsWith(DartUrlResolver.DART_PREFIX)) continue;
      if (remoteUri.startsWith(DartUrlResolver.PACKAGE_PREFIX)) continue;

      final PsiFile[] localFilesWithSameName = ApplicationManager.getApplication().runReadAction((Computable<PsiFile[]>)() -> {
        final String remoteFileName = PathUtil.getFileName(remoteUri);
        final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(getSession().getProject(), projectRoot, true);
        return FilenameIndex.getFilesByName(getSession().getProject(), remoteFileName, scope);
      });

      int howManyFilesMatch = 0;

      for (PsiFile psiFile : localFilesWithSameName) {
        final VirtualFile file = DartResolveUtil.getRealVirtualFile(psiFile);
        if (file == null) continue;

        LOG.assertTrue(file.getPath().startsWith(projectRoot.getPath() + "/"), file.getPath() + "," + projectRoot.getPath());
        final String relPath = file.getPath().substring(projectRoot.getPath().length()); // starts with slash
        if (remoteUri.endsWith(relPath)) {
          howManyFilesMatch++;
          setRemoteProjectRootUri(remoteUri.substring(0, remoteUri.length() - relPath.length()));
        }
      }

      if (howManyFilesMatch == 1) {
        break; // we did the best guess we could
      }
    }
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
  public void stop() {
    myVmConnected = false;

    if (myVmServiceWrapper != null) {
      if (myDASExecutionContextId != null) {
        DartAnalysisServerService.getInstance().execution_deleteContext(myDASExecutionContextId);
      }

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

  @Override
  public void registerAdditionalActions(@NotNull final DefaultActionGroup leftToolbar,
                                        @NotNull final DefaultActionGroup topToolbar,
                                        @NotNull final DefaultActionGroup settings) {
    // For Run tool window this action is added in DartCommandLineRunningState.createActions()
    topToolbar.addSeparator();
    topToolbar.addAction(new OpenObservatoryAction(this::computeObservatoryBrowserUrl, this::isSessionActive));
    topToolbar.addSeparator();
    topToolbar.addAction(new HotReloadFlutterApp(myConnector, () -> shouldEnableHotReload() && isSessionActive()));
    topToolbar.addAction(new RestartFlutterApp(myConnector, () -> shouldEnableHotReload() && isSessionActive()));
  }

  // Overridden by subclasses.
  public boolean shouldEnableHotReload() {
    return false;
  }

  private boolean isSessionActive() {
    return (myConnector != null && myConnector.isConnectionReady()) &&
           myVmConnected && !getSession().isStopped();
  }

  private String computeObservatoryBrowserUrl() {
    assert myConnector != null;
    myObservatoryWsUrl = myConnector.getObservatoryWsUrl();
    assert myObservatoryWsUrl != null;
    return OpenObservatoryAction.convertWsToHttp(myObservatoryWsUrl);
  }

  @NotNull
  public Collection<String> getUrisForFile(@NotNull final VirtualFile file) {
    final Set<String> result = new HashSet<>();
    final String uriByIde = myDartUrlResolver.getDartUrlForFile(file);

    // If dart:, short circuit the results.
    if (uriByIde.startsWith(DartUrlResolver.DART_PREFIX)) {
      result.add(uriByIde);
      return result;
    }

    // file:
    if (uriByIde.startsWith(DartUrlResolver.FILE_PREFIX)) {
      result.add(threeSlashize(uriByIde));
    }
    else {
      result.add(uriByIde);
      result.add(threeSlashize(new File(file.getPath()).toURI().toString()));
    }

    // straight path - used by some VM embedders
    result.add(file.getPath());

    // package: (if applicable)
    if (myDASExecutionContextId != null) {
      final String uriByServer = DartAnalysisServerService.getInstance().execution_mapUri(myDASExecutionContextId, file.getPath(), null);
      if (uriByServer != null) {
        result.add(uriByServer);
      }
    }

    // remote prefix (if applicable)
    if (getRemoteProjectRootUri() != null) {
      final VirtualFile pubspec = myDartUrlResolver.getPubspecYamlFile();
      if (pubspec != null) {
        final String projectPath = pubspec.getParent().getPath();
        final String filePath = file.getPath();
        if (filePath.startsWith(projectPath)) {
          result.add(getRemoteProjectRootUri() + filePath.substring(projectPath.length()));
        }
      }
      else if (myCurrentWorkingDirectory != null) {
        // Handle projects with no pubspecs.
        final String projectPath = myCurrentWorkingDirectory.getPath();
        final String filePath = file.getPath();
        if (filePath.startsWith(projectPath)) {
          result.add(getRemoteProjectRootUri() + filePath.substring(projectPath.length()));
        }
      }
    }

    return result;
  }

  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final ScriptRef scriptRef, int tokenPos) {
    VirtualFile file = ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
      String uri = scriptRef.getUri();

      if (myDASExecutionContextId != null && !isDartPatchUri(uri)) {
        if (getRemoteProjectRootUri() == null || !uri.contains(getRemoteProjectRootUri())) {
          if (uri.startsWith("/")) {
            // Convert a file path to a file: uri.
            uri = new File(uri).toURI().toString();
          }
          final String path = DartAnalysisServerService.getInstance().execution_mapUri(myDASExecutionContextId, null, uri);
          if (path != null) {
            return LocalFileSystem.getInstance().findFileByPath(path);
          }
        }
      }

      final VirtualFile pubspec = myDartUrlResolver.getPubspecYamlFile();
      final VirtualFile parent = pubspec != null ? pubspec.getParent() : myCurrentWorkingDirectory;

      if (getRemoteProjectRootUri() != null && uri.startsWith(getRemoteProjectRootUri()) && parent != null) {
        final String localRootUri = StringUtil.trimEnd(myDartUrlResolver.getDartUrlForFile(parent), '/');
        uri = localRootUri + uri.substring(getRemoteProjectRootUri().length());
      }

      return myDartUrlResolver.findFileByDartUrl(uri);
    });

    if (file == null) {
      file = myScriptIdToContentMap.get(scriptRef.getId());
    }

    TIntObjectHashMap<Pair<Integer, Integer>> tokenPosToLineAndColumn = myScriptIdToLinesAndColumnsMap.get(scriptRef.getId());

    if (file != null && tokenPosToLineAndColumn != null) {
      final Pair<Integer, Integer> lineAndColumn = tokenPosToLineAndColumn.get(tokenPos);
      if (lineAndColumn == null) return XDebuggerUtil.getInstance().createPositionByOffset(file, 0);
      return XDebuggerUtil.getInstance().createPosition(file, lineAndColumn.first, lineAndColumn.second);
    }

    final Script script = myVmServiceWrapper.getScriptSync(isolateId, scriptRef.getId());
    if (script == null) return null;

    if (file == null) {
      file = new LightVirtualFile(PathUtil.getFileName(script.getUri()), DartFileType.INSTANCE, script.getSource());
      ((LightVirtualFile)file).setWritable(false);
      myScriptIdToContentMap.put(scriptRef.getId(), (LightVirtualFile)file);
    }

    if (tokenPosToLineAndColumn == null) {
      tokenPosToLineAndColumn = createTokenPosToLineAndColumnMap(script.getTokenPosTable());
      myScriptIdToLinesAndColumnsMap.put(scriptRef.getId(), tokenPosToLineAndColumn);
    }

    final Pair<Integer, Integer> lineAndColumn = tokenPosToLineAndColumn.get(tokenPos);
    if (lineAndColumn == null) return XDebuggerUtil.getInstance().createPositionByOffset(file, 0);
    return XDebuggerUtil.getInstance().createPosition(file, lineAndColumn.first, lineAndColumn.second);
  }

  private static boolean isDartPatchUri(@NotNull final String uri) {
    // dart:_builtin or dart:core-patch/core_patch.dart
    return uri.startsWith("dart:_") || uri.startsWith("dart:") && uri.contains("-patch/");
  }

  @NotNull
  private static TIntObjectHashMap<Pair<Integer, Integer>> createTokenPosToLineAndColumnMap(@NotNull final List<List<Integer>> tokenPosTable) {
    // Each subarray consists of a line number followed by (tokenPos, columnNumber) pairs
    // see https://github.com/dart-lang/vm_service_drivers/blob/master/dart/tool/service.md#script
    final TIntObjectHashMap<Pair<Integer, Integer>> result = new TIntObjectHashMap<>();

    for (List<Integer> lineAndPairs : tokenPosTable) {
      final Iterator<Integer> iterator = lineAndPairs.iterator();
      final int line = Math.max(0, iterator.next() - 1);
      while (iterator.hasNext()) {
        final int tokenPos = iterator.next();
        final int column = Math.max(0, iterator.next() - 1);
        result.put(tokenPos, Pair.create(line, column));
      }
    }

    return result;
  }

  @NotNull
  private static String threeSlashize(@NotNull final String uri) {
    if (!uri.startsWith("file:")) return uri;
    if (uri.startsWith("file:///")) return uri;
    if (uri.startsWith("file://")) return "file:///" + uri.substring("file://".length());
    if (uri.startsWith("file:/")) return "file:///" + uri.substring("file:/".length());
    if (uri.startsWith("file:")) return "file:///" + uri.substring("file:".length());
    return uri;
  }

  private String getRemoteProjectRootUri() {
    if (!baseUriWasInited) {
      final FlutterApp app = myConnector != null ? myConnector.getApp() : null;
      if (app != null && app.baseUri() != null) {
        setRemoteProjectRootUri(app.baseUri());
      }
    }

    try {
      // We use reflection here to access a private field in the super class.
      final java.lang.reflect.Field field = getDeclaredField("myRemoteProjectRootUri");
      return field == null ? null : (String)field.get(this);
    }
    catch (IllegalAccessException ex) {
      LOG.warn("error accessing myRemoteProjectRootUri", ex);
      return null;
    }
  }

  private void setRemoteProjectRootUri(String value) {
    baseUriWasInited = true;

    try {
      // We use reflection here to access a private field in the super class.
      final java.lang.reflect.Field field = getDeclaredField("myRemoteProjectRootUri");
      if (field != null) {
        field.set(this, value);
      }
    }
    catch (IllegalAccessException ex) {
      LOG.warn("error accessing myRemoteProjectRootUri", ex);
    }
  }

  @SuppressWarnings("SameParameterValue")
  private java.lang.reflect.Field getDeclaredField(String name) {
    return getDeclaredField(getClass(), name);
  }

  private java.lang.reflect.Field getDeclaredField(Class clazz, String name) {
    try {
      final java.lang.reflect.Field field = clazz.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    }
    catch (NoSuchFieldException ex) {
      if (clazz.getSuperclass() != null) {
        return getDeclaredField(clazz.getSuperclass(), name);
      }
      else {
        return null;
      }
    }
  }
}
