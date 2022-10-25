/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.junit.Test;

import static io.flutter.utils.TypedDataList.*;
import static org.junit.Assert.assertEquals;

// Note, all the data is interpreted as little-endian.
public class TypedDataListTest {

  final byte[] signedBytes = new byte[]{1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, -2, 2};
  final byte[] unsignedBytes = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

  @Test
  public void testInt8List() {
    Int8List list = new Int8List(signedBytes);
    assertEquals("1", list.getValue(0));
    assertEquals("-2", list.getValue(3));
    list = new Int8List(unsignedBytes);
    assertEquals("-1", list.getValue(0));
  }

  @Test
  public void testUint8List() {
    Uint8List b = new Uint8List(new byte[]{6, 7, -1, -7, -6, -5, -4, -3, -2, 8});
    assertEquals("0x6", b.getValue(0));
    assertEquals("0x7", b.getValue(1));
    assertEquals("0xf", b.getValue(2));
    assertEquals("0x9", b.getValue(3));
    assertEquals("0xa", b.getValue(4));
    assertEquals("0xb", b.getValue(5));
    assertEquals("0xc", b.getValue(6));
    assertEquals("0xd", b.getValue(7));
    assertEquals("0xe", b.getValue(8));
    assertEquals("0x8", b.getValue(9));
    Uint8List list = new Uint8List(signedBytes);
    assertEquals("0x1", list.getValue(0));
    assertEquals("0xf", list.getValue(1));
    list = new Uint8List(unsignedBytes);
    assertEquals("0xf", list.getValue(0));
  }

  @Test
  public void testInt16List() {
    Int16List list = new Int16List(signedBytes);
    assertEquals("-255", list.getValue(0));
    assertEquals("-510", list.getValue(1));
    list = new Int16List(unsignedBytes);
    assertEquals("-1", list.getValue(0));
  }

  @Test
  public void testUint16List() {
    Uint16List list = new Uint16List(signedBytes);
    assertEquals("0xff01", list.getValue(0));
    assertEquals("0xfe02", list.getValue(1));
    list = new Uint16List(unsignedBytes);
    assertEquals("0xffff", list.getValue(0));
  }

  @Test
  public void testInt32List() {
    Int32List list = new Int32List(signedBytes);
    assertEquals("-33358079", list.getValue(0));
    assertEquals("-66781949", list.getValue(1));
    list = new Int32List(unsignedBytes);
    assertEquals("-1", list.getValue(0));
  }

  @Test
  public void testUint32List() {
    Uint32List list = new Uint32List(signedBytes);
    assertEquals("0xfe02ff01", list.getValue(0));
    assertEquals("0xfc04fd03", list.getValue(1));
    list = new Uint32List(unsignedBytes);
    assertEquals("0xffffffff", list.getValue(0));
  }

  @Test
  public void testInt64List() {
    Int64List list = new Int64List(signedBytes);
    assertEquals("-286826282656530687", list.getValue(0));
    list = new Int64List(unsignedBytes);
    assertEquals("-1", list.getValue(0));
  }

  @Test
  public void testUint64List() {
    Uint64List list = new Uint64List(signedBytes);
    assertEquals("0xfc04fd03fe02ff01", list.getValue(0));
    list = new Uint64List(unsignedBytes);
    assertEquals("0xffffffffffffffff", list.getValue(0));
  }

  @Test
  public void testFloat32List() {
    byte[] bytes = new byte[]{0, 0, -128, 63};
    Float32List list = new Float32List(bytes);
    assertEquals("1.0", list.getValue(0));
  }

  @Test
  public void testFloat64List() {
    byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, -16, 63};
    Float64List list = new Float64List(bytes);
    assertEquals("1.0", list.getValue(0));
  }
}
