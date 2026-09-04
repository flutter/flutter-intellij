// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for Dart tests.
 * <p>
 * This class provides utility methods for Dart tests, such as configuring the Dart SDK and extracting position markers from test files.
 */
public final class DartTestUtils {

  public static final String BASE_TEST_DATA_PATH = findTestDataPath();
  public static final String SDK_HOME_PATH = BASE_TEST_DATA_PATH + "/sdk";

  private static String findTestDataPath() {
    if (new File(PathManager.getHomePath() + "/contrib").isDirectory()) {
      // started from IntelliJ IDEA Ultimate project
      return FileUtil.toSystemIndependentName(PathManager.getHomePath() + "/contrib/Dart/testData");
    }

    final File f = new File("src/test/testData");
    if (f.isDirectory()) {
      // started from 'Dart-plugin' project
      return FileUtil.toSystemIndependentName(f.getAbsolutePath());
    }

    final String parentPath = PathUtil.getParentPath(PathManager.getHomePath());

    if (new File(parentPath + "/dart-intellij-third-party").isDirectory()) {
      // started from IntelliJ IDEA Community Edition + Dart Plugin project
      return FileUtil.toSystemIndependentName(parentPath + "/dart-intellij-third-party/third_party/src/test/testData");
    }

    if (new File(parentPath + "/contrib").isDirectory()) {
      // started from IntelliJ IDEA Community + Dart Plugin project
      return FileUtil.toSystemIndependentName(parentPath + "/contrib/Dart/testData");
    }

    return "";
  }

  @TestOnly
  public static void configureDartSdk(@NotNull final Module module, @NotNull final Disposable disposable, final boolean realSdk) {
    final String sdkHome;
    String tempSdk;

    if (realSdk) {
      tempSdk = System.getProperty("dart.sdk");

      // getting the dart.sdk from System.getProperty() was failing in many cases on Windows,
      // inexplicably? However, since the dart.sdk is an environment variable it makes sense that
      // getenv works here
      //
      if (tempSdk == null) {
        // try to get from System.getEnv
       tempSdk = System.getenv("dart.sdk");
      }

      sdkHome = tempSdk;

      if (sdkHome == null) {
        Assert.fail("To run tests that use Dart Analysis Server you need to add '-Ddart.sdk=[real SDK home]' to the VM Options field of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
      if (!DartSdkUtil.isDartSdkHome(sdkHome)) {
        Assert.fail("Incorrect path to the Dart SDK (" + sdkHome + ") is set as '-Ddart.sdk' VM option of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
    }
    else {
      sdkHome = SDK_HOME_PATH;
    }

    VfsRootAccess.allowRootAccess(disposable, sdkHome);

    // The above root access doesn't always work properly, the line below
    // sets access to anything from src/test/testData and below
    //
    VfsRootAccess.allowRootAccess(disposable, BASE_TEST_DATA_PATH);

    DartConfigurable.setExperimentalLspFeaturesEnabled(module.getProject(), false);

    ApplicationManager.getApplication().runWriteAction(() -> {
      Disposer.register(disposable, DartSdkLibUtil.configureDartSdkAndReturnUndoingDisposable(module.getProject(), sdkHome));
      Disposer.register(disposable, DartSdkLibUtil.enableDartSdkAndReturnUndoingDisposable(module));
    });
    IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
    if (realSdk) {
      ApplicationManager.getApplication().runReadAction(() -> {
        DartAnalysisServerService.getInstance(module.getProject()).serverReadyForRequest();
      });
    }
  }

  public static List<CaretPositionInfo> extractPositionMarkers(@NotNull final Project project, @NotNull final Document document) {
    final Pattern caretPattern = Pattern.compile(
      "<caret(?: expected='([^']*)')?(?: completionEquals='([^']*)')?(?: completionIncludes='([^']*)')?(?: completionExcludes='([^']*)')?>");
    final List<CaretPositionInfo> result = new SmartList<>();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      while (true) {
        Matcher m = caretPattern.matcher(document.getImmutableCharSequence());
        if (m.find()) {
          document.deleteString(m.start(), m.end());

          final int caretOffset = m.start();

          final String expected = m.group(1);

          final String completionEqualsRaw = m.group(2);
          final List<String> completionEqualsList = completionEqualsRaw == null ? null : StringUtil.split(completionEqualsRaw, ",");

          final String completionIncludesRaw = m.group(3);
          final List<String> completionIncludesList = completionIncludesRaw == null ? null : StringUtil.split(completionIncludesRaw, ",");

          final String completionExcludesRaw = m.group(4);
          final List<String> completionExcludesList = completionExcludesRaw == null ? null : StringUtil.split(completionExcludesRaw, ",");

          result.add(new CaretPositionInfo(caretOffset, expected, completionEqualsList, completionIncludesList, completionExcludesList));
        }
        else {
          break;
        }
      }
    });

    if (!result.isEmpty()) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
    return result;
  }

  /**
   * Use this method in finally{} clause if the test modifies excluded roots or configures module libraries
   */
  public static void resetModuleRoots(@NotNull final Module module) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();

      try {
        final List<OrderEntry> entriesToRemove = new SmartList<>();

        for (OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
          if (orderEntry instanceof LibraryOrderEntry) {
            entriesToRemove.add(orderEntry);
          }
        }

        for (OrderEntry orderEntry : entriesToRemove) {
          modifiableModel.removeOrderEntry(orderEntry);
        }

        final ContentEntry[] contentEntries = modifiableModel.getContentEntries();
        TestCase.assertEquals("Expected one content root, got: " + contentEntries.length, 1, contentEntries.length);

        final ContentEntry oldContentEntry = contentEntries[0];
        if (oldContentEntry.getSourceFolders().length != 1 || !oldContentEntry.getExcludeFolderUrls().isEmpty()) {
          modifiableModel.removeContentEntry(oldContentEntry);
          final ContentEntry newContentEntry = modifiableModel.addContentEntry(oldContentEntry.getUrl());
          newContentEntry.addSourceFolder(newContentEntry.getUrl(), false);
        }

        if (modifiableModel.isChanged()) {
          modifiableModel.commit();
        }
      }
      finally {
        if (!modifiableModel.isDisposed()) {
          modifiableModel.dispose();
        }
      }
    });
  }
}
