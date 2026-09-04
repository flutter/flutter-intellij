For information about how to profile IntelliJ for performance issues, see https://intellij-support.jetbrains.com/hc/en-us/articles/207241235-Reporting-performance-problems#capture_slow_startup_2019_2.

In summary:
- install the 'Performance Testing' plugin
- restart IntelliJ
- get set up to repro the issue
- from the `Help > Diagnostics` menu, select `Start CPU Usage Profiling`
- repro the issue
- from the `Help > Diagnostics` menu, select `Stop CPU Usage Profiling`
- this will save a zip file with a performance snapshot

You can them attach that zip file to bug reports, or open it yourself in JetBrains' `YourKit Java Profiler` tool.