/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// TODO(helin24): This class is using reflection to find experimental APIs that are only present in platform versions 2025.3+. We should be
//  able to use the APIs directly once we are only supporting versions past 2025.3.
//  See https://github.com/flutter/flutter-intellij/issues/8879.
public class FlutterDebugSessionUtils {

    private static final Method newSessionBuilderMethod;
    private static final Method environmentMethod;
    private static final Method sessionNameMethod;
    private static final Method contentToReuseMethod;
    private static final Method showTabMethod;
    private static final Method startSessionMethod;

    static {
        Method nsb = null;
        Method env = null;
        Method sn = null;
        Method ctr = null;
        Method st = null;
        Method ss = null;
        try {
            final Method nsbLocal = XDebuggerManager.class.getMethod("newSessionBuilder", XDebugProcessStarter.class);
            final Class<?> builderClass = nsbLocal.getReturnType();
            final Method envLocal = builderClass.getMethod("environment", ExecutionEnvironment.class);
            final Method ssLocal = builderClass.getMethod("startSession");
            try {
                sn = builderClass.getMethod("sessionName", String.class);
                ctr = builderClass.getMethod("contentToReuse", RunContentDescriptor.class);
                st = builderClass.getMethod("showTab", boolean.class);
            } catch (NoSuchMethodException e) {
                // Some newer SDKs may expose the builder without all of the tab configuration hooks.
            }
            nsb = nsbLocal;
            env = envLocal;
            ss = ssLocal;
        } catch (NoSuchMethodException e) {
            // Fallback for older platforms
        }
        newSessionBuilderMethod = nsb;
        environmentMethod = env;
        sessionNameMethod = sn;
        contentToReuseMethod = ctr;
        showTabMethod = st;
        startSessionMethod = ss;
    }

    public static @NotNull RunContentDescriptor startSessionAndGetDescriptor(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            boolean muteBreakpoints) throws ExecutionException {
        return startSessionAndGetDescriptor(manager, env, starter, env.getRunProfile().getName(), muteBreakpoints);
    }

    public static @NotNull RunContentDescriptor startSessionAndGetDescriptor(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            @NotNull String sessionName,
            boolean muteBreakpoints) throws ExecutionException {
        if (newSessionBuilderMethod == null) {
            return startLegacySessionAndGetDescriptor(manager, env, starter, muteBreakpoints);
        }

        try {
            final Object sessionResult = startBuilderSession(manager, env, starter, sessionName);
            muteBreakpointsIfNeeded(sessionResult, muteBreakpoints);
            return getDescriptor(sessionResult);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExecutionException) {
                throw (ExecutionException) cause;
            }
            throw new ExecutionException("Failed to start debug session via reflection", cause != null ? cause : e);
        } catch (Exception e) {
            throw new ExecutionException("Failed with unexpected reflection error", e);
        }
    }

    private static @NotNull RunContentDescriptor startLegacySessionAndGetDescriptor(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            boolean muteBreakpoints) throws ExecutionException {
        final XDebugSession session = manager.startSession(env, starter);
        if (muteBreakpoints) {
            session.setBreakpointMuted(true);
        }
        return session.getRunContentDescriptor();
    }

    private static @NotNull Object startBuilderSession(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            @NotNull String sessionName) throws Exception {
        Object builder = newSessionBuilderMethod.invoke(manager, starter);
        builder = environmentMethod.invoke(builder, env);
        builder = configureNamedTab(builder, env, sessionName);
        return startSessionMethod.invoke(builder);
    }

    private static @NotNull Object configureNamedTab(
            @NotNull Object builder,
            @NotNull ExecutionEnvironment env,
            @NotNull String sessionName) throws Exception {
        // Split debugger builds derive the visible tab title from the builder configuration rather than later descriptor mutation.
        if (contentToReuseMethod != null) {
            builder = contentToReuseMethod.invoke(builder, env.getContentToReuse());
        }
        if (sessionNameMethod != null) {
            builder = sessionNameMethod.invoke(builder, sessionName);
        }
        if (showTabMethod != null) {
            builder = showTabMethod.invoke(builder, true);
        }
        return builder;
    }

    private static void muteBreakpointsIfNeeded(@NotNull Object sessionResult, boolean muteBreakpoints) throws Exception {
        if (!muteBreakpoints) {
            return;
        }
        final Method getSessionMethod = sessionResult.getClass().getMethod("getSession");
        final XDebugSession session = (XDebugSession) getSessionMethod.invoke(sessionResult);
        session.setBreakpointMuted(true);
    }

    private static @NotNull RunContentDescriptor getDescriptor(@NotNull Object sessionResult) throws Exception {
        final Method getDescriptorMethod = sessionResult.getClass().getMethod("getRunContentDescriptor");
        return (RunContentDescriptor) getDescriptorMethod.invoke(sessionResult);
    }

    /**
     * Returns null when the current SDK exposes the reflective builder hooks needed to label split debug tabs.
     */
    @VisibleForTesting
    static @Nullable String getNamedTabSupportError() {
        if (newSessionBuilderMethod == null) {
            return null;
        }
        if (environmentMethod == null) {
            return "environment not found on XDebugSessionBuilder";
        }
        if (startSessionMethod == null) {
            return "startSession not found on XDebugSessionBuilder";
        }
        if (sessionNameMethod == null) {
            return "sessionName not found on XDebugSessionBuilder";
        }
        if (showTabMethod == null) {
            return "showTab not found on XDebugSessionBuilder";
        }
        return null;
    }
}
