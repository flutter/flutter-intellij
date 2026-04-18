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

    private static final @Nullable BuilderHooks builderHooks = findBuilderHooks();

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
        if (builderHooks == null) {
            return startLegacySessionAndGetDescriptor(manager, env, starter, muteBreakpoints);
        }

        return startSessionAndGetDescriptor(builderHooks, manager, env, starter, sessionName, muteBreakpoints);
    }

    @VisibleForTesting
    static @NotNull RunContentDescriptor startSessionAndGetDescriptor(
            @NotNull BuilderHooks hooks,
            @NotNull Object manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            @NotNull String sessionName,
            boolean muteBreakpoints) throws ExecutionException {
        try {
            final Object sessionResult = startBuilderSession(hooks, manager, env, starter, sessionName);
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

    private static @Nullable BuilderHooks findBuilderHooks() {
        Method sn = null;
        Method ctr = null;
        Method st = null;
        try {
            final Method nsb = XDebuggerManager.class.getMethod("newSessionBuilder", XDebugProcessStarter.class);
            final Class<?> builderClass = nsb.getReturnType();
            final Method env = builderClass.getMethod("environment", ExecutionEnvironment.class);
            final Method ss = builderClass.getMethod("startSession");
            try {
                sn = builderClass.getMethod("sessionName", String.class);
                ctr = builderClass.getMethod("contentToReuse", RunContentDescriptor.class);
                st = builderClass.getMethod("showTab", boolean.class);
            } catch (NoSuchMethodException e) {
                // Some newer SDKs may expose the builder without all of the tab configuration hooks.
            }
            return new BuilderHooks(nsb, env, sn, ctr, st, ss);
        } catch (NoSuchMethodException e) {
            // Fallback for older platforms
            return null;
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
            @NotNull BuilderHooks hooks,
            @NotNull Object manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            @NotNull String sessionName) throws Exception {
        Object builder = hooks.newSessionBuilderMethod.invoke(manager, starter);
        builder = hooks.environmentMethod.invoke(builder, env);
        builder = configureNamedTab(hooks, builder, env, sessionName);
        return hooks.startSessionMethod.invoke(builder);
    }

    private static @NotNull Object configureNamedTab(
            @NotNull BuilderHooks hooks,
            @NotNull Object builder,
            @NotNull ExecutionEnvironment env,
            @NotNull String sessionName) throws Exception {
        // Split debugger builds derive the visible tab title from the builder configuration rather than later descriptor mutation.
        if (hooks.contentToReuseMethod != null) {
            builder = hooks.contentToReuseMethod.invoke(builder, env.getContentToReuse());
        }
        if (hooks.sessionNameMethod != null) {
            builder = hooks.sessionNameMethod.invoke(builder, sessionName);
        }
        if (hooks.showTabMethod != null) {
            builder = hooks.showTabMethod.invoke(builder, true);
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
        if (builderHooks == null) {
            return null;
        }
        if (builderHooks.environmentMethod == null) {
            return "environment not found on XDebugSessionBuilder";
        }
        if (builderHooks.startSessionMethod == null) {
            return "startSession not found on XDebugSessionBuilder";
        }
        if (builderHooks.sessionNameMethod == null) {
            return "sessionName not found on XDebugSessionBuilder";
        }
        if (builderHooks.showTabMethod == null) {
            return "showTab not found on XDebugSessionBuilder";
        }
        return null;
    }

    @VisibleForTesting
    static final class BuilderHooks {
        final Method newSessionBuilderMethod;
        final Method environmentMethod;
        final @Nullable Method sessionNameMethod;
        final @Nullable Method contentToReuseMethod;
        final @Nullable Method showTabMethod;
        final Method startSessionMethod;

        BuilderHooks(
                @NotNull Method newSessionBuilderMethod,
                @NotNull Method environmentMethod,
                @Nullable Method sessionNameMethod,
                @Nullable Method contentToReuseMethod,
                @Nullable Method showTabMethod,
                @NotNull Method startSessionMethod) {
            this.newSessionBuilderMethod = newSessionBuilderMethod;
            this.environmentMethod = environmentMethod;
            this.sessionNameMethod = sessionNameMethod;
            this.contentToReuseMethod = contentToReuseMethod;
            this.showTabMethod = showTabMethod;
            this.startSessionMethod = startSessionMethod;
        }
    }
}
