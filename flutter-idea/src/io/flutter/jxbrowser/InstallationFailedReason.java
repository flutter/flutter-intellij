package io.flutter.jxbrowser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstallationFailedReason {
  public final @NotNull FailureType failureType;
  public final @Nullable String detail;

  public InstallationFailedReason(@NotNull FailureType failureType) {
    this(failureType, null);
  }

  InstallationFailedReason(@NotNull FailureType failureType, @Nullable String detail) {
    this.failureType = failureType;
    this.detail = detail;
  }
}
