package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class FlutterDebugSessionUtils {

    public static RunContentDescriptor startSessionAndGetDescriptor(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            boolean muteBreakpoints) throws ExecutionException {
        try {
            // Try to find the newSessionBuilder method (introduced in 2025.2+)
            Method newSessionBuilderMethod = XDebuggerManager.class.getMethod("newSessionBuilder", XDebugProcessStarter.class);
            Object builder = newSessionBuilderMethod.invoke(manager, starter);

            // builder.environment(env)
            Method environmentMethod = builder.getClass().getMethod("environment", ExecutionEnvironment.class);
            builder = environmentMethod.invoke(builder, env);

            // builder.startSession() -> returns XSessionStartedResult
            Method startSessionMethod = builder.getClass().getMethod("startSession");
            Object sessionResult = startSessionMethod.invoke(builder);

            if (muteBreakpoints) {
                Method getSessionMethod = sessionResult.getClass().getMethod("getSession");
                XDebugSession session = (XDebugSession) getSessionMethod.invoke(sessionResult);
                session.setBreakpointMuted(true);
            }

            // sessionResult.getRunContentDescriptor()
            Method getDescriptorMethod = sessionResult.getClass().getMethod("getRunContentDescriptor");
            return (RunContentDescriptor) getDescriptorMethod.invoke(sessionResult);

        } catch (NoSuchMethodException e) {
            // Fallback to old API for 2025.1 and older
            XDebugSession session = manager.startSession(env, starter);
            if (muteBreakpoints) {
                session.setBreakpointMuted(true);
            }
            return session.getRunContentDescriptor();
        } catch (Exception e) {
            throw new ExecutionException("Failed to start debug session via reflection", e);
        }
    }

    public static XDebugSession startSession(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter) throws ExecutionException {
        try {
            Method newSessionBuilderMethod = XDebuggerManager.class.getMethod("newSessionBuilder", XDebugProcessStarter.class);
            Object builder = newSessionBuilderMethod.invoke(manager, starter);

            Method environmentMethod = builder.getClass().getMethod("environment", ExecutionEnvironment.class);
            builder = environmentMethod.invoke(builder, env);

            Method startSessionMethod = builder.getClass().getMethod("startSession");
            Object sessionResult = startSessionMethod.invoke(builder); // This is XSessionStartedResult

            // Now get the session from the result
            Method getSessionMethod = sessionResult.getClass().getMethod("getSession");
            return (XDebugSession) getSessionMethod.invoke(sessionResult);

        } catch (NoSuchMethodException e) {
            // Fallback to old API
            return manager.startSession(env, starter);
        } catch (Exception e) {
            throw new ExecutionException("Failed to start debug session via reflection", e);
        }
    }
}
