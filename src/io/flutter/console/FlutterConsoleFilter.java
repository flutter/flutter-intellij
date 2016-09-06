/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.Nullable;

public class FlutterConsoleFilter implements Filter {

  private static final Logger LOG = Logger.getInstance(FlutterConsoleFilter.class);

  private final Module module;

  public FlutterConsoleFilter(Module module) {
    this.module = module;
  }

  @Nullable
  public Result applyFilter(final String line, final int entireLength) {
    if (line.startsWith("Run \"flutter doctor\" for information about installing additional components.")) {
      return getFlutterDoctorResult(line, entireLength - line.length());
    }

    return null;
  }

  private Result getFlutterDoctorResult(final String line, final int lineStart) {
    final int commandStart = line.indexOf('"') + 1;
    final int startOffset = lineStart + commandStart;
    final int commandLength = "flutter doctor".length();
    return new Result(startOffset, startOffset + commandLength, new FlutteryHyperlinkInfo());
  }


  private class FlutteryHyperlinkInfo implements HyperlinkInfo {
    @Override
    public void navigate(final Project project) {
      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk != null) {
        try {
          sdk.run(FlutterSdk.Command.DOCTOR, module, project.getBaseDir(), null, new String[]{});
        }
        catch (ExecutionException e) {
          LOG.warn(e);
        }
      }
    }
  }
}
