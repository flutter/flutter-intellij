package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// TODO(helin24): This class is using reflection to find experimental APIs that are only present in platform versions 2025.3+. We should be
//  able to use the APIs directly once we are only supporting versions past 2025.3.
public class FlutterDebugSessionUtils {

    private static final Method newSessionBuilderMethod;
    private static final Method environmentMethod;
    private static final Method startSessionMethod;

    static {
        Method nsb = null;
        Method env = null;
        Method ss = null;
        try {
            nsb = XDebuggerManager.class.getMethod("newSessionBuilder", XDebugProcessStarter.class);
            Class<?> builderClass = nsb.getReturnType();
            env = builderClass.getMethod("environment", ExecutionEnvironment.class);
            ss = builderClass.getMethod("startSession");
        } catch (NoSuchMethodException e) {
            // Fallback for older platforms
        }
        newSessionBuilderMethod = nsb;
        environmentMethod = env;
        startSessionMethod = ss;
    }

    public static @NotNull RunContentDescriptor startSessionAndGetDescriptor(
            @NotNull XDebuggerManager manager,
            @NotNull ExecutionEnvironment env,
            @NotNull XDebugProcessStarter starter,
            boolean muteBreakpoints) throws ExecutionException {
        try {
            if (newSessionBuilderMethod == null) {
                throw new NoSuchMethodException("newSessionBuilder is not available");
            }
            Object builder = newSessionBuilderMethod.invoke(manager, starter);
            builder = environmentMethod.invoke(builder, env);
            Object sessionResult = startSessionMethod.invoke(builder);

            if (muteBreakpoints) {
                Method getSessionMethod = sessionResult.getClass().getMethod("getSession");
                XDebugSession session = (XDebugSession) getSessionMethod.invoke(sessionResult);
                session.setBreakpointMuted(true);
            }

            Method getDescriptorMethod = sessionResult.getClass().getMethod("getRunContentDescriptor");
            return (RunContentDescriptor) getDescriptorMethod.invoke(sessionResult);

        } catch (NoSuchMethodException e) {
            // Fallback to old API for 2025.1 and older
            XDebugSession session = manager.startSession(env, starter);
            if (muteBreakpoints) {
                session.setBreakpointMuted(true);
            }
            return session.getRunContentDescriptor();
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
}
