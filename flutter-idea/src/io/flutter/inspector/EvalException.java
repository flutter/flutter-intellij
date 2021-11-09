/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

/**
 * An exception that can occur from calls to eval() from the Inspector codebase.
 */
public class EvalException extends Exception {
  public final String expression;
  public final String errorCode;
  public final String errorMessage;

  public EvalException(String expression, String errorCode, String errorMessage) {
    this.expression = expression;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  @Override
  public String toString() {
    return "expression=" + expression + ",errorCode=" + errorCode + ",errorMessage=" + errorMessage;
  }
}
