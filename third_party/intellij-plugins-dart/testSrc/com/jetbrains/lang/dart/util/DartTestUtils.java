package com.jetbrains.lang.dart.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.util.PathUtil;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.dart.DartPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.util.Collections;

// TODO(devoncarew): We should pare this class down, and move some of its functionality into FlutterTestUtils.
// From //intellij-plugins/Dart/testSrc/com/jetbrains/lang/dart/util/DartTestUtils.java
public class DartTestUtils {

  public static final String BASE_TEST_DATA_PATH = findTestDataPath();
  public static final String SDK_HOME_PATH = BASE_TEST_DATA_PATH + "/sdk";

  @SuppressWarnings("Duplicates")
  private static String findTestDataPath() {
    if (new File(PathManager.getHomePath() + "/contrib").isDirectory()) {
      // started from IntelliJ IDEA Ultimate project
      return FileUtil.toSystemIndependentName(PathManager.getHomePath() + "/contrib/Dart/testData");
    }

    final File f = new File("testData");
    if (f.isDirectory()) {
      // started from 'Dart-plugin' project
      return FileUtil.toSystemIndependentName(f.getAbsolutePath());
    }

    final String parentPath = PathUtil.getParentPath(PathManager.getHomePath());

    if (new File(parentPath + "/intellij-plugins").isDirectory()) {
      // started from IntelliJ IDEA Community Edition + Dart Plugin project
      return FileUtil.toSystemIndependentName(parentPath + "/intellij-plugins/Dart/testData");
    }

    if (new File(parentPath + "/contrib").isDirectory()) {
      // started from IntelliJ IDEA Community + Dart Plugin project
      return FileUtil.toSystemIndependentName(parentPath + "/contrib/Dart/testData");
    }

    return "";
  }

  @SuppressWarnings("Duplicates")
  public static void configureDartSdk(@NotNull final Module module, @NotNull final Disposable disposable, final boolean realSdk) {
    final String sdkHome;
    if (realSdk) {
      sdkHome = System.getProperty("dart.sdk");
      if (sdkHome == null) {
        Assert.fail("To run tests that use Dart Analysis Server you need to add '-Ddart.sdk=[real SDK home]' to the VM Options field of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
      if (!DartPlugin.isDartSdkHome(sdkHome)) {
        Assert.fail("Incorrect path to the Dart SDK (" + sdkHome + ") is set as '-Ddart.sdk' VM option of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
      VfsRootAccess.allowRootAccess(disposable, sdkHome);
      // Dart Analysis Server threads
      ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(),
                                             "ByteRequestSink.LinesWriterThread",
                                             "ByteResponseStream.LinesReaderThread",
                                             "RemoteAnalysisServerImpl watcher",
                                             "ServerErrorReaderThread",
                                             "ServerResponseReaderThread");
    }
    else {
      sdkHome = SDK_HOME_PATH;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      DartPlugin.ensureDartSdkConfigured(module.getProject(), sdkHome);
      DartPlugin.enableDartSdk(module);
    });

    Disposer.register(disposable, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (!module.isDisposed()) {
        DartPlugin.disableDartSdk(Collections.singletonList(module));
      }

      final ApplicationLibraryTable libraryTable = ApplicationLibraryTable.getApplicationTable();
      final Library library = libraryTable.getLibraryByName(DartSdk.DART_SDK_GLOBAL_LIB_NAME);
      if (library != null) {
        libraryTable.removeLibrary(library);
      }
    }));
  }
}
