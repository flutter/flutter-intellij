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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@SuppressWarnings({"WeakerAccess", "unused"})
public class Frame extends Response {

  public Frame(JsonObject json) {
    super(json);
  }

  /**
   * Can return <code>null</code>.
   */
  public CodeRef getCode() {
    return json.get("code") == null ? null : new CodeRef((JsonObject) json.get("code"));
  }

  /**
   * Can return <code>null</code>.
   */
  public FuncRef getFunction() {
    return json.get("function") == null ? null : new FuncRef((JsonObject) json.get("function"));
  }

  public int getIndex() {
    return json.get("index") == null ? -1 : json.get("index").getAsInt();
  }

  /**
   * Can return <code>null</code>.
   */
  public FrameKind getKind() {
    if (json.get("kind") == null) return null;
    
    final JsonElement value = json.get("kind");
    try {
      return value == null ? FrameKind.Unknown : FrameKind.valueOf(value.getAsString());
    } catch (IllegalArgumentException e) {
      return FrameKind.Unknown;
    }
  }

  /**
   * Can return <code>null</code>.
   */
  public SourceLocation getLocation() {
    return json.get("location") == null ? null : new SourceLocation((JsonObject) json.get("location"));
  }

  /**
   * Can return <code>null</code>.
   */
  public ElementList<BoundVariable> getVars() {
    if (json.get("vars") == null) return null;
    
    return new ElementList<BoundVariable>(json.get("vars").getAsJsonArray()) {
      @Override
      protected BoundVariable basicGet(JsonArray array, int index) {
        return new BoundVariable(array.get(index).getAsJsonObject());
      }
    };
  }
}
