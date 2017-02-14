/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.testFramework.fixtures.IdeaTestFixture;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Wraps an IDEA fixture so it works as a Junit 4 Rule.
 *
 * <p>(That is, it can be used with the @Rule annotation.)
 */
public class AdaptedFixture<T extends IdeaTestFixture> implements TestRule {
  public final Factory<T> factory;
  private T inner;

  AdaptedFixture(Factory<T> factory) {
    this.factory = factory;
  }

  public T getInner() {
    return inner;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        inner = factory.create(description.getClassName());
        Testing.runOnDispatchThread(inner::setUp);
        try {
          base.evaluate();
        } finally {
          Testing.runOnDispatchThread(inner::tearDown);
          inner = null;
        }
      }
    };
  }

  public interface Factory<T extends IdeaTestFixture> {
    T create(String testClassName);
  }
}
