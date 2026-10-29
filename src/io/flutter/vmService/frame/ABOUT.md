<!--* freshness: { reviewed: '2026-10-25' } *-->
# Dart VM Service Frames

## Overview
Connects IDE debugger execution stacks, variables, and expressions with the Dart VM Service for inspection.

## Interface
- [`DartAsyncMarkerFrame`](DartAsyncMarkerFrame.java)
- [`DartStaticFieldsGroup`](DartStaticFieldsGroup.java)
- [`DartVmServiceEvaluator`](DartVmServiceEvaluator.java) - Resolves PSI context from the expression position, or else from the selected editor.
- [`DartVmServiceEvaluatorInFrame`](DartVmServiceEvaluatorInFrame.java)
- [`DartVmServiceExecutionStack`](DartVmServiceExecutionStack.java)
- [`DartVmServiceStackFrame`](DartVmServiceStackFrame.java)
- [`DartVmServiceSuspendContext`](DartVmServiceSuspendContext.java)
- [`DartVmServiceValue`](DartVmServiceValue.java) (with nested `DartVmServiceValue.LocalVarSourceLocation`)

## Invariants
- topFrame is not null if and only if it is the active execution stack in DartVmServiceExecutionStack
- A frame can be dropped if it is droppable and not the last frame in the stack
- Collections are processed in chunks bounded by XCompositeNode.MAX_CHILDREN_TO_SHOW

## Side Effects
- Sets the VM Service exception pause mode to `None` during expression evaluation, then sets it to `myDebugProcess.getBreakOnExceptionMode()` (the configured mode, not necessarily the previously active one).
- Makes asynchronous RPC calls to the Dart VM Service (evaluating expressions, fetching object instances, and computing stack frames)
- `DartVmServiceValue.reportSourcePosition` computes source positions on an application pooled thread; `DartVmServiceStackFrame` computes its source position synchronously in its constructor.
