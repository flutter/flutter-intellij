package io.flutter.util;

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
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkGlobalLibUtil;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.util.Collections;

public class FlutterTestUtils {

  public static final String BASE_TEST_DATA_PATH = findTestDataPath();
  public static final String SDK_HOME_PATH = BASE_TEST_DATA_PATH + "/sdk";

  public static void configureDartSdk(@NotNull final Module module, @NotNull final Disposable disposable, final boolean realSdk) {
    final String sdkHome;
    if (realSdk) {
      sdkHome = System.getProperty("flutter.sdk");
      if (sdkHome == null) {
        Assert.fail("To run tests that use the flutter tools you need to add '-Dflutter.sdk=[real SDK home]' to the VM Options field of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
      if (!FlutterSdkUtil.isFlutterSdkHome(sdkHome)) {
        Assert.fail("Incorrect path to the Flutter SDK (" + sdkHome + ") is set as '-Dflutter.sdk' VM option of " +
                    "the corresponding JUnit run configuration (Run | Edit Configurations)");
      }
      VfsRootAccess.allowRootAccess(disposable, sdkHome);
      // Flutter execution threads
      ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(),
                                             "ByteRequestSink.LinesWriterThread",
                                             "ByteResponseStream.LinesReaderThread");
    }
    else {
      sdkHome = SDK_HOME_PATH;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      FlutterSdkGlobalLibUtil.ensureDartSdkConfigured(sdkHome);
      FlutterSdkGlobalLibUtil.enableDartSdk(module);
    });

    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (!module.isDisposed()) {
            FlutterSdkGlobalLibUtil.disableDartSdk(Collections.singletonList(module));
          }

          ApplicationLibraryTable libraryTable = ApplicationLibraryTable.getApplicationTable();
          final Library library = libraryTable.getLibraryByName(FlutterSdk.FLUTTER_SDK_GLOBAL_LIB_NAME);
          if (library != null) {
            libraryTable.removeLibrary(library);
          }
        });
      }
    });
  }

  private static String findTestDataPath() {
    if (new File(PathManager.getHomePath() + "/contrib").isDirectory()) {
      // started from IntelliJ IDEA Ultimate project
      return FileUtil.toSystemIndependentName(PathManager.getHomePath() + "/contrib/flutter-intellij/testData");
    }

    final File f = new File("testData");
    if (f.isDirectory()) {
      // started from 'Dart-plugin' project
      return FileUtil.toSystemIndependentName(f.getAbsolutePath());
    }

    final String parentPath = PathUtil.getParentPath(PathManager.getHomePath());

    if (new File(parentPath + "/intellij-plugins").isDirectory()) {
      // started from IntelliJ IDEA Community Edition + Dart Plugin project
      return FileUtil.toSystemIndependentName(parentPath + "/intellij-plugins/flutter-intellij/testData");
    }

    if (new File(parentPath + "/contrib").isDirectory()) {
      // started from IntelliJ IDEA Community + Dart Plugin project
      return FileUtil.toSystemIndependentName(parentPath + "/contrib/flutter-intellij/testData");
    }

    return "";
  }
}
