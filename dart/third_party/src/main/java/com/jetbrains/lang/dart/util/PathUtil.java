package com.jetbrains.lang.dart.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import org.jetbrains.annotations.NotNull;

public class PathUtil {
    private PathUtil() {
    }

    public static boolean isAbsolutePlatformIndependent(@NotNull String path) {
        return (SystemInfo.isWindows && PathUtil.isWindowsAbsolutePath(path))
                || (!SystemInfo.isWindows && PathUtil.isUnixAbsolutePath(path));
    }

    /**
     * See documentation in Community's FileUtil.java, this method intentionally matches the deprecated version from the
     * platform.
     */
    public static boolean isUnixAbsolutePath(@NotNull String path) {
        return path.startsWith("/");

    }

    /**
     * See documentation in Community's FileUtil.java, this method intentionally matches the deprecated version from the
     * platform.
     */
    public static boolean isWindowsAbsolutePath(@NotNull String path) {
        return path.length() <= 2 && OSAgnosticPathUtil.startsWithWindowsDrive(path)
                || OSAgnosticPathUtil.isAbsoluteDosPath(path);

    }
}
