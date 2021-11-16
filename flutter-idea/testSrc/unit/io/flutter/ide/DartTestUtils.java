/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.SmartList;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import io.flutter.sdk.FlutterSdkUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.util.List;
import java.util.Objects;

/**
 * Adapted from similar class in the Dart plugin.
 */
public class DartTestUtils {

  public static final String BASE_TEST_DATA_PATH = findTestDataPath();
  public static final String SDK_HOME_PATH = BASE_TEST_DATA_PATH + "/sdk";

  @TestOnly
  public static void configureFlutterSdk(@NotNull final Module module, @NotNull final Disposable disposable, final boolean realSdk) {
    final String sdkHome;
    if (realSdk) {
      sdkHome = System.getProperty("flutter.sdk");
      if (sdkHome == null) {
        Assert.fail(
          "To run tests that use Dart Analysis Server you need to add '-Dflutter.sdk=[real SDK home]' to the VM Options field of " +
          "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
      if (!FlutterSdkUtil.isFlutterSdkHome(sdkHome)) {
        Assert.fail("Incorrect path to the Flutter SDK (" + sdkHome + ") is set as '-Dflutter.sdk' VM option of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
    }
    else {
      sdkHome = SDK_HOME_PATH;
    }

    VfsRootAccess.allowRootAccess(disposable, sdkHome);
    //final String dartSdkHome = sdkHome + "bin/cache/dart-sdk";
    //VfsRootAccess.allowRootAccess(disposable, dartSdkHome);

    //noinspection ConstantConditions
    //ApplicationManager.getApplication().runWriteAction(() -> {
    //  Disposer.register(disposable, DartSdkLibUtil.configureDartSdkAndReturnUndoingDisposable(module.getProject(), dartSdkHome));
    //  Disposer.register(disposable, DartSdkLibUtil.enableDartSdkAndReturnUndoingDisposable(module));
    //});
  }

  /**
   * Use this method in finally{} clause if the test modifies excluded roots or configures module libraries
   */
  public static void resetModuleRoots(@NotNull final Module module) {
    //noinspection ConstantConditions
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel modifiableModel = Objects.requireNonNull(ModuleRootManager.getInstance(module)).getModifiableModel();

      try {
        final List<OrderEntry> entriesToRemove = new SmartList<>();

        for (OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
          if (orderEntry instanceof LibraryOrderEntry) {
            entriesToRemove.add(orderEntry);
          }
        }

        for (OrderEntry orderEntry : entriesToRemove) {
          assert orderEntry != null;
          modifiableModel.removeOrderEntry(orderEntry);
        }

        final ContentEntry[] contentEntries = modifiableModel.getContentEntries();
        TestCase.assertEquals("Expected one content root, got: " + contentEntries.length, 1, contentEntries.length);

        final ContentEntry oldContentEntry = contentEntries[0];
        assert oldContentEntry != null;
        if (oldContentEntry.getSourceFolders().length != 1 || oldContentEntry.getExcludeFolderUrls().size() > 0) {
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

  /**
   * Creates the syntax tree for a Dart file at a specific path and returns the innermost element with the given text.
   */
  @NotNull
  public static <E extends PsiElement> E setUpDartElement(@Nullable String filePath,
                                                          @NotNull String fileText,
                                                          @NotNull String elementText,
                                                          @NotNull Class<E> expectedClass,
                                                          @NotNull Project project) {
    final int offset = fileText.indexOf(elementText);
    if (offset < 0) {
      throw new IllegalArgumentException("'" + elementText + "' not found in '" + fileText + "'");
    }

    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    assert factory != null;
    final PsiFile file;
    if (filePath != null) {
      file = factory.createFileFromText(filePath, DartLanguage.INSTANCE, fileText);
    }
    else {
      file = factory.createFileFromText(DartLanguage.INSTANCE, fileText);
    }

    assert file != null;
    PsiElement elt = file.findElementAt(offset);
    while (elt != null) {
      if (elementText.equals(elt.getText())) {
        return expectedClass.cast(elt);
      }
      elt = elt.getParent();
    }

    throw new RuntimeException("unable to find element with text: " + elementText);
  }

  private static String findTestDataPath() {
    return "testData/sdk";
  }
}
