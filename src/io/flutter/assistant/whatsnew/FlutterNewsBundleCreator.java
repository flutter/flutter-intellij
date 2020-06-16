/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.assistant.whatsnew;

import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.android.tools.idea.assistant.datamodel.AnalyticsProvider;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import io.flutter.sdk.FlutterSdk;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterNewsBundleCreator implements AssistantBundleCreator {
  public static final String BUNDLE_ID = "DeveloperServices.FlutterNewsAssistant";
  public static final String FLUTTER_NEWS_PATH = "/flutter-news-assistant.xml";

  @SuppressWarnings("FieldCanBeLocal")
  private String pluginVersion;
  @SuppressWarnings("FieldCanBeLocal")
  private String flutterVersion;

  @NotNull
  @Override
  public String getBundleId() {
    return BUNDLE_ID;
  }

  @Nullable
  @Override
  public URL getConfig() {
    return null;
  }

  @Nullable
  @Override
  public FlutterNewsBundle getBundle(@NotNull Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode() || !ApplicationManager.getApplication().isDispatchThread();
    @Nullable FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      getLog().error("Could not get Flutter SDK");
      return null;
    }
    flutterVersion = sdk.getVersion().fullVersion();

    PluginManager pluginManager = PluginManager.getInstance();
    IdeaPluginDescriptor descriptor = pluginManager.findEnabledPlugin(PluginId.getId("io.flutter"));
    if (descriptor == null) {
      getLog().error("Could not get plugin version");
      return null;
    }
    pluginVersion = descriptor.getVersion();

    // Hopefully, the news file can be distributed and managed with the Flutter SDK. For now, embed it in the plugin.
    FlutterNewsBundle bundle = parseBundle(FLUTTER_NEWS_PATH);
    if (bundle == null) {
      bundle = parseBundle(FLUTTER_NEWS_PATH.substring(0, FLUTTER_NEWS_PATH.length() - 3) + pluginVersion + ".xml");
    }
    if (bundle == null) {
      bundle = parseBundle(FLUTTER_NEWS_PATH.substring(0, FLUTTER_NEWS_PATH.length() - 3) + flutterVersion + ".xml");
    }
    if (bundle == null) {
      getLog().error("Could not parse bundle");
    }
    return bundle;
  }

  @NotNull
  @Override
  public AnalyticsProvider getAnalyticsProvider() {
    return AnalyticsProvider.getNoOp(); // TODO Hook up io.flutter.analytics.Analytics
  }

  private FlutterNewsBundle parseBundle(String path) {
    try (InputStream configStream = getClass().getResourceAsStream(path)) {
      if (configStream == null)
        return null;
      return DefaultTutorialBundle.parse(configStream, FlutterNewsBundle.class, getBundleId());
    }
    catch (Exception e) {
      getLog().warn("Error parsing bundle", e);
      return null;
    }
  }

  private static Logger getLog() {
    return Logger.getInstance(FlutterNewsBundleCreator.class);
  }
}
