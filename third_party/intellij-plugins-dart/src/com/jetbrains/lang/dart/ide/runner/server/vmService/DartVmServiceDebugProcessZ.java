package com.jetbrains.lang.dart.ide.runner.server.vmService;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.ui.ConsoleViewContentType;
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
 *
 * <p>Doesn't do remote debugging. (The Flutter plugin doesn't need it.)</p>
 */
public class DartVmServiceDebugProcessZ extends DartVmServiceDebugProcess {
  private static final Logger LOG = Logger.getInstance(DartVmServiceDebugProcess.class.getName());

  @NotNull private final DartUrlResolver myDartUrlResolver;

  private boolean myVmConnected = false;

  @NotNull private final XBreakpointHandler[] myBreakpointHandlers;
  private final IsolatesInfo myIsolatesInfo;
  private VmServiceWrapper myVmServiceWrapper;

  @NotNull private final Set<String> mySuspendedIsolateIds = Collections.synchronizedSet(new THashSet<String>());
  private String myLatestCurrentIsolateId;

  private final Map<String, LightVirtualFile> myScriptIdToContentMap = new THashMap<>();
  private final Map<String, TIntObjectHashMap<Pair<Integer, Integer>>> myScriptIdToLinesAndColumnsMap = new THashMap<>();

  /**
   * Same private variable as superclass, but we override all methods so only this one is used.
   */
  @Nullable private final String myDASExecutionContextId;

  @Nullable private final VirtualFile myCurrentWorkingDirectory;
  @NotNull private final ObservatoryConnector myConnector;

  /**
   * A prefix to be removed from a remote URI before looking for a corresponding local file.
   * (Typically a "snapshot prefix".)
   */
  private String mySnapshotBaseUri;

  /**
   * The "devfs" base uri returned by the Flutter app at start.
   */
  private String myRemoteBaseUri;
  private boolean remoteDebug = false;

  public DartVmServiceDebugProcessZ(@NotNull final XDebugSession session,
                                    @NotNull final ExecutionResult executionResult,
                                    @NotNull final DartUrlResolver dartUrlResolver,
                                    @Nullable final String dasExecutionContextId,
                                    @Nullable final VirtualFile currentWorkingDirectory,
                                    @NotNull final ObservatoryConnector connector) {
    super(session, "localhost", 0, executionResult, dartUrlResolver, "fakeExecutionIdNotUsed",
          false, 0, currentWorkingDirectory);

    myDartUrlResolver = dartUrlResolver;
    myCurrentWorkingDirectory = currentWorkingDirectory;
    myConnector = connector;

    myIsolatesInfo = new IsolatesInfo();
    final DartVmServiceBreakpointHandler breakpointHandler = new DartVmServiceBreakpointHandlerZ(this);
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

    myDASExecutionContextId = dasExecutionContextId;

    scheduleConnectNew();

    // We disable the service protocol library logger because of a user facing NPE in a
    // DartVmServiceListener from the Dart plugin.
    Logging.setLogger(org.dartlang.vm.service.logging.Logger.NULL);
  }

  @Override
  public boolean isRemoteDebug() {
    // The flutter plugin doesn't do remote debugging, but we set this value to
    // whatever is needed to fake out the superclass.
    return remoteDebug;
  }

  @NotNull
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
  @SuppressWarnings("EmptyMethod")
  public void scheduleConnect() {
    // This page intentionally left blank.

  }

  public void scheduleConnectNew() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      // Allow 5 minutes to connect to the observatory; the user can cancel manually in the interim.
      final long timeout = (long)5 * 60 * 1000;
      long startTime = System.currentTimeMillis();

      String url = myConnector.getWebSocketUrl();
      while (url == null) {
        if (getSession().isStopped()) return;

        if (System.currentTimeMillis() > startTime + timeout) {
          final String message = "Observatory connection never became ready.\n";
          getSession().getConsoleView().print(message, ConsoleViewContentType.ERROR_OUTPUT);
          getSession().stop();
          return;
        }
        else {
          TimeoutUtil.sleep(50);
        }
        url = myConnector.getWebSocketUrl();
      }

      if (getSession().isStopped()) {
        return;
      }

      startTime = System.currentTimeMillis();

      try {
        while (true) {
          try {
            connect(url);
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

  private void connect(@NotNull String websocketUrl) throws IOException {
    final VmService vmService = VmService.connect(websocketUrl);
    final DartVmServiceListener vmServiceListener =
      new DartVmServiceListener(this, (DartVmServiceBreakpointHandler)myBreakpointHandlers[0]);
    final DartVmServiceBreakpointHandler breakpointHandler = (DartVmServiceBreakpointHandler)myBreakpointHandlers[0];

    myVmServiceWrapper = new VmServiceWrapper(this, vmService, vmServiceListener, myIsolatesInfo, breakpointHandler);

    // We disable the remote debug flag so that handleDebuggerConnected() does not echo the stdout and
    // stderr streams (this would duplicate what we get over daemon logging).
    remoteDebug = false;
    myVmServiceWrapper.handleDebuggerConnected();
    // We re-enable the remote debug flag so that the service wrapper will call our guessRemoteProjectRoot()
    // method with the list of loaded libraries for the isolate.
    remoteDebug = true;

    vmService.addVmServiceListener(vmServiceListener);

    myVmConnected = true;
    getSession().rebuildViews();

    onVmConnected(vmService);
  }

  /**
   * Callback for subclass.
   */
  protected void onVmConnected(@NotNull VmService vmService) {}

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
          mySnapshotBaseUri = remoteUri.substring(0, remoteUri.length() - relPath.length());
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

  @NotNull
  public Collection<String> getUrisForFile(@NotNull final VirtualFile file) {
    final Set<String> results = new HashSet<>();
    final String uriByIde = myDartUrlResolver.getDartUrlForFile(file);

    // If dart:, short circuit the results.
    if (uriByIde.startsWith(DartUrlResolver.DART_PREFIX)) {
      results.add(uriByIde);
      return results;
    }

    // file:
    if (uriByIde.startsWith(DartUrlResolver.FILE_PREFIX)) {
      results.add(threeSlashize(uriByIde));
    }
    else {
      results.add(uriByIde);
      results.add(threeSlashize(new File(file.getPath()).toURI().toString()));
    }

    // straight path - used by some VM embedders
    results.add(file.getPath());

    // package: (if applicable)
    if (myDASExecutionContextId != null) {
      final String uriByServer = DartAnalysisServerService.getInstance().execution_mapUri(myDASExecutionContextId, file.getPath(), null);
      if (uriByServer != null) {
        results.add(uriByServer);
      }
    }

    final VirtualFile pubspec = myDartUrlResolver.getPubspecYamlFile();
    final VirtualFile projectDirectory = pubspec != null ? pubspec.getParent() : myCurrentWorkingDirectory;

    if (projectDirectory != null) {
      final String projectPath = projectDirectory.getPath();
      final String filePath = file.getPath();

      if (filePath.startsWith(projectPath)) {
        // snapshot prefix (if applicable)
        if (getSnapshotBaseUri() != null) {
          results.add(getSnapshotBaseUri() + filePath.substring(projectPath.length()));
        }

        // remote prefix (if applicable)
        if (getRemoteBaseUri() != null) {
          results.add(getRemoteBaseUri() + filePath.substring(projectPath.length()));
        }
      }
    }

    return results;
  }

  @Nullable
  public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final ScriptRef scriptRef, int tokenPos) {
    VirtualFile file = ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
      String uri = scriptRef.getUri();

      if (myDASExecutionContextId != null && !isDartPatchUri(uri)) {
        if (!stringStartsWith(uri, getSnapshotBaseUri()) && !stringStartsWith(uri, getRemoteBaseUri())) {
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

      if (stringStartsWith(uri, getSnapshotBaseUri()) && parent != null) {
        final String localRootUri = StringUtil.trimEnd(myDartUrlResolver.getDartUrlForFile(parent), '/');
        uri = localRootUri + uri.substring(getSnapshotBaseUri().length());
      }
      else if (stringStartsWith(uri, getRemoteBaseUri()) && parent != null) {
        final String localRootUri = StringUtil.trimEnd(myDartUrlResolver.getDartUrlForFile(parent), '/');
        uri = localRootUri + uri.substring(getRemoteBaseUri().length());
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

  private String getSnapshotBaseUri() {
    return mySnapshotBaseUri;
  }

  private String getRemoteBaseUri() {
    if (myRemoteBaseUri == null) {
      myRemoteBaseUri = myConnector.getRemoteBaseUrl();
    }
    return myRemoteBaseUri;
  }

  public boolean getVmConnected() {
    return myVmConnected;
  }

  private static boolean stringStartsWith(String base, String prefix) {
    return prefix != null && base.startsWith(prefix);
  }
}
