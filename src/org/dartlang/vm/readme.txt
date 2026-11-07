Dart VM Service client history and maintenance

This code was forked from https://github.com/dart-lang/vm_service_drivers and is
now maintained in this repository.

The Java protocol model, consumer interfaces, and VmService API were originally
generated from the Dart VM Service protocol. That generation workflow is no
longer used for this Java client. The sources are maintained and reviewed as
regular repository code, so they may be edited directly.

VM Service protocol updates may be made in either repository, or in both in
parallel. The Dart plugin repository is
https://github.com/flutter/dart-intellij-third-party. Whichever repository is
updated first, the corresponding changes are then brought to the other. The
production clients are intended to match apart from their package names and
repository-specific infrastructure. Shared unit-test changes are also
recommended, while test layout and additional coverage may differ between the
repositories.

Integration tests are repository-specific. Both repositories test a live VM
Service WebSocket connection, while some protocol-specific live VM tests exist
only in the Dart plugin.
