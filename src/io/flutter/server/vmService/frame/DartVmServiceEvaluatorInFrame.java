package io.flutter.server.vmService.frame;

import com.intellij.xdebugger.XSourcePosition;
import io.flutter.server.vmService.DartVmServiceDebugProcess;
import org.dartlang.vm.service.element.Frame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DartVmServiceEvaluatorInFrame extends DartVmServiceEvaluator {

  @NotNull private final String myIsolateId;
  @NotNull private final Frame myFrame;

  public DartVmServiceEvaluatorInFrame(@NotNull final DartVmServiceDebugProcess debugProcess,
                                       @NotNull final String isolateId,
                                       @NotNull final Frame vmFrame) {
    super(debugProcess);
    myIsolateId = isolateId;
    myFrame = vmFrame;
  }

  @Override
  public void evaluate(@NotNull final String expression,
                       @NotNull final XEvaluationCallback callback,
                       @Nullable final XSourcePosition expressionPosition) {
    myDebugProcess.getVmServiceWrapper().evaluateInFrame(myIsolateId, myFrame, expression, callback);
  }
}
