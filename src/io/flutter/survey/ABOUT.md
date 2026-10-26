<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Survey

## Overview
Integrates with Google Cloud Storage to conditionally prompt users with Flutter user surveys based on engagement frequency.

## Interface
- [`FlutterSurvey`](FlutterSurvey.java) - Survey model with `uniqueId`, `startDate`, `endDate`, `title` and `urlPrefix`.
- `FlutterSurvey.fromJson` - Maps JSON keys to fields (the JSON `url` key becomes `urlPrefix`); dates are parsed as `ISO_ZONED_DATE_TIME`.
- [`FlutterSurveyNotifications`](FlutterSurveyNotifications.java)
- `FlutterSurveyNotifications.init` - Entry point, called per project from [`FlutterInitializer`](../FlutterInitializer.java).
- [`FlutterSurveyService`](FlutterSurveyService.java) - Fetches and caches the latest survey content.

## Invariants
- Survey notification prompts are shown at most once every 40 hours. `FLUTTER_LAST_SURVEY_PROMPT_KEY` is written before the "already taken/dismissed" check, so it records the last eligible check, and the 40h timer resets even if no notification is shown.
- Survey metadata is fetched from the network at most once every 40 hours (`CHECK_INTERVAL_IN_MS`; a code comment saying "daily" is stale).
- The fetch timestamp is written before the async fetch starts. A failed fetch (`cachedSurvey = null`) suppresses surveys until the next 40h window. Fetched content is only used on a later editor event.
- A user is prompted for each unique survey at most once (taking or dismissing it prevents further prompts).
- A survey is considered open only if the current time is on or after its startDate and before its endDate.
- Survey display checks are only triggered when opening or selecting Dart files or pubspec files.

## Side Effects
- Registers a FileEditorManagerListener on the project's message bus.
- Makes asynchronous HTTP network requests to a Google Cloud Storage URL to fetch survey metadata.
- Mutates IDE properties (PropertiesComponent) to track the last check time, last prompt time, and completion/dismissal status per survey.
- Displays UI notifications to the user with a 3-second delay. The delay uses a single-thread scheduler in try-with-resources; because `ExecutorService.close()` waits for termination, the calling (editor listener) thread blocks for those ~3 seconds.
- Opens an external web browser when a user chooses to take a survey, passing IDE and SDK version info in the URL.
