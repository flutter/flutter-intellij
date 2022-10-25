/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jetbrains.annotations.NotNull;

import java.nio.*;

public abstract class TypedDataList {
  public @NotNull
  abstract String getValue(int i);

  public abstract int size();

  public static class Int8List extends TypedDataList {
    @NotNull final ByteBuffer buffer;

    public Int8List(byte @NotNull [] bytes) {
      buffer = ByteBuffer.wrap(bytes);
    }

    public @NotNull String getValue(int i) {
      return Byte.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint8List extends Int8List {
    public Uint8List(byte @NotNull [] bytes) {
      super(bytes);
    }

    public @NotNull String getValue(int i) {
      String hex = Integer.toHexString(buffer.get(i) & 0xff);
      if (hex.length() == 2) {
        hex = hex.substring(1, 2);
      }
      return "0x" + hex;
    }
  }

  public static class Int16List extends TypedDataList {
    @NotNull final ShortBuffer buffer;

    public Int16List(byte @NotNull [] bytes) {
      // 95% of CPUs are running in little-endian mode now, and we're not going to get fancy here.
      // If this becomes a problem, see the Dart code Endian.host for a way to possibly fix it.
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
    }

    public @NotNull String getValue(int i) {
      return Short.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint16List extends Int16List {
    public Uint16List(byte @NotNull [] bytes) {
      super(bytes);
    }

    public @NotNull String getValue(int i) {
      return "0x" + Integer.toHexString(buffer.get(i) & 0xffff);
    }
  }

  public static class Int32List extends TypedDataList {
    @NotNull final IntBuffer buffer;

    public Int32List(byte @NotNull [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
    }

    public @NotNull String getValue(int i) {
      return Integer.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint32List extends Int32List {
    public Uint32List(byte @NotNull [] bytes) {
      super(bytes);
    }

    public @NotNull String getValue(int i) {
      return "0x" + Integer.toHexString(buffer.get(i));
    }
  }

  public static class Int64List extends TypedDataList {
    @NotNull final LongBuffer buffer;

    public Int64List(byte @NotNull [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
    }

    public @NotNull String getValue(int i) {
      return Long.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint64List extends Int64List {
    public Uint64List(byte @NotNull [] bytes) {
      super(bytes);
    }

    public @NotNull String getValue(int i) {
      return "0x" + Long.toUnsignedString(buffer.get(i), 16);
    }
  }

  public static class Float32List extends TypedDataList {
    @NotNull final FloatBuffer buffer;

    public Float32List(byte @NotNull [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    }

    public @NotNull String getValue(int i) {
      return String.valueOf(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Float64List extends TypedDataList {
    @NotNull final DoubleBuffer buffer;

    public Float64List(byte @NotNull [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();
    }

    public @NotNull String getValue(int i) {
      return String.valueOf(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }
}