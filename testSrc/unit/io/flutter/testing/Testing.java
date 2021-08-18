/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test utilities.
 */
public class Testing {

  private Testing() {
  }

  /**
   * Returns a "light" test fixture containing an empty project.
   *
   * <p>(No modules are allowed.)
   */
  public static IdeaProjectFixture makeEmptyProject() {
    return new IdeaProjectFixture(
      (x) -> IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture(), true);
  }

  /**
   * Creates a "heavy" test fixture containing a Project with an empty Module.
   */
  public static IdeaProjectFixture makeEmptyModule() {
    return new IdeaProjectFixture((String testClassName) -> {
      final TestFixtureBuilder<IdeaProjectTestFixture> builder =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(testClassName);
      builder.addModule(EmptyModuleFixtureBuilder.class);
      return builder.getFixture();
    }, true);
  }

  public static CodeInsightProjectFixture makeCodeInsightModule() {
    return new CodeInsightProjectFixture((x) -> {
      final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
      final IdeaProjectTestFixture light = factory.createLightFixtureBuilder().getFixture();
      return factory.createCodeInsightFixture(light);
    }, false);
  }

  @SuppressWarnings("RedundantTypeArguments")
  public static <T> T computeInWriteAction(ThrowableComputable<T, Exception> callback) throws Exception {
    return computeOnDispatchThread(() -> {
      final Application app = ApplicationManager.getApplication();
      return app.<T, Exception>runWriteAction(callback);
    });
  }

  public static void runInWriteAction(RunnableThatThrows callback) throws Exception {
    final ThrowableComputable<Object, Exception> action = () -> {
      callback.run();
      return null;
    };
    runOnDispatchThread(() -> ApplicationManager.getApplication().runWriteAction(action));
  }

  public static <T> T computeOnDispatchThread(ThrowableComputable<T, Exception> callback) throws Exception {
    final AtomicReference<T> result = new AtomicReference<>();
    runOnDispatchThread(() -> result.set(callback.compute()));
    return result.get();
  }

  public static void runOnDispatchThread(RunnableThatThrows callback) throws Exception {
    try {
      final AtomicReference<Exception> ex = new AtomicReference<>();
      assert (!SwingUtilities.isEventDispatchThread());

      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          callback.run();
        }
        catch (Exception e) {
          ex.set(e);
        }
        catch (AssertionError e) {
          // Sometimes, the SDK leak detector fires, but we do not care since we do not use Android or Java SDKs.
          if (!e.getMessage().startsWith("Leaked SDKs")) {
            throw e;
          }
        }
      });
      if (ex.get() != null) {
        throw ex.get();
      }
    }
    catch (InvocationTargetException e) {
      if (e.getCause() instanceof AssertionError) {
        throw (AssertionError)e.getCause();
      }
    }
  }

  public interface RunnableThatThrows {
    void run() throws Exception;
  }
}
