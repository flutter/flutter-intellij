/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npwOld;

import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import java.io.File;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Validation Result for Wizard Validations, contains a status and a message
 *
 * @deprecated Use {@link PathValidator} instead.
 */
public final class PathValidationResult {
  public static final PathValidationResult OK = new PathValidationResult(Status.OK, null, "any");

  private static final CharMatcher ILLEGAL_CHARACTER_MATCHER = CharMatcher.anyOf("[/\\\\?%*:|\"<>!;]");
  private static final Set<String> INVALID_WINDOWS_FILENAMES = ImmutableSet
    .of("con", "prn", "aux", "clock$", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
        "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "$mft", "$mftmirr", "$logfile", "$volume", "$attrdef", "$bitmap", "$boot",
        "$badclus", "$secure", "$upcase", "$extend", "$quota", "$objid", "$reparse");
  private static final int WINDOWS_PATH_LENGTH_LIMIT = 100;

  private final Status myStatus;
  private final Message myMessage;
  private final Object[] myMessageParams;

  public static PathValidationResult warn(@NotNull Message message, String field, Object... params) {
    return new PathValidationResult(Status.WARN, message, field, params);
  }

  public static PathValidationResult error(@NotNull Message message, String field, Object... params) {
    return new PathValidationResult(Status.ERROR, message, field, params);
  }

  /**
   * Will return {@link PathValidationResult#OK} if projectLocation is valid or
   * a {@link PathValidationResult} with error/warning information if not.
   */
  @NotNull
  public static PathValidationResult validateLocation(@Nullable String projectLocation) {
    return validateLocation(projectLocation, "project location", true);
  }

  @NotNull
  public static PathValidationResult validateLocation(@Nullable String projectLocation, @NotNull String fieldName, boolean checkEmpty) {
    return validateLocation(projectLocation, fieldName, checkEmpty, WritableCheckMode.NOT_WRITABLE_IS_ERROR);
  }

  @NotNull
  public static PathValidationResult validateLocation(@Nullable String projectLocation,
                                                  @NotNull String fieldName,
                                                  boolean checkEmpty,
                                                  @NotNull WritableCheckMode writableCheckMode) {
    PathValidationResult warningResult = null;
    if (projectLocation == null || projectLocation.isEmpty()) {
      return error(Message.NO_LOCATION_SPECIFIED, fieldName);
    }
    // Check the separators
    if ((File.separatorChar == '/' && projectLocation.contains("\\")) || (File.separatorChar == '\\' && projectLocation.contains("/"))) {
      return error(Message.BAD_SLASHES, fieldName);
    }
    // Check the individual components for not allowed characters.
    File testFile = new File(projectLocation);
    while (testFile != null) {
      String filename = testFile.getName();
      if (ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(filename)) {
        char illegalChar = filename.charAt(ILLEGAL_CHARACTER_MATCHER.indexIn(filename));
        return error(Message.ILLEGAL_CHARACTER, fieldName, illegalChar, filename);
      }
      if (INVALID_WINDOWS_FILENAMES.contains(filename.toLowerCase(Locale.US))) {
        Status status = SystemInfo.isWindows ? Status.ERROR : Status.WARN;
        return new PathValidationResult(status, Message.ILLEGAL_FILENAME, fieldName, filename);
      }
      if (CharMatcher.WHITESPACE.matchesAnyOf(filename)) {
        warningResult = warn(Message.WHITESPACE, fieldName);
      }
      if (!CharMatcher.ASCII.matchesAllOf(filename)) {
        if (SystemInfo.isWindows) {
          return error(Message.NON_ASCII_CHARS_ERROR, fieldName);
        }
        else {
          warningResult = warn(Message.NON_ASCII_CHARS_WARNING, fieldName);
        }
      }
      // Check that we can write to that location: make sure we can write into the first extant directory in the path.
      File parent = testFile.getParentFile();

      if (!writableCheckMode.equals(WritableCheckMode.DO_NOT_CHECK) &&
          !testFile.exists() &&
          parent != null &&
          parent.exists() &&
          !parent.canWrite()) {
        // TODO Passing NOT_WRITABLE_IS_ERROR here is a hack. Stop depending on this code and use PathValidator.
        return pathNotWritable(WritableCheckMode.NOT_WRITABLE_IS_ERROR, fieldName, parent);
      }

      testFile = parent;
    }

    if (SystemInfo.isWindows && projectLocation.length() > WINDOWS_PATH_LENGTH_LIMIT) {
      return error(Message.PATH_TOO_LONG, fieldName);
    }

    File file = new File(projectLocation);
    if (file.isFile()) {
      return error(Message.PROJECT_LOC_IS_FILE, fieldName);
    }
    if (file.getParent() == null) {
      return error(Message.PROJECT_IS_FILE_SYSTEM_ROOT, fieldName);
    }
    if (file.getParentFile().exists() && !file.getParentFile().isDirectory()) {
      return error(Message.PARENT_NOT_DIR, fieldName);
    }

    if (!writableCheckMode.equals(WritableCheckMode.DO_NOT_CHECK) && file.exists() && !file.canWrite()) {
      return pathNotWritable(writableCheckMode, fieldName, file);
    }

    String installLocation = PathManager.getHomePathFor(Application.class);
    if (installLocation != null && FileUtil.isAncestor(new File(installLocation), file, false)) {
      String applicationName = ApplicationNamesInfo.getInstance().getProductName();
      return error(Message.INSIDE_ANDROID_STUDIO, fieldName, applicationName);
    }

    if (checkEmpty && file.exists() && FileOpUtils.create().listFiles(file).length > 0) {
      return warn(Message.NON_EMPTY_DIR, fieldName);
    }

    return (warningResult == null) ? OK : warningResult;
  }

  private PathValidationResult(@NotNull Status status, @Nullable Message message, @NotNull String field, Object... messageParams) {
    myStatus = status;
    myMessage = message;
    myMessageParams = ArrayUtil.prepend(field, messageParams);
  }

  @NotNull
  private static PathValidationResult pathNotWritable(@NotNull WritableCheckMode mode, @NotNull String field, @NotNull File file) {
    switch (mode) {
      case NOT_WRITABLE_IS_ERROR:
        return error(Message.PATH_NOT_WRITABLE, field, file.getPath());
      case NOT_WRITABLE_IS_WARNING:
        return warn(Message.PATH_NOT_WRITABLE, field, file.getPath());
      default:
        throw new IllegalArgumentException(mode.toString());
    }
  }

  public String getFormattedMessage() {
    if (myMessage == null) {
      throw new IllegalStateException("Null message, are you trying to get the message of an OK?");
    }
    return String.format(myMessage.toString(), myMessageParams);
  }

  @NotNull
  public Status getStatus() {
    return myStatus;
  }

  public boolean isError() {
    return myStatus.equals(Status.ERROR);
  }

  public boolean isOk() {
    return myStatus.equals(Status.OK);
  }

  public enum Status {
    OK, WARN, ERROR
  }

  public enum Message {
    NO_LOCATION_SPECIFIED("Please specify a %1$s"),
    BAD_SLASHES("Your %1$s contains incorrect slashes ('\\' vs '/')"),
    ILLEGAL_CHARACTER("Illegal character in %1$s path: '%2$c' in filename %3s"),
    ILLEGAL_FILENAME("Illegal filename in %1$s path: %2$s"),
    WHITESPACE("%1$s should not contain whitespace, as this can cause problems with the NDK tools."),
    NON_ASCII_CHARS_WARNING("Your %1$s contains non-ASCII characters, which can cause problems. Proceed with caution."),
    NON_ASCII_CHARS_ERROR("Your %1$s contains non-ASCII characters."),
    PATH_NOT_WRITABLE("The path '%2$s' is not writable. Please choose a new location."),
    PROJECT_LOC_IS_FILE("There must not already be a file at the %1$s."),
    NON_EMPTY_DIR("A non-empty directory already exists at the specified %1$s. Existing files may be overwritten. Proceed with caution."),
    PROJECT_IS_FILE_SYSTEM_ROOT("The %1$s can not be at the filesystem root"),
    PARENT_NOT_DIR("The %1$s's parent directory must be a directory, not a plain file"),
    INSIDE_ANDROID_STUDIO("The %1$s is inside %2$s install location"),
    PATH_TOO_LONG("The %1$s is too long");

    private final String myText;

    Message(final String text) {
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }
  }

  public enum WritableCheckMode {DO_NOT_CHECK, NOT_WRITABLE_IS_ERROR, NOT_WRITABLE_IS_WARNING}
}

