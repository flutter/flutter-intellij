<!--* freshness: { reviewed: '2026-08-17' } *-->
# Dart VM Service Frames

## Overview
Connects IDE debugger execution stacks, variables, and expressions with the Dart VM Service for inspection.

## Interface
- `DartAsyncMarkerFrame`
- `DartStaticFieldsGroup`
- `DartVmServiceEvaluator`
- `DartVmServiceEvaluatorInFrame`
- `DartVmServiceExecutionStack`
- `DartVmServiceStackFrame`
- `DartVmServiceSuspendContext`
- `DartVmServiceValue`

## Invariants
- topFrame is not null if and only if it is the active execution stack in DartVmServiceExecutionStack
- A frame can be dropped if it is droppable and not the last frame in the stack
- Collections are processed in chunks bounded by XCompositeNode.MAX_CHILDREN_TO_SHOW

## Side Effects
- Disables and restores VM Service exception pause mode during expression evaluation
- Makes asynchronous RPC calls to the Dart VM Service (evaluating expressions, fetching object instances, and computing stack frames)
- Calculates source position offsets asynchronously on an application pooled thread
