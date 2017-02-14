/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test utilities.
 */
public class Testing {

  private Testing() {}

  /**
   * Returns a "light" test fixture containing an empty project.
   *
   * <p>(No modules are allowed.)
   */
  public static ProjectFixture makeEmptyProject() {
    return new ProjectFixture(
      (x) -> IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture());
  }

  /**
   * Creates a "heavy" test fixture containing a Project with an empty Module.
   */
  public static ProjectFixture makeEmptyModule() {
    return new ProjectFixture((String testClassName) -> {
      final TestFixtureBuilder<IdeaProjectTestFixture> builder =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(testClassName);
      builder.addModule(EmptyModuleFixtureBuilder.class);
      return builder.getFixture();
    });
  }

  public static <T> T computeInWriteAction(ThrowableComputable<T, Exception> callback) throws Exception {
    return computeOnDispatchThread(() -> ApplicationManager.getApplication().<T, Exception>runWriteAction(callback));
  }

  public static void runInWriteAction(RunnableThatThrows callback) throws Exception {
    final ThrowableComputable<Object, Exception> action = () -> {
      callback.run();
      return null;
    };
    runOnDispatchThread(() -> ApplicationManager.getApplication().<Object, Exception>runWriteAction(action));
  }

  public static <T> T computeOnDispatchThread(ThrowableComputable<T,Exception> callback) throws Exception {
    final AtomicReference<T> result = new AtomicReference<>();
    runOnDispatchThread(() -> result.set(callback.compute()));
    return result.get();
  }

  public static void runOnDispatchThread(RunnableThatThrows callback) throws Exception {
    try {
      final AtomicReference<Exception> ex = new AtomicReference<>();
      SwingUtilities.invokeAndWait(() -> {
        try {
          callback.run();
        }
        catch (Exception e) {
          ex.set(e);
        }
      });
      if (ex.get() != null) {
        throw ex.get();
      }
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof AssertionError) {
        throw (AssertionError) e.getCause();
      }
    }
  }

  public interface RunnableThatThrows {
    void run() throws Exception;
  }
}
