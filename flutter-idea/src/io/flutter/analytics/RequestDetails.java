package io.flutter.analytics;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class RequestDetails {
  private final String method;
  private final Instant startTime;

  public RequestDetails(@NotNull String method, @NotNull Instant startTime) {
    this.method = method;
    this.startTime = startTime;
  }

  @NotNull
  public String method() {
    return method;
  }

  @NotNull
  public Instant startTime() {
    return startTime;
  }

  @NotNull
  public String toString() {
    return method + ": " + startTime.toEpochMilli();
  }
}
