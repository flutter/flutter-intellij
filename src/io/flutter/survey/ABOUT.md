<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Survey

## Overview
Integrates with Google Cloud Storage to conditionally prompt users with Flutter user surveys based on engagement frequency.

## Interface
- `FlutterSurvey`
- `FlutterSurvey.fromJson`
- `FlutterSurveyNotifications`
- `FlutterSurveyNotifications.init`
- `FlutterSurveyService`

## Invariants
- Survey notification prompts are shown at most once every 40 hours.
- Survey metadata is fetched from the network at most once every 40 hours.
- A user is prompted for each unique survey at most once (taking or dismissing it prevents further prompts).
- A survey is considered open only if the current time is on or after its startDate and before its endDate.
- Survey display checks are only triggered when opening or selecting Dart files or pubspec files.

## Side Effects
- Registers a FileEditorManagerListener on the project's message bus.
- Makes asynchronous HTTP network requests to a Google Cloud Storage URL to fetch survey metadata.
- Mutates IDE properties (PropertiesComponent) to track the last check time, last prompt time, and completion/dismissal status per survey.
- Displays UI notifications to the user with a 3-second delay.
- Opens an external web browser when a user chooses to take a survey, passing IDE and SDK version info in the URL.
