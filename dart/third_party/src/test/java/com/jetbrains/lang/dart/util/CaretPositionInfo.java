package com.jetbrains.lang.dart.util;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Store information about a caret position.
 * <p>
 * This class is used to store information about a caret position in a test file, such as the expected completion list.
 */
public class CaretPositionInfo {

  public final int caretOffset;
  @Nullable public final String expected;
  @Nullable public final List<String> completionEqualsList;
  @Nullable public final List<String> completionIncludesList;
  @Nullable public final List<String> completionExcludesList;

  public CaretPositionInfo(final int caretOffset,
                           @Nullable final String expected,
                           @Nullable final List<String> completionEqualsList,
                           @Nullable final List<String> completionIncludesList,
                           @Nullable final List<String> completionExcludesList) {
    this.caretOffset = caretOffset;
    this.expected = expected;
    this.completionEqualsList = completionEqualsList;
    this.completionIncludesList = completionIncludesList;
    this.completionExcludesList = completionExcludesList;
  }
}
