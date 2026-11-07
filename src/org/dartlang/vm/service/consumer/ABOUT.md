<!--* freshness: { reviewed: '2026-11-07' } *-->
# vmServiceDrivers Consumer

## Overview
Contains interface definitions used for handling asynchronous callback responses from the Dart VM Service. These interfaces define `onError` and `received` callbacks for specific service requests.

## Interface
- Base [`Consumer`](Consumer.java) interface.
- 62 protocol-specific consumer interfaces, including [`AddBreakpointConsumer`](AddBreakpointConsumer.java), [`EvaluateConsumer`](EvaluateConsumer.java), [`GetIsolateConsumer`](GetIsolateConsumer.java), [`GetObjectConsumer`](GetObjectConsumer.java), [`InvokeConsumer`](InvokeConsumer.java), [`ServiceExtensionConsumer`](ServiceExtensionConsumer.java), and [`VMConsumer`](VMConsumer.java).

## Invariants
- All interfaces except `Consumer` extend the base `Consumer` interface.
- The base `Consumer` interface defines a single method `onError(RPCError error)` for handling failure responses.
- Each specific consumer interface defines one or more `received(...)` methods corresponding to the expected response element types for a specific VM service request.
- The consumer interfaces were originally generated from the Dart VM Service protocol and are now maintained here. See [readme.txt](../../readme.txt) for the client history and maintenance process.

## Side Effects
- None. The directory purely contains interface definitions used for handling asynchronous callback responses from the Dart VM service.
