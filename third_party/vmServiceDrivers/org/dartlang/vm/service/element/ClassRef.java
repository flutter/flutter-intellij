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
package org.dartlang.vm.service.element;

// This is a generated file.

import com.google.gson.JsonObject;

/**
 * {@link ClassRef} is a reference to a {@link ClassObj}.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class ClassRef extends ObjRef {

  public ClassRef(JsonObject json) {
    super(json);
  }

  /**
   * The library which contains this class.
   */
  public LibraryRef getLibrary() {
    return new LibraryRef((JsonObject) json.get("library"));
  }

  /**
   * The location of this class in the source code.
   *
   * Can return <code>null</code>.
   */
  public SourceLocation getLocation() {
    JsonObject obj = (JsonObject) json.get("location");
    if (obj == null) return null;
    final String type = json.get("type").getAsString();
    if ("Instance".equals(type) || "@Instance".equals(type)) {
      final String kind = json.get("kind").getAsString();
      if ("Null".equals(kind)) return null;
    }
    return new SourceLocation(obj);
  }

  /**
   * The name of this class.
   */
  public String getName() {
    return getAsString("name");
  }
}
