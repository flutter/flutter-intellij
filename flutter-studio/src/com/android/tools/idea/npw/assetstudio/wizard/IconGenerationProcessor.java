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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import java.util.function.Consumer;
import javax.swing.SwingWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generates icons in a background thread using {@link IconGenerator} instances.
 */
public class IconGenerationProcessor {
  @Nullable private Request myQueuedRequest;
  @Nullable private Request myRunningRequest;

  public void enqueue(@NotNull IconGenerator iconGenerator, @NotNull Consumer<IconGeneratorResult> onDone) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (iconGenerator.sourceAsset().get().isPresent()) {
      IconGenerator.Options options = iconGenerator.createOptions(true);
      myQueuedRequest = new Request(iconGenerator, options, onDone);
    }

    processNextRequest();
  }

  private void processNextRequest() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myQueuedRequest == null) {
      return;
    }

    if (myRunningRequest != null) {
      myRunningRequest.cancel();
    }

    myRunningRequest = myQueuedRequest;
    myQueuedRequest = null;
    Worker worker = new Worker(myRunningRequest, () -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myRunningRequest = null;
      processNextRequest();
    });
    worker.execute();
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(IconGenerationProcessor.class);
  }

  private static class Request {
    @NotNull private final IconGenerator myIconGenerator;
    @NotNull private final Consumer<IconGeneratorResult> myOnDone;
    @NotNull private final IconGenerator.Options myOptions;
    @Nullable private IconGeneratorResult myGeneratorResult;
    private boolean isCanceled;

    public Request(@NotNull IconGenerator iconGenerator,
                   @NotNull IconGenerator.Options options,
                   @NotNull Consumer<IconGeneratorResult> onDone) {
      myIconGenerator = iconGenerator;
      myOptions = options;
      myOnDone = onDone;
    }

    public void run() {
      assert !ApplicationManager.getApplication().isDispatchThread();

      myGeneratorResult = myIconGenerator.generateIcons(myOptions);
    }

    public void done() {
      if (!isCanceled) {
        myOnDone.accept(myGeneratorResult);
      }
    }

    public void cancel() {
      isCanceled = true;
    }

    public boolean isCanceled() {
      return isCanceled;
    }
  }

  private static class Worker extends SwingWorker<Void, Void> {
    @NotNull private final Request myRequest;
    @NotNull private final Runnable myOnDone;

    public Worker(@NotNull Request request, @NotNull Runnable onDone) {
      myRequest = request;
      myOnDone = onDone;
    }

    @Override
    protected Void doInBackground() {
      long start = System.currentTimeMillis();
      myRequest.run();
      long end = System.currentTimeMillis();
      getLog().info(String.format("Icons generated in %.2g sec", (end - start) / 1000.));
      return null;
    }

    @Override
    protected void done() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      try {
        myRequest.done();
      }
      finally {
        // Don't run immediately to allow things to settle down if necessary.
        ApplicationManager.getApplication().invokeLater(myOnDone, ModalityState.any());
      }
    }
  }
}
