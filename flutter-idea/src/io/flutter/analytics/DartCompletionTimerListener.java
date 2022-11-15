package io.flutter.analytics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import com.intellij.util.IncorrectOperationException;
import com.jetbrains.lang.dart.ide.completion.DartCompletionTimerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Implementation of the {@link DartCompletionTimerExtension} which allows the Flutter plugin to
 * measure the total time to present a code completion to the user.
 */
public class DartCompletionTimerListener extends DartCompletionTimerExtension {
  @Nullable FlutterAnalysisServerListener dasListener;
  @Nullable Long startTimeMS;

  @Override
  public void dartCompletionStart() {
    if (dasListener == null) {
      try {
        Project project = ProjectManager.getInstance().getDefaultProject();
        dasListener = FlutterAnalysisServerListener.getInstance(project);
      } catch (IncorrectOperationException ex) {
        return;
      }
    }
    startTimeMS = Instant.now().toEpochMilli();
  }

  @Override
  public void dartCompletionEnd() {
    if (dasListener != null && startTimeMS != null) {
      long durationTimeMS = Instant.now().toEpochMilli() - startTimeMS;
      dasListener.logE2ECompletionSuccessMS(durationTimeMS); // test: logE2ECompletionSuccessMS()
      startTimeMS = null;
    }
  }

  /**
   * The parameters match those of {@link org.dartlang.analysis.server.protocol.RequestError}, they
   * are not used currently because our hypothesis is that this method will never be called.
   */
  @Override
  public void dartCompletionError(
    @NotNull String code, @NotNull String message, @NotNull String stackTrace) {
    if (dasListener != null && startTimeMS != null) {
      long durationTimeMS = Instant.now().toEpochMilli() - startTimeMS;
      dasListener.logE2ECompletionErrorMS(durationTimeMS); // test: logE2ECompletionErrorMS()
      startTimeMS = null;
    }
  }
}