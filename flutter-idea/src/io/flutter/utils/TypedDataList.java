/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jetbrains.annotations.NotNull;

import java.nio.*;

public abstract class TypedDataList {
  public  
  abstract String getValue(int i);

  public abstract int size();

  public static class Int8List extends TypedDataList {
      final ByteBuffer buffer;

    public Int8List(byte   [] bytes) {
      buffer = ByteBuffer.wrap(bytes);
    }

    public   String getValue(int i) {
      return Byte.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint8List extends Int8List {
    public Uint8List(byte   [] bytes) {
      super(bytes);
    }

    public   String getValue(int i) {
      return "0x" + Integer.toHexString(buffer.get(i) & 0xff);
    }
  }

  public static class Int16List extends TypedDataList {
      final ShortBuffer buffer;

    public Int16List(byte   [] bytes) {
      // 95% of CPUs are running in little-endian mode now, and we're not going to get fancy here.
      // If this becomes a problem, see the Dart code Endian.host for a way to possibly fix it.
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
    }

    public   String getValue(int i) {
      return Short.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint16List extends Int16List {
    public Uint16List(byte   [] bytes) {
      super(bytes);
    }

    public   String getValue(int i) {
      return "0x" + Integer.toHexString(buffer.get(i) & 0xffff);
    }
  }

  public static class Int32List extends TypedDataList {
      final IntBuffer buffer;

    public Int32List(byte   [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
    }

    public   String getValue(int i) {
      return Integer.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint32List extends Int32List {
    public Uint32List(byte   [] bytes) {
      super(bytes);
    }

    public   String getValue(int i) {
      return "0x" + Integer.toHexString(buffer.get(i));
    }
  }

  public static class Int64List extends TypedDataList {
      final LongBuffer buffer;

    public Int64List(byte   [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
    }

    public   String getValue(int i) {
      return Long.toString(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Uint64List extends Int64List {
    public Uint64List(byte   [] bytes) {
      super(bytes);
    }

    public   String getValue(int i) {
      return "0x" + Long.toUnsignedString(buffer.get(i), 16);
    }
  }

  public static class Float32List extends TypedDataList {
      final FloatBuffer buffer;

    public Float32List(byte   [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    }

    public   String getValue(int i) {
      return String.valueOf(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }

  public static class Float64List extends TypedDataList {
      final DoubleBuffer buffer;

    public Float64List(byte   [] bytes) {
      //noinspection ConstantConditions
      buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();
    }

    public   String getValue(int i) {
      return String.valueOf(buffer.get(i));
    }

    public int size() {
      return buffer.capacity();
    }
  }
}