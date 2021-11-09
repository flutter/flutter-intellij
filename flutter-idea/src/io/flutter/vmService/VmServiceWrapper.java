package io.flutter.vmService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.jetbrains.lang.dart.DartFileType;
import io.flutter.FlutterInitializer;
import io.flutter.analytics.Analytics;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.vmService.frame.DartAsyncMarkerFrame;
import io.flutter.vmService.frame.DartVmServiceEvaluator;
import io.flutter.vmService.frame.DartVmServiceStackFrame;
import io.flutter.vmService.frame.DartVmServiceValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.AddBreakpointWithScriptUriConsumer;
import org.dartlang.vm.service.consumer.EvaluateConsumer;
import org.dartlang.vm.service.consumer.EvaluateInFrameConsumer;
import org.dartlang.vm.service.consumer.GetIsolateConsumer;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.consumer.GetStackConsumer;
import org.dartlang.vm.service.consumer.InvokeConsumer;
import org.dartlang.vm.service.consumer.PauseConsumer;
import org.dartlang.vm.service.consumer.RemoveBreakpointConsumer;
import org.dartlang.vm.service.consumer.SetExceptionPauseModeConsumer;
import org.dartlang.vm.service.consumer.SuccessConsumer;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.Breakpoint;
import org.dartlang.vm.service.element.ElementList;
import org.dartlang.vm.service.element.ErrorRef;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.EventKind;
import org.dartlang.vm.service.element.ExceptionPauseMode;
import org.dartlang.vm.service.element.Frame;
import org.dartlang.vm.service.element.FrameKind;
import org.dartlang.vm.service.element.InstanceRef;
import org.dartlang.vm.service.element.Isolate;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.LibraryRef;
import org.dartlang.vm.service.element.Obj;
import org.dartlang.vm.service.element.RPCError;
import org.dartlang.vm.service.element.Script;
import org.dartlang.vm.service.element.ScriptRef;
import org.dartlang.vm.service.element.Sentinel;
import org.dartlang.vm.service.element.Stack;
import org.dartlang.vm.service.element.StepOption;
import org.dartlang.vm.service.element.Success;
import org.dartlang.vm.service.element.UnresolvedSourceLocation;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VmServiceWrapper implements Disposable {
  private static final Logger LOG = Logger.getInstance(VmServiceWrapper.class.getName());

  private static final long RESPONSE_WAIT_TIMEOUT = 3000; // millis

  private final DartVmServiceDebugProcess myDebugProcess;
  @NotNull private final VmService myVmService;
  private final DartVmServiceListener myVmServiceListener;
  private final IsolatesInfo myIsolatesInfo;
  private final DartVmServiceBreakpointHandler myBreakpointHandler;
  private final Alarm myRequestsScheduler;
  private final Map<Integer, CanonicalBreakpoint> breakpointNumbersToCanonicalMap;
  private final Set<CanonicalBreakpoint> canonicalBreakpoints;

  private long myVmServiceReceiverThreadId;

  @Nullable private StepOption myLatestStep;

  public VmServiceWrapper(@NotNull final DartVmServiceDebugProcess debugProcess,
                          @NotNull final VmService vmService,
                          @NotNull final DartVmServiceListener vmServiceListener,
                          @NotNull final IsolatesInfo isolatesInfo,
                          @NotNull final DartVmServiceBreakpointHandler breakpointHandler) {
    myDebugProcess = debugProcess;
    myVmService = vmService;
    myVmServiceListener = vmServiceListener;
    myIsolatesInfo = isolatesInfo;
    myBreakpointHandler = breakpointHandler;
    myRequestsScheduler = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    breakpointNumbersToCanonicalMap = new HashMap<>();
    canonicalBreakpoints = new HashSet<>();
  }

  @NotNull
  public VmService getVmService() {
    return myVmService;
  }

  @Override
  public void dispose() {
  }

  private void addRequest(@NotNull final Runnable runnable) {
    if (!myRequestsScheduler.isDisposed()) {
      myRequestsScheduler.addRequest(runnable, 0);
    }
  }

  public List<IsolateRef> getExistingIsolates() {
    final List<IsolateRef> isolateRefs = new ArrayList<>();
    for (IsolatesInfo.IsolateInfo isolateInfo : myIsolatesInfo.getIsolateInfos()) {
      isolateRefs.add(isolateInfo.getIsolateRef());
    }
    return isolateRefs;
  }

  @Nullable
  public StepOption getLatestStep() {
    return myLatestStep;
  }

  private void assertSyncRequestAllowed() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      LOG.error("EDT should not be blocked by waiting for for the answer from the Dart debugger");
    }
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      LOG.error("Waiting for the answer from the Dart debugger under read action may lead to EDT freeze");
    }
    if (myVmServiceReceiverThreadId == Thread.currentThread().getId()) {
      LOG.error("Synchronous requests must not be made in Web Socket listening thread: answer will never be received");
    }
  }

  public void handleDebuggerConnected() {
    streamListen(VmService.DEBUG_STREAM_ID, new VmServiceConsumers.SuccessConsumerWrapper() {
      @Override
      public void received(final Success success) {
        myVmServiceReceiverThreadId = Thread.currentThread().getId();
        streamListen(VmService.ISOLATE_STREAM_ID, new VmServiceConsumers.SuccessConsumerWrapper() {
          @Override
          public void received(final Success success) {
            getVm(new VmServiceConsumers.VmConsumerWrapper() {
              @Override
              public void received(final VM vm) {
                for (final IsolateRef isolateRef : vm.getIsolates()) {
                  getIsolate(isolateRef.getId(), new VmServiceConsumers.GetIsolateConsumerWrapper() {
                    @Override
                    public void received(final Isolate isolate) {
                      final Event event = isolate.getPauseEvent();
                      final EventKind eventKind = event.getKind();

                      // Ignore isolates that are very early in their lifecycle. You can't set breakpoints on them
                      // yet, and we'll get lifecycle events for them later.
                      if (eventKind == EventKind.None) {
                        return;
                      }

                      // This is the entry point for attaching a debugger to a running app.
                      if (eventKind == EventKind.Resume) {
                        attachIsolate(isolateRef, isolate);
                        return;
                      }
                      // if event is not PauseStart it means that PauseStart event will follow later and will be handled by listener
                      handleIsolate(isolateRef, eventKind == EventKind.PauseStart);

                      // Handle the case of isolates paused when we connect (this can come up in remote debugging).
                      if (eventKind == EventKind.PauseBreakpoint ||
                          eventKind == EventKind.PauseException ||
                          eventKind == EventKind.PauseInterrupted) {
                        myDebugProcess.isolateSuspended(isolateRef);

                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                          final ElementList<Breakpoint> breakpoints =
                            eventKind == EventKind.PauseBreakpoint ? event.getPauseBreakpoints() : null;
                          final InstanceRef exception = eventKind == EventKind.PauseException ? event.getException() : null;
                          myVmServiceListener
                            .onIsolatePaused(isolateRef, breakpoints, exception, event.getTopFrame(), event.getAtAsyncSuspension());
                        });
                      }
                    }
                  });
                }
              }
            });
          }
        });
      }
    });
  }

  private void streamListen(@NotNull final String streamId, @NotNull final SuccessConsumer consumer) {
    addRequest(() -> myVmService.streamListen(streamId, consumer));
  }

  private void getVm(@NotNull final VMConsumer consumer) {
    addRequest(() -> myVmService.getVM(consumer));
  }

  public CompletableFuture<Isolate> getCachedIsolate(@NotNull final String isolateId) {
    return myIsolatesInfo.getCachedIsolate(isolateId, () -> {
      final CompletableFuture<Isolate> isolateFuture = new CompletableFuture<>();
      getIsolate(isolateId, new GetIsolateConsumer() {

        @Override
        public void onError(RPCError error) {
          isolateFuture.completeExceptionally(new RuntimeException(error.getMessage()));
        }

        @Override
        public void received(Isolate response) {
          isolateFuture.complete(response);
        }

        @Override
        public void received(Sentinel response) {
          // Unable to get the isolate.
          isolateFuture.complete(null);
        }
      });
      return isolateFuture;
    });
  }

  private void getIsolate(@NotNull final String isolateId, @NotNull final GetIsolateConsumer consumer) {
    addRequest(() -> myVmService.getIsolate(isolateId, consumer));
  }

  public void handleIsolate(@NotNull final IsolateRef isolateRef, final boolean isolatePausedStart) {
    // We should auto-resume on a StartPaused event, if we're not remote debugging, and after breakpoints have been set.

    final boolean newIsolate = myIsolatesInfo.addIsolate(isolateRef);

    if (isolatePausedStart) {
      myIsolatesInfo.setShouldInitialResume(isolateRef);
    }

    // Just to make sure that the main isolate is not handled twice, both from handleDebuggerConnected() and DartVmServiceListener.received(PauseStart)
    if (newIsolate) {
      addRequest(() -> myVmService.setExceptionPauseMode(
        isolateRef.getId(),
        myDebugProcess.getBreakOnExceptionMode(),
        new SetExceptionPauseModeConsumer() {
          @Override
          public void received(Success response) {
            setInitialBreakpointsAndResume(isolateRef);
          }

          @Override
          public void onError(RPCError error) {

          }

          @Override
          public void received(Sentinel response) {

          }
        }));
    }
    else {
      checkInitialResume(isolateRef);
    }
  }

  public void attachIsolate(@NotNull IsolateRef isolateRef, @NotNull Isolate isolate) {
    final boolean newIsolate = myIsolatesInfo.addIsolate(isolateRef);
    // Just to make sure that the main isolate is not handled twice, both from handleDebuggerConnected() and DartVmServiceListener.received(PauseStart)
    if (newIsolate) {
      final XDebugSessionImpl session = (XDebugSessionImpl)myDebugProcess.getSession();
      ApplicationManager.getApplication().runReadAction(() -> {
        session.reset();
        session.initBreakpoints();
      });
      addRequest(() -> myVmService.setExceptionPauseMode(
        isolateRef.getId(),
        myDebugProcess.getBreakOnExceptionMode(),
        new SetExceptionPauseModeConsumer() {
          @Override
          public void received(Success response) {
            setInitialBreakpointsAndCheckExtensions(isolateRef, isolate);
          }

          @Override
          public void onError(RPCError error) {

          }

          @Override
          public void received(Sentinel response) {

          }
        }));
    }
  }

  private void checkInitialResume(IsolateRef isolateRef) {
    if (myIsolatesInfo.getShouldInitialResume(isolateRef)) {
      resumeIsolate(isolateRef.getId(), null);
    }
  }

  private void setInitialBreakpointsAndResume(@NotNull final IsolateRef isolateRef) {
    if (myDebugProcess.myRemoteProjectRootUri == null) {
      // need to detect remote project root path before setting breakpoints
      getIsolate(isolateRef.getId(), new VmServiceConsumers.GetIsolateConsumerWrapper() {
        @Override
        public void received(final Isolate isolate) {
          myDebugProcess.guessRemoteProjectRoot(isolate.getLibraries());
          doSetInitialBreakpointsAndResume(isolateRef);
        }
      });
    }
    else {
      doSetInitialBreakpointsAndResume(isolateRef);
    }
  }

  private void setInitialBreakpointsAndCheckExtensions(@NotNull IsolateRef isolateRef, @NotNull Isolate isolate) {
    doSetBreakpointsForIsolate(myBreakpointHandler.getXBreakpoints(), isolateRef.getId(), () -> {
      myIsolatesInfo.setBreakpointsSet(isolateRef);
    });
    final FlutterApp app = FlutterApp.fromEnv(myDebugProcess.getExecutionEnvironment());
    // TODO(messick) Consider replacing this test with an assert; could interfere with setExceptionPauseMode().
    if (app != null) {
      final VMServiceManager service = app.getVMServiceManager();
      if (service != null) {
        service.addRegisteredExtensionRPCs(isolate, true);
      }
    }
  }

  private void doSetInitialBreakpointsAndResume(@NotNull final IsolateRef isolateRef) {
    doSetBreakpointsForIsolate(myBreakpointHandler.getXBreakpoints(), isolateRef.getId(), () -> {
      myIsolatesInfo.setBreakpointsSet(isolateRef);
      checkInitialResume(isolateRef);
    });
  }

  private void doSetBreakpointsForIsolate(@NotNull final Set<XLineBreakpoint<XBreakpointProperties>> xBreakpoints,
                                          @NotNull final String isolateId,
                                          @Nullable final Runnable onFinished) {
    if (xBreakpoints.isEmpty()) {
      if (onFinished != null) {
        onFinished.run();
      }
      return;
    }

    final AtomicInteger counter = new AtomicInteger(xBreakpoints.size());

    for (final XLineBreakpoint<XBreakpointProperties> xBreakpoint : xBreakpoints) {
      addBreakpoint(isolateId, xBreakpoint.getSourcePosition(), new VmServiceConsumers.BreakpointsConsumer() {
        @Override
        void sourcePositionNotApplicable() {
          myBreakpointHandler.breakpointFailed(xBreakpoint);

          checkDone();
        }

        @Override
        void received(List<Breakpoint> breakpointResponses, List<RPCError> errorResponses) {
          if (breakpointResponses.size() > 0) {
            for (Breakpoint breakpoint : breakpointResponses) {
              myBreakpointHandler.vmBreakpointAdded(xBreakpoint, isolateId, breakpoint);
            }
          }
          else if (errorResponses.size() > 0) {
            myBreakpointHandler.breakpointFailed(xBreakpoint);
          }

          checkDone();
        }

        private void checkDone() {
          if (counter.decrementAndGet() == 0 && onFinished != null) {
            onFinished.run();

            myVmService.getIsolate(isolateId, new GetIsolateConsumer() {
              @Override
              public void received(Isolate response) {
                final Set<String> libraryUris = new HashSet<>();
                final Set<String> fileNames = new HashSet<>();
                for (LibraryRef library : response.getLibraries()) {
                  final String uri = library.getUri();
                  libraryUris.add(uri);
                  final String[] split = uri.split("/");
                  fileNames.add(split[split.length - 1]);
                }

                final ElementList<Breakpoint> breakpoints = response.getBreakpoints();
                if (breakpoints.isEmpty()) {
                  return;
                }

                final Set<CanonicalBreakpoint> mappedCanonicalBreakpoints = new HashSet<>();
                for (Breakpoint breakpoint : breakpoints) {
                  Object location = breakpoint.getLocation();
                  // In JIT mode, locations will be unresolved at this time since files aren't compiled until they are used.
                  if (location instanceof UnresolvedSourceLocation) {
                    final ScriptRef script = ((UnresolvedSourceLocation)location).getScript();
                    if (script != null && libraryUris.contains(script.getUri())) {
                      mappedCanonicalBreakpoints.add(breakpointNumbersToCanonicalMap.get(breakpoint.getBreakpointNumber()));
                    }
                  }
                }

                final Analytics analytics = FlutterInitializer.getAnalytics();
                final String category = "breakpoint";

                final Sets.SetView<CanonicalBreakpoint> initialDifference =
                  Sets.difference(canonicalBreakpoints, mappedCanonicalBreakpoints);
                final Set<CanonicalBreakpoint> finalDifference = new HashSet<>();

                for (CanonicalBreakpoint missingBreakpoint : initialDifference) {
                  // If the file name doesn't exist in loaded library files, then most likely it's not part of the dependencies of what was
                  // built. So it's okay to ignore these breakpoints in our count.
                  if (fileNames.contains(missingBreakpoint.fileName)) {
                    finalDifference.add(missingBreakpoint);
                  }
                }

                analytics.sendEventMetric(category, "unmapped-count", finalDifference.size());

                // For internal bazel projects, report files where mapping failed.
                if (WorkspaceCache.getInstance(myDebugProcess.getSession().getProject()).isBazel()) {
                  for (CanonicalBreakpoint canonicalBreakpoint : finalDifference) {
                    if (canonicalBreakpoint.path.contains("google3")) {
                      analytics.sendEvent(category,
                                          String.format("unmapped-file|%s|%s", response.getRootLib().getUri(), canonicalBreakpoint.path));
                    }
                  }
                }
              }

              @Override
              public void received(Sentinel response) {

              }

              @Override
              public void onError(RPCError error) {

              }
            });
          }
        }
      });
    }
  }

  public void addBreakpoint(@NotNull final String isolateId,
                            @Nullable final XSourcePosition position,
                            @NotNull final VmServiceConsumers.BreakpointsConsumer consumer) {
    if (position == null || position.getFile().getFileType() != DartFileType.INSTANCE) {
      consumer.sourcePositionNotApplicable();
      return;
    }

    addRequest(() -> {
      final int line = position.getLine() + 1;

      final Collection<String> scriptUris = myDebugProcess.getUrisForFile(position.getFile());
      final CanonicalBreakpoint canonicalBreakpoint =
        new CanonicalBreakpoint(position.getFile().getName(), position.getFile().getCanonicalPath(), line);
      canonicalBreakpoints.add(canonicalBreakpoint);
      final List<Breakpoint> breakpointResponses = new ArrayList<>();
      final List<RPCError> errorResponses = new ArrayList<>();

      for (String uri : scriptUris) {
        myVmService.addBreakpointWithScriptUri(isolateId, uri, line, new AddBreakpointWithScriptUriConsumer() {
          @Override
          public void received(Breakpoint response) {
            breakpointResponses.add(response);
            breakpointNumbersToCanonicalMap.put(response.getBreakpointNumber(), canonicalBreakpoint);

            checkDone();
          }

          @Override
          public void received(Sentinel response) {
            checkDone();
          }

          @Override
          public void onError(RPCError error) {
            errorResponses.add(error);

            checkDone();
          }

          private void checkDone() {
            if (scriptUris.size() == breakpointResponses.size() + errorResponses.size()) {
              consumer.received(breakpointResponses, errorResponses);
            }
          }
        });
      }
    });
  }

  public void addBreakpointForIsolates(@NotNull final XLineBreakpoint<XBreakpointProperties> xBreakpoint,
                                       @NotNull final Collection<IsolatesInfo.IsolateInfo> isolateInfos) {
    for (final IsolatesInfo.IsolateInfo isolateInfo : isolateInfos) {
      addBreakpoint(isolateInfo.getIsolateId(), xBreakpoint.getSourcePosition(), new VmServiceConsumers.BreakpointsConsumer() {
        @Override
        void sourcePositionNotApplicable() {
          myBreakpointHandler.breakpointFailed(xBreakpoint);
        }

        @Override
        void received(List<Breakpoint> breakpointResponses, List<RPCError> errorResponses) {
          for (Breakpoint breakpoint : breakpointResponses) {
            myBreakpointHandler.vmBreakpointAdded(xBreakpoint, isolateInfo.getIsolateId(), breakpoint);
          }
        }
      });
    }
  }

  /**
   * Reloaded scripts need to have their breakpoints re-applied; re-set all existing breakpoints.
   */
  public void restoreBreakpointsForIsolate(@NotNull final String isolateId, @Nullable final Runnable onFinished) {
    // Cached information about the isolate may now be stale.
    myIsolatesInfo.invalidateCache(isolateId);

    // Remove all existing VM breakpoints for this isolate.
    myBreakpointHandler.removeAllVmBreakpoints(isolateId);
    // Re-set existing breakpoints.
    doSetBreakpointsForIsolate(myBreakpointHandler.getXBreakpoints(), isolateId, onFinished);
  }

  public void addTemporaryBreakpoint(@NotNull final XSourcePosition position, @NotNull final String isolateId) {
    addBreakpoint(isolateId, position, new VmServiceConsumers.BreakpointsConsumer() {
      @Override
      void sourcePositionNotApplicable() {
      }

      @Override
      void received(List<Breakpoint> breakpointResponses, List<RPCError> errorResponses) {
        for (Breakpoint breakpoint : breakpointResponses) {
          myBreakpointHandler.temporaryBreakpointAdded(isolateId, breakpoint);
        }
      }
    });
  }

  public void removeBreakpoint(@NotNull final String isolateId, @NotNull final String vmBreakpointId) {
    addRequest(() -> myVmService.removeBreakpoint(isolateId, vmBreakpointId, new RemoveBreakpointConsumer() {
      @Override
      public void onError(RPCError error) {

      }

      @Override
      public void received(Sentinel response) {

      }

      @Override
      public void received(Success response) {

      }
    }));
  }

  public void resumeIsolate(@NotNull final String isolateId, @Nullable final StepOption stepOption) {
    addRequest(() -> {
      myLatestStep = stepOption;
      myVmService.resume(isolateId, stepOption, null, new VmServiceConsumers.EmptyResumeConsumer() {
      });
    });
  }

  public void setExceptionPauseMode(@NotNull final ExceptionPauseMode mode) {
    for (final IsolatesInfo.IsolateInfo isolateInfo : myIsolatesInfo.getIsolateInfos()) {
      addRequest(() -> myVmService.setExceptionPauseMode(isolateInfo.getIsolateId(), mode, new SetExceptionPauseModeConsumer() {
        @Override
        public void onError(RPCError error) {

        }

        @Override
        public void received(Sentinel response) {

        }

        @Override
        public void received(Success response) {

        }
      }));
    }
  }

  /**
   * Drop to the indicated frame.
   * <p>
   * frameIndex specifies the stack frame to rewind to. Stack frame 0 is the currently executing
   * function, so frameIndex must be at least 1.
   */
  public void dropFrame(@NotNull final String isolateId, int frameIndex) {
    addRequest(() -> {
      myLatestStep = StepOption.Rewind;
      myVmService.resume(isolateId, StepOption.Rewind, frameIndex, new VmServiceConsumers.EmptyResumeConsumer() {
        @Override
        public void onError(RPCError error) {
          myDebugProcess.getSession().getConsoleView()
            .print("Error from drop frame: " + error.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
      });
    });
  }

  public void pauseIsolate(@NotNull final String isolateId) {
    addRequest(() -> myVmService.pause(isolateId, new PauseConsumer() {
      @Override
      public void onError(RPCError error) {
      }

      @Override
      public void received(Sentinel response) {
      }

      @Override
      public void received(Success response) {
      }
    }));
  }

  public void computeStackFrames(@NotNull final String isolateId,
                                 final int firstFrameIndex,
                                 @NotNull final XExecutionStack.XStackFrameContainer container,
                                 @Nullable final InstanceRef exception) {
    addRequest(() -> myVmService.getStack(isolateId, new GetStackConsumer() {
      @Override
      public void received(final Stack vmStack) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          InstanceRef exceptionToAddToFrame = exception;

          // Check for async causal frames; fall back to using regular sync frames.
          ElementList<Frame> elementList = vmStack.getAsyncCausalFrames();
          if (elementList == null) {
            elementList = vmStack.getFrames();
          }

          final List<Frame> vmFrames = Lists.newArrayList(elementList);
          final List<XStackFrame> xStackFrames = new ArrayList<>(vmFrames.size());

          for (final Frame vmFrame : vmFrames) {
            if (vmFrame.getKind() == FrameKind.AsyncSuspensionMarker) {
              // Render an asynchronous gap.
              final XStackFrame markerFrame = new DartAsyncMarkerFrame();
              xStackFrames.add(markerFrame);
            }
            else {
              final DartVmServiceStackFrame stackFrame =
                new DartVmServiceStackFrame(myDebugProcess, isolateId, vmFrame, vmFrames, exceptionToAddToFrame);
              stackFrame.setIsDroppableFrame(vmFrame.getKind() == FrameKind.Regular);
              xStackFrames.add(stackFrame);

              if (!stackFrame.isInDartSdkPatchFile()) {
                // The exception (if any) is added to the frame where debugger stops and to the upper frames.
                exceptionToAddToFrame = null;
              }
            }
          }
          container.addStackFrames(firstFrameIndex == 0 ? xStackFrames : xStackFrames.subList(firstFrameIndex, xStackFrames.size()), true);
        });
      }

      @Override
      public void onError(final RPCError error) {
        container.errorOccurred(error.getMessage());
      }

      @Override
      public void received(Sentinel response) {
        container.errorOccurred(response.getValueAsString());
      }
    }));
  }

  @Nullable
  public Script getScriptSync(@NotNull final String isolateId, @NotNull final String scriptId) {
    assertSyncRequestAllowed();

    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final Ref<Script> resultRef = Ref.create();

    addRequest(() -> myVmService.getObject(isolateId, scriptId, new GetObjectConsumer() {
      @Override
      public void received(Obj script) {
        resultRef.set((Script)script);
        semaphore.up();
      }

      @Override
      public void received(Sentinel response) {
        semaphore.up();
      }

      @Override
      public void onError(RPCError error) {
        semaphore.up();
      }
    }));

    semaphore.waitFor(RESPONSE_WAIT_TIMEOUT);
    return resultRef.get();
  }

  public void getObject(@NotNull final String isolateId, @NotNull final String objectId, @NotNull final GetObjectConsumer consumer) {
    addRequest(() -> myVmService.getObject(isolateId, objectId, consumer));
  }

  public void getCollectionObject(@NotNull final String isolateId,
                                  @NotNull final String objectId,
                                  final int offset,
                                  final int count,
                                  @NotNull final GetObjectConsumer consumer) {
    addRequest(() -> myVmService.getObject(isolateId, objectId, offset, count, consumer));
  }

  public void evaluateInFrame(@NotNull final String isolateId,
                              @NotNull final Frame vmFrame,
                              @NotNull final String expression,
                              @NotNull final XDebuggerEvaluator.XEvaluationCallback callback) {
    addRequest(() -> myVmService.evaluateInFrame(isolateId, vmFrame.getIndex(), expression, new EvaluateInFrameConsumer() {
      @Override
      public void received(InstanceRef instanceRef) {
        callback.evaluated(new DartVmServiceValue(myDebugProcess, isolateId, "result", instanceRef, null, null, false));
      }

      @Override
      public void received(Sentinel sentinel) {
        callback.errorOccurred(sentinel.getValueAsString());
      }

      @Override
      public void received(ErrorRef errorRef) {
        callback.errorOccurred(DartVmServiceEvaluator.getPresentableError(errorRef.getMessage()));
      }

      @Override
      public void onError(RPCError error) {
        callback.errorOccurred(error.getMessage());
      }
    }));
  }

  @SuppressWarnings("SameParameterValue")
  public void evaluateInTargetContext(@NotNull final String isolateId,
                                      @NotNull final String targetId,
                                      @NotNull final String expression,
                                      @NotNull final EvaluateConsumer consumer) {
    addRequest(() -> myVmService.evaluate(isolateId, targetId, expression, consumer));
  }

  public void evaluateInTargetContext(@NotNull final String isolateId,
                                      @NotNull final String targetId,
                                      @NotNull final String expression,
                                      @NotNull final XDebuggerEvaluator.XEvaluationCallback callback) {
    evaluateInTargetContext(isolateId, targetId, expression, new EvaluateConsumer() {
      @Override
      public void received(InstanceRef instanceRef) {
        callback.evaluated(new DartVmServiceValue(myDebugProcess, isolateId, "result", instanceRef, null, null, false));
      }

      @Override
      public void received(Sentinel sentinel) {
        callback.errorOccurred(sentinel.getValueAsString());
      }

      @Override
      public void received(ErrorRef errorRef) {
        callback.errorOccurred(DartVmServiceEvaluator.getPresentableError(errorRef.getMessage()));
      }

      @Override
      public void onError(RPCError error) {
        callback.errorOccurred(error.getMessage());
      }
    });
  }

  public void callToString(@NotNull String isolateId, @NotNull String targetId, @NotNull InvokeConsumer callback) {
    callMethodOnTarget(isolateId, targetId, "toString", callback);
  }

  public void callToList(@NotNull String isolateId, @NotNull String targetId, @NotNull InvokeConsumer callback) {
    callMethodOnTarget(isolateId, targetId, "toList", callback);
  }

  public void callMethodOnTarget(@NotNull final String isolateId,
                                 @NotNull final String targetId,
                                 @NotNull String methodName,
                                 @NotNull final InvokeConsumer callback) {
    addRequest(() -> myVmService.invoke(isolateId, targetId, methodName, Collections.emptyList(), true, callback));
  }
}

class CanonicalBreakpoint {
  final String fileName;
  final String path;
  final int line;

  CanonicalBreakpoint(String name, String path, int line) {
    this.fileName = name;
    this.path = path;
    this.line = line;
  }
}
