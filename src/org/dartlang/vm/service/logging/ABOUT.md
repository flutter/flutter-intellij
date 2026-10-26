<!--* freshness: { reviewed: '2026-10-25' } *-->
# vmServiceDrivers Logging

## Overview
Provides logging interfaces and a global logging configuration for the Dart VM Service Drivers.

## Interface
- [`Logger`](Logger.java) (interface) - defines `logError(String)`, `logError(String, Throwable)`, `logInformation(String)`, and `logInformation(String, Throwable)`
- `Logger.NullLogger` (class) - no-op implementation of Logger
- `Logger.NULL` (static constant) - instance of NullLogger
- [`Logging`](Logging.java) (class) - manages a global Logger instance
- `Logging.getLogger()` (static method)
- `Logging.setLogger(Logger)` (static method)

## Invariants
- The global logger instance in `Logging` is never null; if set to null, it defaults to `Logger.NULL`
- `Logging.logger` is a plain (non-volatile) static field; reads and writes are unsynchronized, so set it once during startup.

## Side Effects
- `Logging.setLogger()` mutates global static state
