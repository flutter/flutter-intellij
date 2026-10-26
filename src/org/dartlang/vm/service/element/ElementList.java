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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Simple wrapper around a {@link JsonArray} which lazily converts {@link JsonObject} elements to
 * subclasses of {@link Element}. Subclasses need only implement {@link #basicGet(JsonArray, int)}
 * to return an {@link Element} subclass for the {@link JsonObject} at a given index.
 */
public abstract class ElementList<T> implements Iterable<T> {

  private final JsonArray array;

  public ElementList(JsonArray array) {
    this.array = array;
  }

  public T get(int index) {
    return basicGet(array, index);
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    return new Iterator<T>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < size();
      }

      @Override
      public T next() {
        return get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public int size() {
    return array.size();
  }

  protected abstract T basicGet(JsonArray array, int index);
}
