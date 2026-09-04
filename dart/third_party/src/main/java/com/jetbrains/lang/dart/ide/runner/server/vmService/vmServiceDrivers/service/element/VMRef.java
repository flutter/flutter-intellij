/*
 * Copyright (c) 2015, the Dart project authors.
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
package com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element;

import com.google.gson.JsonObject;

/**
 * {@link VMRef} is a reference to a {@link VM} object.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class VMRef extends Response {

  public VMRef(JsonObject json) {
    super(json);
  }

  /**
   * A name identifying this vm. Not guaranteed to be unique.
   */
  public String getName() {
    return getAsString("name");
  }
}
