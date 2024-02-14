package io.flutter.jxbrowser;

public class InstallationFailedReason {
    public FailureType failureType;
    public String detail;

    public InstallationFailedReason(FailureType failureType) {
        this(failureType, null);
    }

    InstallationFailedReason(FailureType failureType, String detail) {
        this.failureType = failureType;
        this.detail = detail;
    }
}
