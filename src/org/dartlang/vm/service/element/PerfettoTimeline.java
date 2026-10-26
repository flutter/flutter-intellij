/*
 * Copyright (c) 2026, the Dart project authors.
 *
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.dartlang.vm.service.element;

import com.google.gson.JsonObject;

/**
 * See getPerfettoVMTimeline;
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class PerfettoTimeline extends Response {

  public PerfettoTimeline(JsonObject json) {
    super(json);
  }

  /**
   * The duration of time covered by the trace.
   */
  public long getTimeExtentMicros() {
    return getAsLong("timeExtentMicros");
  }

  /**
   * The start of the period of time covered by the trace.
   */
  public long getTimeOriginMicros() {
    return getAsLong("timeOriginMicros");
  }

  /**
   * A Base64 string representing the requested timeline trace in Perfetto's proto format.
   */
  public String getTrace() {
    return getAsString("trace");
  }
}
