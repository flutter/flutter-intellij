/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;


import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Monitors the application library table to notify clients when Flutter SDK configuration changes.
 */
public class FlutterSdkManager {

  private static FlutterSdkManager INSTANCE;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private final LibraryTableListener myLibraryTableListener = new LibraryTableListener();
  private boolean isFlutterConfigured;

  private FlutterSdkManager() {
    listenForSdkChanges();
    // Cache initial state.
    isFlutterConfigured = isGlobalFlutterSdkSet();
  }

  public static FlutterSdkManager getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new FlutterSdkManager();
    }
    return INSTANCE;
  }

  private void listenForSdkChanges() {
    ApplicationLibraryTable.getApplicationTable().addListener(myLibraryTableListener);
  }

  public void addListener(@NotNull Listener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull Listener listener) {
    myDispatcher.removeListener(listener);
  }

  private static boolean isGlobalFlutterSdkSet() {
    return FlutterSdk.getGlobalFlutterSdk() != null;
  }

  /**
   * Listen for SDK configuration changes.
   */
  public interface Listener extends EventListener {

    /**
     * Fired when the Flutter global library is set.
     */
    void flutterSdkAdded();

    /**
     * Fired when the Flutter global library is removed.
     */
    void flutterSdkRemoved();
  }

  // Listens for changes in Flutter Library configuration state in the Library table.
  private final class LibraryTableListener implements LibraryTable.Listener {

    @Override
    public void afterLibraryAdded(Library newLibrary) {
      if (!isFlutterConfigured && isGlobalFlutterSdkSet()) {
          isFlutterConfigured = true;
          myDispatcher.getMulticaster().flutterSdkAdded();
      }
    }

    @Override
    public void afterLibraryRenamed(Library library) {
      // Since we key off name, test to be safe.
      checkForFlutterSdkRemoval();
    }

    private void checkForFlutterSdkRemoval() {
      if (isFlutterConfigured && !isGlobalFlutterSdkSet()) {
          isFlutterConfigured = false;
          myDispatcher.getMulticaster().flutterSdkRemoved();
      }
    }

    @Override
    public void beforeLibraryRemoved(Library library) {
      // Test after.
    }

    @Override
    public void afterLibraryRemoved(Library library) {
      checkForFlutterSdkRemoval();
    }

  }
}
