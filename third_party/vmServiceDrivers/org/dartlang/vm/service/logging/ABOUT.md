<!--* freshness: { reviewed: '2026-08-17' } *-->
# vmServiceDrivers Logging

## Overview
Provides logging interfaces and a global logging configuration for the Dart VM Service Drivers.

## Interface
- `Logger` (interface) - defines logError and logInformation methods
- `Logger.NullLogger` (class) - no-op implementation of Logger
- `Logger.NULL` (static constant) - instance of NullLogger
- `Logging` (class) - manages a global Logger instance
- `Logging.getLogger()` (static method)
- `Logging.setLogger(Logger)` (static method)

## Invariants
- The global logger instance in `Logging` is never null; if set to null, it defaults to `Logger.NULL`

## Side Effects
- `Logging.setLogger()` mutates global static state
