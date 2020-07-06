/*
 * Copyright (C) 2020 The Android Open Source Project
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
package io.flutter.assistant.whatsnew;

import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.android.tools.idea.assistant.datamodel.AnalyticsProvider;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import io.flutter.sdk.FlutterSdk;
import java.io.InputStream;
import java.net.URL;
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
