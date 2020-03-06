/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npwOld.model;

import com.android.annotations.VisibleForTesting;
//import com.android.tools.idea.project.IndexingSuspender;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sometimes, there are several separate classes which want to render templates, in some order, but the whole process should be aborted if
 * any of them fail a validation pass. This class acts as a central way to coordinate such render request.
 */
public final class MultiTemplateRenderer {
  public interface TemplateRendererListener {
    /**
     * Called just before rendering multiple templates. Since rendering typically involves adding quite a lot of files
     * to the project, this callback is useful to prevent other file-intensive operations such as indexing.
     */
   default void multiRenderingStarted() {}

    /**
     * Called when the last template in the series has been rendered.
     */
   default void multiRenderingFinished() {}
  }

  private static final Topic<TemplateRendererListener> TEMPLATE_RENDERER_TOPIC = new Topic<>("Template rendering",
                                                                                             TemplateRendererListener.class);

  public interface TemplateRenderer {
    /**
     * Runs any needed Model pre-initialisation, for example, setting Template default values.
     */
    default void init() {}

    /**
     * Run validation, but don't write any file
     * @return true if the validation succeeded. Returning false will stop any call to {@link #render()}
     */
    boolean doDryRun();

    /**
     * Do the actual work of writing the files.
     */
    void render();
  }

  @Nullable private Project myProject;
  @NotNull private final ProjectSyncInvoker myProjectSyncInvoker;
  private final List<TemplateRenderer> myTemplateRenderers = new ArrayList<>();
  private int myRequestCount = 1;

  public MultiTemplateRenderer(@Nullable Project project, @NotNull ProjectSyncInvoker projectSyncInvoker) {
    myProject = project;
    myProjectSyncInvoker = projectSyncInvoker;
  }

  @NotNull
  public static MessageBusConnection subscribe(@NotNull Project project, @NotNull TemplateRendererListener listener) {
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(TEMPLATE_RENDERER_TOPIC, listener);
    return connection;
  }

  @VisibleForTesting
  public static void multiRenderingStarted(@NotNull Project project) {
    project.getMessageBus().syncPublisher(TEMPLATE_RENDERER_TOPIC).multiRenderingStarted();
  }

  @VisibleForTesting
  public static void multiRenderingFinished(@NotNull Project project) {
    project.getMessageBus().syncPublisher(TEMPLATE_RENDERER_TOPIC).multiRenderingFinished();
  }

  /**
   * When creating a new Project, the new Project instance is only available after the {@link MultiTemplateRenderer} is created.
   * Use this method to set Project instance later.
   * @param project
   */
  public void setProject(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Call this method to indicate that one more render is available. Every call to this method needs to be later matched by a
   * call to either {@link #requestRender(TemplateRenderer)} or {@link #skipRender()}
   */
  public void incrementRenders() {
    myRequestCount++;
  }

  /**
   * Enqueue a template render request, batching it into a collection that will all be validated and, if all valid, rendered, at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  public void requestRender(@NotNull TemplateRenderer templateRenderer) {
    myTemplateRenderers.add(templateRenderer);
    countDown();
  }

  /**
   * Skip a template render request, any pending batching collection will be all validated and, if all valid, rendered, at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  public void skipRender() {
    countDown();
  }

  /**
   * Process batched requests. When all requests are accounted (#incrementRenders == #requestRender + #skipRender), we check that all
   * requests are valid, and if they are, run render them all.
   */
  private void countDown() {
    if (myRequestCount == 0) {
      throw new IllegalStateException("Invalid extra call to MultiTemplateRenderer#countDown");
    }
    myRequestCount--;

    if (myRequestCount == 0 && !myTemplateRenderers.isEmpty()) {
      assert myProject != null : "Project instance is always expected to be not null at this point.";
      //IndexingSuspender.ensureInitialised(myProject);
      multiRenderingStarted(myProject);

      try {
        // Some models need to access other models data, during doDryRun/render phase. By calling init() in all of them first, we make sure
        // they are properly initialized when doDryRun/render is called bellow.
        for (TemplateRenderer renderer : myTemplateRenderers) {
          renderer.init();
        }

        for (TemplateRenderer renderer : myTemplateRenderers) {
          if (!renderer.doDryRun()) {
            return;
          }
        }

        for (TemplateRenderer renderer : myTemplateRenderers) {
          renderer.render();
        }

        if (myProject.isInitialized()) {
          myProjectSyncInvoker.syncProject(myProject);
        }

      }
      finally {
        multiRenderingFinished(myProject);
      }
    }
  }
}
