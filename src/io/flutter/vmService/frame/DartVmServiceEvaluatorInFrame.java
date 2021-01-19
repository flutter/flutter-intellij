package io.flutter.vmService.frame;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.xdebugger.XSourcePosition;
import io.flutter.vmService.DartVmServiceDebugProcess;
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
    myDebugProcess.getVmServiceWrapper().evaluateInFrame(myIsolateId, myFrame, replaceNewlines(expression), callback);
  }

  private String replaceNewlines(String expr) {
    if (SystemInfo.isWindows) {
      // Doing separately in case we only have \n in this string.
      return expr.replaceAll("\n", " ").replaceAll("\r", " ");
    } else {
      return expr.replaceAll("\n", " ");
    }
  }
}
