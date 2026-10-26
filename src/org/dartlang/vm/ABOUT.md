<!--* freshness: { reviewed: '2026-10-25' } *-->
# org.dartlang.vm

## Overview
Groups packages related to the Dart Virtual Machine. The code was forked from [dart-lang/vm_service_drivers](https://github.com/dart-lang/vm_service_drivers) and is now maintained in this repo (see [readme.txt](readme.txt)).

## Files
- [readme.txt](readme.txt) - Provenance note for the fork.
- [LICENSE](LICENSE) - BSD-style license from the Dart project authors.

## Licensing
Licensing is mixed. [LICENSE](LICENSE) is BSD-style, but most Java sources under `service/` (e.g. [VmService.java](service/VmService.java)) carry Eclipse Public License v1.0 headers. Newer Kotlin files (e.g. [WebSocket.kt](service/internal/websocket/WebSocket.kt)) carry the Chromium BSD-style header.

## Subdirectories & Boundaries
- `service`: [./service/ABOUT.md](./service/ABOUT.md) - Dart VM Service client implementation, including the VmService class and related listeners for interacting with a running Dart VM via JSON-RPC.
