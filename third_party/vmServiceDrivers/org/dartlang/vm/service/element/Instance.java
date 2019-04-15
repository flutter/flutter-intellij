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

/**
 * An {@link Instance} represents an instance of the Dart language class {@link Obj}.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class Instance extends Obj {

  public Instance(JsonObject json) {
    super(json);
  }

  /**
   * The elements of a Map instance.
   *
   * Provided for instance kinds:
   *  - Map
   *
   * Can return <code>null</code>.
   */
  public ElementList<MapAssociation> getAssociations() {
    if (json.get("associations") == null) return null;
    
    return new ElementList<MapAssociation>(json.get("associations").getAsJsonArray()) {
      @Override
      protected MapAssociation basicGet(JsonArray array, int index) {
        return new MapAssociation(array.get(index).getAsJsonObject());
      }
    };
  }

  /**
   * The bound of a TypeParameter or BoundedType.
   *
   * The value will always be of one of the kinds: Type, TypeRef, TypeParameter, BoundedType.
   *
   * Provided for instance kinds:
   *  - BoundedType
   *  - TypeParameter
   *
   * Can return <code>null</code>.
   */
  public InstanceRef getBound() {
    return json.get("bound") == null ? null : new InstanceRef((JsonObject) json.get("bound"));
  }

  /**
   * The bytes of a TypedData instance.
   *
   * The data is provided as a Base64 encoded string.
   *
   * Provided for instance kinds:
   *  - Uint8ClampedList
   *  - Uint8List
   *  - Uint16List
   *  - Uint32List
   *  - Uint64List
   *  - Int8List
   *  - Int16List
   *  - Int32List
   *  - Int64List
   *  - Float32List
   *  - Float64List
   *  - Int32x4List
   *  - Float32x4List
   *  - Float64x2List
   *
   * Can return <code>null</code>.
   */
  public String getBytes() {
    return json.get("bytes") == null ? null : json.get("bytes").getAsString();
  }

  /**
   * Instance references always include their class.
   */
  public ClassRef getClassRef() {
    return new ClassRef((JsonObject) json.get("class"));
  }

  /**
   * The function associated with a Closure instance.
   *
   * Provided for instance kinds:
   *  - Closure
   *
   * Can return <code>null</code>.
   */
  public FuncRef getClosureFunction() {
    return json.get("closureFunction") == null ? null : new FuncRef((JsonObject) json.get("closureFunction"));
  }

  /**
   * The number of elements or associations or codeunits returned. This is only provided when it is
   * less than length.
   *
   * Provided for instance kinds:
   *  - String
   *  - List
   *  - Map
   *  - Uint8ClampedList
   *  - Uint8List
   *  - Uint16List
   *  - Uint32List
   *  - Uint64List
   *  - Int8List
   *  - Int16List
   *  - Int32List
   *  - Int64List
   *  - Float32List
   *  - Float64List
   *  - Int32x4List
   *  - Float32x4List
   *  - Float64x2List
   *
   * Can return <code>null</code>.
   */
  public int getCount() {
    return json.get("count") == null ? -1 : json.get("count").getAsInt();
  }

  /**
   * The elements of a List instance.
   *
   * Provided for instance kinds:
   *  - List
   *
   * @return one of <code>ElementList<InstanceRef></code> or <code>ElementList<Sentinel></code>
   *
   * Can return <code>null</code>.
   */
  public ElementList<InstanceRef> getElements() {
    if (json.get("elements") == null) return null;
    
    return new ElementList<InstanceRef>(json.get("elements").getAsJsonArray()) {
      @Override
      protected InstanceRef basicGet(JsonArray array, int index) {
        return new InstanceRef(array.get(index).getAsJsonObject());
      }
    };
  }

  /**
   * The fields of this Instance.
   *
   * Can return <code>null</code>.
   */
  public ElementList<BoundField> getFields() {
    if (json.get("fields") == null) return null;
    
    return new ElementList<BoundField>(json.get("fields").getAsJsonArray()) {
      @Override
      protected BoundField basicGet(JsonArray array, int index) {
        return new BoundField(array.get(index).getAsJsonObject());
      }
    };
  }

  /**
   * Whether this regular expression is case sensitive.
   *
   * Provided for instance kinds:
   *  - RegExp
   *
   * Can return <code>null</code>.
   */
  public boolean getIsCaseSensitive() {
    return json.get("isCaseSensitive") == null ? false : json.get("isCaseSensitive").getAsBoolean();
  }

  /**
   * Whether this regular expression matches multiple lines.
   *
   * Provided for instance kinds:
   *  - RegExp
   *
   * Can return <code>null</code>.
   */
  public boolean getIsMultiLine() {
    return json.get("isMultiLine") == null ? false : json.get("isMultiLine").getAsBoolean();
  }

  /**
   * What kind of instance is this?
   */
  public InstanceKind getKind() {
    final JsonElement value = json.get("kind");
    try {
      return value == null ? InstanceKind.Unknown : InstanceKind.valueOf(value.getAsString());
    } catch (IllegalArgumentException e) {
      return InstanceKind.Unknown;
    }
  }

  /**
   * The length of a List or the number of associations in a Map or the number of codeunits in a
   * String.
   *
   * Provided for instance kinds:
   *  - String
   *  - List
   *  - Map
   *  - Uint8ClampedList
   *  - Uint8List
   *  - Uint16List
   *  - Uint32List
   *  - Uint64List
   *  - Int8List
   *  - Int16List
   *  - Int32List
   *  - Int64List
   *  - Float32List
   *  - Float64List
   *  - Int32x4List
   *  - Float32x4List
   *  - Float64x2List
   *
   * Can return <code>null</code>.
   */
  public int getLength() {
    return json.get("length") == null ? -1 : json.get("length").getAsInt();
  }

  /**
   * TODO(devoncarew): this can return an InstanceRef
   *
   * The context associated with a Closure instance.
   *
   * Provided for instance kinds:
   *  - Closure@Context closureContext [optional]; The referent of a MirrorReference instance.
   *
   * Provided for instance kinds:
   *  - MirrorReference
   *
   * Can return <code>null</code>.
   */
  public InstanceRef getMirrorReferent() {
    return json.get("mirrorReferent") == null ? null : new InstanceRef((JsonObject) json.get("mirrorReferent"));
  }

  /**
   * The name of a Type instance.
   *
   * Provided for instance kinds:
   *  - Type
   *
   * Can return <code>null</code>.
   */
  public String getName() {
    return json.get("name") == null ? null : json.get("name").getAsString();
  }

  /**
   * The index of the first element or association or codeunit returned. This is only provided when
   * it is non-zero.
   *
   * Provided for instance kinds:
   *  - String
   *  - List
   *  - Map
   *  - Uint8ClampedList
   *  - Uint8List
   *  - Uint16List
   *  - Uint32List
   *  - Uint64List
   *  - Int8List
   *  - Int16List
   *  - Int32List
   *  - Int64List
   *  - Float32List
   *  - Float64List
   *  - Int32x4List
   *  - Float32x4List
   *  - Float64x2List
   *
   * Can return <code>null</code>.
   */
  public int getOffset() {
    return json.get("offset") == null ? -1 : json.get("offset").getAsInt();
  }

  /**
   * The index of a TypeParameter instance.
   *
   * Provided for instance kinds:
   *  - TypeParameter
   *
   * Can return <code>null</code>.
   */
  public int getParameterIndex() {
    return json.get("parameterIndex") == null ? -1 : json.get("parameterIndex").getAsInt();
  }

  /**
   * The parameterized class of a type parameter:
   *
   * Provided for instance kinds:
   *  - TypeParameter
   *
   * Can return <code>null</code>.
   */
  public ClassRef getParameterizedClass() {
    return json.get("parameterizedClass") == null ? null : new ClassRef((JsonObject) json.get("parameterizedClass"));
  }

  /**
   * The pattern of a RegExp instance.
   *
   * Provided for instance kinds:
   *  - RegExp
   *
   * Can return <code>null</code>.
   */
  public String getPattern() {
    return json.get("pattern") == null ? null : json.get("pattern").getAsString();
  }

  /**
   * The key for a WeakProperty instance.
   *
   * Provided for instance kinds:
   *  - WeakProperty
   *
   * Can return <code>null</code>.
   */
  public InstanceRef getPropertyKey() {
    return json.get("propertyKey") == null ? null : new InstanceRef((JsonObject) json.get("propertyKey"));
  }

  /**
   * The key for a WeakProperty instance.
   *
   * Provided for instance kinds:
   *  - WeakProperty
   *
   * Can return <code>null</code>.
   */
  public InstanceRef getPropertyValue() {
    return json.get("propertyValue") == null ? null : new InstanceRef((JsonObject) json.get("propertyValue"));
  }

  /**
   * The type bounded by a BoundedType instance - or - the referent of a TypeRef instance.
   *
   * The value will always be of one of the kinds: Type, TypeRef, TypeParameter, BoundedType.
   *
   * Provided for instance kinds:
   *  - BoundedType
   *  - TypeRef
   *
   * Can return <code>null</code>.
   */
  public InstanceRef getTargetType() {
    return json.get("targetType") == null ? null : new InstanceRef((JsonObject) json.get("targetType"));
  }

  /**
   * The type arguments for this type.
   *
   * Provided for instance kinds:
   *  - Type
   *
   * Can return <code>null</code>.
   */
  public TypeArgumentsRef getTypeArguments() {
    return json.get("typeArguments") == null ? null : new TypeArgumentsRef((JsonObject) json.get("typeArguments"));
  }

  /**
   * The corresponding Class if this Type is canonical.
   *
   * Provided for instance kinds:
   *  - Type
   *
   * Can return <code>null</code>.
   */
  public ClassRef getTypeClass() {
    return json.get("typeClass") == null ? null : new ClassRef((JsonObject) json.get("typeClass"));
  }

  /**
   * The value of this instance as a string.
   *
   * Provided for the instance kinds:
   *  - Bool (true or false)
   *  - Double (suitable for passing to Double.parse())
   *  - Int (suitable for passing to int.parse())
   *  - String (value may be truncated)
   *
   * Can return <code>null</code>.
   */
  public String getValueAsString() {
    return json.get("valueAsString") == null ? null : json.get("valueAsString").getAsString();
  }

  /**
   * The valueAsString for String references may be truncated. If so, this property is added with
   * the value 'true'.
   *
   * New code should use 'length' and 'count' instead.
   *
   * Can return <code>null</code>.
   */
  public boolean getValueAsStringIsTruncated() {
    final JsonElement elem = json.get("valueAsStringIsTruncated");
    return elem != null ? elem.getAsBoolean() : false;
  }
}
