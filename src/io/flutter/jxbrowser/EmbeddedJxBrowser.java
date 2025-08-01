/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.ContentManager;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException;
import com.teamdev.jxbrowser.browser.callback.AlertCallback;
import com.teamdev.jxbrowser.browser.callback.ConfirmCallback;
import com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.js.ConsoleMessage;
import com.teamdev.jxbrowser.ui.KeyCode;
import com.teamdev.jxbrowser.ui.event.KeyPressed;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import com.teamdev.jxbrowser.view.swing.callback.DefaultAlertCallback;
import com.teamdev.jxbrowser.view.swing.callback.DefaultConfirmCallback;
import io.flutter.logging.PluginLogger;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.JxBrowserUtils;
import io.flutter.utils.ZoomLevelSelector;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.EmbeddedTab;
import io.flutter.utils.LabelInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.teamdev.jxbrowser.zoom.Zoom;
import com.teamdev.jxbrowser.zoom.ZoomLevel;
import com.intellij.ide.ui.UISettingsUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

class EmbeddedJxBrowserTab implements EmbeddedTab {
  private final Engine engine;
  private Browser browser;
  private Zoom zoom;
  private final ZoomLevelSelector zoomSelector = new ZoomLevelSelector();
  private static final @NotNull Logger LOG = Logger.getInstance(EmbeddedJxBrowserTab.class);

  public EmbeddedJxBrowserTab(Engine engine) {
    this.engine = engine;

    try {
      this.browser = engine.newBrowser();
      this.zoom = this.browser.zoom();
      this.browser.settings().enableTransparentBackground();
      this.browser.on(ConsoleMessageReceived.class, event -> {
        final ConsoleMessage consoleMessage = event.consoleMessage();
        LOG.info("Browser message(" + consoleMessage.level().name() + "): " + consoleMessage.message());
      });
    }
    catch (UnsupportedRenderingModeException ex) {
      // Skip using a transparent background if an exception is thrown.
    }
    catch (Exception | Error ex) {
      LOG.info(ex);
    }
  }

  @Override
  public void loadUrl(String url) {
    this.browser.navigation().loadUrl(url);
  }

  @Override
  public void close() {
    this.browser.close();
  }

  @Override
  public void matchIdeZoom() {
    if (this.zoom != null) {
      final ZoomLevel zoomLevel = zoomSelector.getClosestZoomLevel(getIdeZoomPercent());
      this.zoom.level(zoomLevel);
    }
  }

  private int getIdeZoomPercent() {
    final UISettingsUtils uiSettingsUtils = UISettingsUtils.getInstance();
    final float ideScale = uiSettingsUtils.getCurrentIdeScale();
    return Math.round(ideScale * 100);
  }

  @Override
  public JComponent getTabComponent(ContentManager contentManager) {
    // Creating Swing component for rendering web content
    // loaded in the given Browser instance.
    final BrowserView view = BrowserView.newInstance(browser);
    view.setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));

    // DevTools may show a confirm dialog to use a fallback version.
    browser.set(ConfirmCallback.class, new DefaultConfirmCallback(view));
    browser.set(AlertCallback.class, new DefaultAlertCallback(view));

    // This is for pulling up Chrome inspector for debugging purposes.
    browser.set(PressKeyCallback.class, params -> {
      KeyPressed keyEvent = params.event();
      boolean keyCodeC = keyEvent.keyCode() == KeyCode.KEY_CODE_J;
      boolean controlDown = keyEvent.keyModifiers().isControlDown();
      if (controlDown && keyCodeC) {
        browser.devTools().show();
      }
      return PressKeyCallback.Response.proceed();
    });

    return view;
  }
}

public class EmbeddedJxBrowser extends EmbeddedBrowser {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(JxBrowserManager.class);
  private static final String INSTALLATION_IN_PROGRESS_LABEL = "Installing JxBrowser...";
  private static final String INSTALLATION_TIMED_OUT_LABEL =
    "Waiting for JxBrowser installation timed out. Restart your IDE to try again.";
  private static final String INSTALLATION_WAIT_FAILED = "The JxBrowser installation failed unexpectedly. Restart your IDE to try again.";
  private static final int INSTALLATION_WAIT_LIMIT_SECONDS = 30;
  private final AtomicReference<Engine> engineRef = new AtomicReference<>(null);

  private final Project project;

  private final JxBrowserManager jxBrowserManager;
  private final JxBrowserUtils jxBrowserUtils;

  @NotNull
  public static EmbeddedJxBrowser getInstance(@NotNull Project project) {
    return Objects.requireNonNull(project.getService(EmbeddedJxBrowser.class));
  }

  private EmbeddedJxBrowser(@NotNull Project project) {
    super(project);
    this.project = project;

    this.jxBrowserManager = JxBrowserManager.getInstance();
    this.jxBrowserUtils = new JxBrowserUtils();
    final JxBrowserStatus jxBrowserStatus = jxBrowserManager.getStatus();

    if (jxBrowserStatus.equals(JxBrowserStatus.NOT_INSTALLED) || jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_SKIPPED)) {
      jxBrowserManager.setUp(project.getName());
    }

    System.setProperty("jxbrowser.force.dpi.awareness", "1.0");
    System.setProperty("jxbrowser.logging.level", "DEBUG");
    System.setProperty("jxbrowser.logging.file", PathManager.getLogPath() + File.separatorChar + "jxbrowser.log");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      System.setProperty("jxbrowser.logging.level", "ALL");
    }

    JxBrowserManager.installation.thenAccept((JxBrowserStatus status) -> {
      if (Objects.equals(status, JxBrowserStatus.INSTALLED)) {
        engineRef.compareAndSet(null, EmbeddedBrowserEngine.getInstance().getEngine());
      }
    });
  }

  @Override
  public @NotNull Logger logger() {
    return LOG;
  }

  @Override
  public @Nullable EmbeddedTab openEmbeddedTab(ContentManager contentManager) {
    manageJxBrowserDownload(contentManager);
    if (engineRef.get() == null) {
      engineRef.compareAndSet(null, EmbeddedBrowserEngine.getInstance().getEngine());
    }
    final Engine engine = engineRef.get();
    if (engine == null) {
      showMessageWithUrlLink(jxBrowserErrorMessage(), contentManager);
      return null;
    }
    else {
      return new EmbeddedJxBrowserTab(engine);
    }
  }

  private @NotNull String jxBrowserErrorMessage() {
    final String defaultError = "JX Browser engine failed to start";
    if (jxBrowserManager == null) {
      return defaultError;
    }
    switch (jxBrowserManager.getStatus()) {
      case NOT_INSTALLED:
        return "JX Browser is not installed";
      case INSTALLATION_IN_PROGRESS:
        return "JX Browser installation in progress";
      case INSTALLATION_SKIPPED:
        return "JX Browser installation skipped";
      case INSTALLATION_FAILED:
        final InstallationFailedReason failedReason = jxBrowserManager.getLatestFailureReason();
        final @Nullable String errorFromFailedMessage = jxBrowserErrorFromFailedReason(failedReason);
        return errorFromFailedMessage != null ? errorFromFailedMessage : defaultError;
      default:
        return defaultError;
    }
  }

  private @Nullable String jxBrowserErrorFromFailedReason(@Nullable InstallationFailedReason failedReason) {
    if (failedReason == null) return null;
    final FailureType failureType = failedReason.failureType;
    if (failureType == null) return null;
    return switch (failureType) {
      case SYSTEM_INCOMPATIBLE -> "System is incompatible with JX Browser";
      case FILE_DOWNLOAD_FAILED -> "JX Browser file download failed";
      case MISSING_KEY -> "JX Browser license key is missing";
      case DIRECTORY_CREATION_FAILED -> "JX Browser directory creation failed";
      case MISSING_PLATFORM_FILES -> "JX Browser platform files are missing";
      case CLASS_LOAD_FAILED -> "JX Browser class load failed";
      case CLASS_NOT_FOUND -> "JX Browser class not found";
    };
  }

  private void manageJxBrowserDownload(ContentManager contentManager) {
    final JxBrowserStatus jxBrowserStatus = jxBrowserManager.getStatus();

    if (jxBrowserStatus.equals(JxBrowserStatus.INSTALLED)) {
      return;
    }
    else if (jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_IN_PROGRESS)) {
      handleJxBrowserInstallationInProgress(contentManager);
    }
    else if (jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_FAILED)) {
      handleJxBrowserInstallationFailed(contentManager);
    }
    else if (jxBrowserStatus.equals(JxBrowserStatus.NOT_INSTALLED) || jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_SKIPPED)) {
      jxBrowserManager.setUp(project.getName());
      handleJxBrowserInstallationInProgress(contentManager);
    }
  }

  protected void handleJxBrowserInstallationInProgress(ContentManager contentManager) {
    showMessageWithUrlLink(INSTALLATION_IN_PROGRESS_LABEL, contentManager);

    if (jxBrowserManager.getStatus().equals(JxBrowserStatus.INSTALLED)) {
      return;
    }
    else {
      waitForJxBrowserInstallation(contentManager);
    }
  }

  protected void waitForJxBrowserInstallation(ContentManager contentManager) {
    try {
      final JxBrowserStatus newStatus = jxBrowserManager.waitForInstallation(INSTALLATION_WAIT_LIMIT_SECONDS);

      handleUpdatedJxBrowserStatusOnEventThread(newStatus, contentManager);
    }
    catch (TimeoutException e) {
      showMessageWithUrlLink(INSTALLATION_TIMED_OUT_LABEL, contentManager);
    }
  }

  protected void handleUpdatedJxBrowserStatusOnEventThread(JxBrowserStatus jxBrowserStatus, ContentManager contentManager) {
    AsyncUtils.invokeLater(() -> handleUpdatedJxBrowserStatus(jxBrowserStatus, contentManager));
  }

  protected void handleUpdatedJxBrowserStatus(JxBrowserStatus jxBrowserStatus, ContentManager contentManager) {
    if (Objects.equals(jxBrowserStatus, JxBrowserStatus.INSTALLED)) {
      return;
    }
    else if (jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_FAILED)) {
      handleJxBrowserInstallationFailed(contentManager);
    }
    else {
      // newStatus can be null if installation is interrupted or stopped for another reason.
      showMessageWithUrlLink(INSTALLATION_WAIT_FAILED, contentManager);
    }
  }

  protected void handleJxBrowserInstallationFailed(ContentManager contentManager) {
    final List<LabelInput> inputs = new ArrayList<>();

    final InstallationFailedReason latestFailureReason = jxBrowserManager.getLatestFailureReason();

    if (!jxBrowserUtils.licenseIsSet()) {
      // If the license isn't available, allow the user to open the equivalent page in a non-embedded browser window.
      inputs.add(new LabelInput("The JxBrowser license could not be found."));
    }
    else if (latestFailureReason != null && Objects.equals(latestFailureReason.failureType, FailureType.SYSTEM_INCOMPATIBLE)) {
      // If we know the system is incompatible, skip retry link and offer to open in browser.
      inputs.add(new LabelInput(latestFailureReason.detail));
    }
    else {
      // Allow the user to manually restart or open the equivalent page in a non-embedded browser window.
      inputs.add(new LabelInput("JxBrowser installation failed."));
      inputs.add(new LabelInput("Retry installation?", (linkLabel, data) -> {
        jxBrowserManager.retryFromFailed(project);
        handleJxBrowserInstallationInProgress(contentManager);
      }));
    }

    showLabelsWithUrlLink(inputs, contentManager);
  }
}
