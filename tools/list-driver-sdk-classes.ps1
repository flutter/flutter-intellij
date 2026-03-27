<#
.SYNOPSIS
  Lists unique path prefixes under com/intellij/driver/sdk/ from the resolved driver-sdk JAR.

.DESCRIPTION
  Finds driver-sdk-*.jar under the Gradle caches for com.jetbrains.intellij.driver:driver-sdk,
  runs jar tf, and prints sorted unique directory prefixes (not every class file).

.PARAMETER GradleCacheRoot
  Root of Gradle caches. Default: $env:USERPROFILE\.gradle\caches\modules-2\files-2.1

.EXAMPLE
  .\tools\list-driver-sdk-classes.ps1
#>

param(
  [string]$GradleCacheRoot = (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1")
)

$ErrorActionPreference = "Stop"

$base = Join-Path $GradleCacheRoot "com.jetbrains.intellij.driver\driver-sdk"
if (-not (Test-Path $base)) {
  Write-Error "driver-sdk cache not found at $base. Run a Gradle integration resolve first (e.g. .\gradlew.bat integration --dry-run)."
}

$jar = Get-ChildItem -Path $base -Recurse -Filter "driver-sdk-*.jar" -File -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -notmatch '-sources\.jar$' -and $_.Name -notmatch '-javadoc\.jar$' } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) {
  Write-Error "No driver-sdk-*.jar under $base"
}

Write-Host "Using: $($jar.FullName)" -ForegroundColor Cyan

& jar tf $jar.FullName |
  Where-Object { $_ -match '^com/intellij/driver/sdk/' } |
  ForEach-Object {
    if ($_ -match '\.(class|kt)$') {
      $_ -replace '/[^/]+$', ''
    }
    else {
      $_ -replace '/$', ''
    }
  } |
  Sort-Object -Unique
