// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidFrameworkDetector extends FacetBasedFrameworkDetector<AndroidFacet, AndroidFacetConfiguration> {
  public AndroidFrameworkDetector() {
    super("android");
  }

  @Override
  public List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<? extends VirtualFile> newFiles,
                                                             @NotNull FrameworkDetectionContext context) {
    Project project = context.getProject();
    if (project != null) {
      GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(project);
      // See https://code.google.com/p/android/issues/detail?id=203384
      // Since this method is invoked before sync, 'isBuildWithGradle' may return false even for Gradle projects. If that happens, we fall
      // back to checking that a project has a suitable Gradle file at its toplevel.
      if (gradleProjectInfo.isBuildWithGradle() || gradleProjectInfo.hasTopLevelGradleFile()) {
        return Collections.emptyList();
      }
    }
    return super.detect(newFiles, context);
  }

  private static boolean getFirstAsBoolean(@NotNull Pair<String, VirtualFile> pair) {
    return Boolean.parseBoolean(pair.getFirst());
  }

  @NotNull
  public static Notification showDexOptionNotification(@NotNull Module module, @NotNull String propertyName) {
    Project project = module.getProject();
    Notification notification = NotificationGroupManager.getInstance().getNotificationGroup("Android Module Importing").createNotification(
      AndroidBundle.message("android.facet.importing.title", module.getName()),
      "'" + propertyName +
      "' property is detected in " + SdkConstants.FN_PROJECT_PROPERTIES +
      " file.<br>You may enable related option in <a href='configure'>Settings | Compiler | Android DX</a>",
      NotificationType.INFORMATION).setListener(new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        notification.expire();
        ShowSettingsUtil.getInstance().showSettingsDialog(
          project, AndroidBundle.message("android.dex.compiler.configurable.display.name"));
      }
    });
    notification.notify(project);
    return notification;
  }

  @NotNull
  @Override
  public FacetType<AndroidFacet, AndroidFacetConfiguration> getFacetType() {
    return AndroidFacet.getFacetType();
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return XmlFileType.INSTANCE;
  }

  @Override
  @NotNull
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent().withName(SdkConstants.FN_ANDROID_MANIFEST_XML);
  }
}
