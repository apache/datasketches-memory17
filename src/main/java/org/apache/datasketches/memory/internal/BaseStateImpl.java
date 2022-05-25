/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.datasketches.memory.internal;

import static jdk.incubator.foreign.MemoryAccess.getByteAtOffset;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.datasketches.memory.BaseState;
import org.apache.datasketches.memory.MemoryRequestServer;

import jdk.incubator.foreign.MemorySegment;

/**
 * Keeps key configuration state for MemoryImpl and BufferImpl plus some common static variables
 * and check methods.
 *
 * @author Lee Rhodes
 */
@SuppressWarnings("restriction")
public abstract class BaseStateImpl implements BaseState {
  public static final String JDK; //must be at least "1.8"
  public static final int JDK_MAJOR; //8, 11, 12, etc

  public static final int BOOLEAN_SHIFT    = 0;
  public static final int BYTE_SHIFT       = 0;
  public static final long SHORT_SHIFT     = 1;
  public static final long CHAR_SHIFT      = 1;
  public static final long INT_SHIFT       = 2;
  public static final long LONG_SHIFT      = 3;
  public static final long FLOAT_SHIFT     = 2;
  public static final long DOUBLE_SHIFT    = 3;

  public static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();
  public static final ByteOrder NON_NATIVE_BYTE_ORDER;

  //class type IDs.
  // 0000 0XXX
  public static final int READONLY  = 1;
  public static final int REGION    = 1 << 1;
  public static final int DUPLICATE = 1 << 2; //for Buffer only

  // 000X X000
  public static final int HEAP   = 0;
  public static final int DIRECT = 1 << 3;
  public static final int MAP    = 1 << 4; //Map is always Direct also

  // 00X0 0000
  public static final int NATIVE    = 0;
  public static final int NONNATIVE = 1 << 5;

  // 0X00 0000
  public static final int MEMORY = 0;
  public  static final int BUFFER = 1 << 6;

  // X000 0000
  public static final int BYTEBUF = 1 << 7;

  final MemorySegment seg;
  final int typeId;
  MemoryRequestServer memReqSvr;

  static {
    final String jdkVer = System.getProperty("java.version");
    final int[] p = parseJavaVersion(jdkVer);
    JDK = p[0] + "." + p[1];
    JDK_MAJOR = (p[0] == 1) ? p[1] : p[0];

    NON_NATIVE_BYTE_ORDER = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
  }

  public BaseStateImpl(final MemorySegment seg, final int typeId) {
    this.seg = seg;
    this.typeId = typeId;
  }

  /**
   * Assert the requested offset and length against the allocated size.
   * The invariants equation is: {@code 0 <= reqOff <= reqLen <= reqOff + reqLen <= allocSize}.
   * If this equation is violated and assertions are enabled, an {@link AssertionError} will
   * be thrown.
   * @param reqOff the requested offset
   * @param reqLen the requested length
   * @param allocSize the allocated size.
   */
  public static void assertBounds(final long reqOff, final long reqLen, final long allocSize) {
    assert ((reqOff | reqLen | (reqOff + reqLen) | (allocSize - (reqOff + reqLen))) >= 0) :
      "reqOffset: " + reqOff + ", reqLength: " + reqLen
      + ", (reqOff + reqLen): " + (reqOff + reqLen) + ", allocSize: " + allocSize;
  }

  /**
   * Check the requested offset and length against the allocated size.
   * The invariants equation is: {@code 0 <= reqOff <= reqLen <= reqOff + reqLen <= allocSize}.
   * If this equation is violated an {@link IllegalArgumentException} will be thrown.
   * @param reqOff the requested offset
   * @param reqLen the requested length
   * @param allocSize the allocated size.
   */
  public static void checkBounds(final long reqOff, final long reqLen, final long allocSize) {
    if ((reqOff | reqLen | (reqOff + reqLen) | (allocSize - (reqOff + reqLen))) < 0) {
      throw new IllegalArgumentException(
          "reqOffset: " + reqOff + ", reqLength: " + reqLen
              + ", (reqOff + reqLen): " + (reqOff + reqLen) + ", allocSize: " + allocSize);
    }
  }

  @SuppressWarnings("resource")
  @Override
  public void close() {
    if (seg != null && seg.scope().isAlive() && !seg.scope().isImplicit()) {
      if (seg.isNative() || seg.isMapped()) {
        seg.scope().close();
      }
    }
  }

  //Byte Order Related

  @Override
  public final ByteOrder getTypeByteOrder() {
    return isNonNativeType() ? NON_NATIVE_BYTE_ORDER : ByteOrder.nativeOrder();
  }

  /**
   * Returns true if the given byteOrder is the same as the native byte order.
   * @param byteOrder the given byte order
   * @return true if the given byteOrder is the same as the native byte order.
   */
  public static boolean isNativeByteOrder(final ByteOrder byteOrder) {
    if (byteOrder == null) {
      throw new IllegalArgumentException("ByteOrder parameter cannot be null.");
    }
    return ByteOrder.nativeOrder() == byteOrder;
  }

  @Override
  public final boolean isByteOrderCompatible(final ByteOrder byteOrder) {
    final ByteOrder typeBO = getTypeByteOrder();
    return typeBO == ByteOrder.nativeOrder() && typeBO == byteOrder;
  }

  @Override
  public final boolean equalTo(final long thisOffsetBytes, final Object that,
      final long thatOffsetBytes, final long lengthBytes) {
    return that instanceof BaseStateImpl
      ? CompareAndCopy.equals(seg, thisOffsetBytes, ((BaseStateImpl) that).seg, thatOffsetBytes, lengthBytes)
      : false;
  }

  @Override
  public ByteBuffer asByteBuffer() {
    return seg.asByteBuffer();
  }

  @Override
  public MemorySegment asMemorySegment() {
    MemorySegment arrSeg = MemorySegment.ofArray(new byte[(int)seg.byteSize()]);
    arrSeg.copyFrom(seg);
    return arrSeg;
  }

  @Override
  public final long getCapacity() {
    return seg.byteSize();
  }

  @Override
  public MemoryRequestServer getMemoryRequestServer() {
    return memReqSvr;
  }

  @Override
  public boolean hasMemoryRequestServer() {
    return memReqSvr != null;
  }

  @Override
  public void setMemoryRequestServer(final MemoryRequestServer memReqSvr) {
    this.memReqSvr = memReqSvr;
  }

  int getTypeId() {
    return typeId;
  }

  @Override
  public final long xxHash64(final long offsetBytes, final long lengthBytes, final long seed) {
    return XxHash64.hash(seg, offsetBytes, lengthBytes, seed);
  }

  @Override
  public final long xxHash64(final long in, final long seed) {
    return XxHash64.hash(in, seed);
  }

  @Override
  public final boolean hasByteBuffer() {
    return isByteBufferType();
  }

  @Override
  public final boolean isDirect() {
    return seg.isNative();
  }

  @Override
  public final boolean isSameResource(final Object that) {
    return (this == that);
  }

  //TYPE ID Management

  @Override
  public final boolean isReadOnly() {
    return (getTypeId() & READONLY) > 0;
  }

  final static int setReadOnlyType(final int type, final boolean readOnly) {
    return (type & ~READONLY) | (readOnly ? READONLY : 0);
  }

  final boolean isRegionType() {
    return (getTypeId() & REGION) > 0;
  }

  final boolean isDuplicateType() {
    return (getTypeId() & DUPLICATE) > 0;
  }

  final boolean isMemoryType() {
    return (getTypeId() & BUFFER) == 0;
  }

  final boolean isBufferType() {
    return (getTypeId() & BUFFER) > 0;
  }

  final boolean isNativeType() {
    return (getTypeId() & NONNATIVE) == 0;
  }

  final boolean isNonNativeType() {
    return (getTypeId() & NONNATIVE) > 0;
  }

  final boolean isHeapType() {
    return (getTypeId() >>> 3 & 3) == 0;
  }

  final boolean isDirectType() {
    return (getTypeId() & DIRECT) > 0;
  }

  final boolean isMapType() {
    return (getTypeId() & MAP) > 0;
  }

  final boolean isByteBufferType() {
    return (getTypeId() & BYTEBUF) > 0;
  }

  //TO STRING
  /**
   * Decodes the resource type. This is primarily for debugging.
   * @param typeId the given typeId
   * @return a human readable string.
   */
  public static final String typeDecode(final int typeId) {
    final StringBuilder sb = new StringBuilder();
    final int group1 = typeId & 0x7;
    switch (group1) {
      case 1 : sb.append("ReadOnly, "); break;
      case 2 : sb.append("Region, "); break;
      case 3 : sb.append("ReadOnly Region, "); break;
      case 4 : sb.append("Duplicate, "); break;
      case 5 : sb.append("ReadOnly Duplicate, "); break;
      case 6 : sb.append("Region Duplicate, "); break;
      case 7 : sb.append("ReadOnly Region Duplicate, "); break;
      default: break;
    }
    final int group2 = (typeId >>> 3) & 0x3;
    switch (group2) {
      case 0 : sb.append("Heap, "); break;
      case 1 : sb.append("Direct, "); break;
      case 2 : sb.append("Map, "); break;
      default: break;
    }
    if ((typeId & BYTEBUF) > 0) { sb.append("ByteBuffer, "); }
    final int group3 = (typeId >>> 5) & 0x1;
    switch (group3) {
      case 0 : sb.append("Native, "); break;
      case 1 : sb.append("NonNative, "); break;
      default: break;
    }
    final int group4 = (typeId >>> 6) & 0x1;
    switch (group4) {
      case 0 : sb.append("Memory"); break;
      case 1 : sb.append("Buffer"); break;
      default: break;
    }
    return sb.toString();
  }

  @Override
  public final String toHexString(final String header, final long offsetBytes,
      final int lengthBytes) {
    final String klass = this.getClass().getSimpleName();
    final String s1 = String.format("(..., %d, %d)", offsetBytes, lengthBytes);
    final long hcode = hashCode() & 0XFFFFFFFFL;
    final String call = ".toHexString" + s1 + ", hashCode: " + hcode;
    final StringBuilder sb = new StringBuilder();
    sb.append("### ").append(klass).append(" SUMMARY ###").append(LS);
    sb.append("Header Comment      : ").append(header).append(LS);
    sb.append("Call Parameters     : ").append(call);
    return toHex(this, sb.toString(), offsetBytes, lengthBytes);
  }

  /**
   * Returns a formatted hex string of an area of this object.
   * Used primarily for testing.
   * @param state the BaseStateImpl
   * @param preamble a descriptive header
   * @param offsetBytes offset bytes relative to the MemoryImpl start
   * @param lengthBytes number of bytes to convert to a hex string
   * @return a formatted hex string in a human readable array
   */
  static final String toHex(final BaseStateImpl state, final String preamble, final long offsetBytes,
      final int lengthBytes) {
    final MemorySegment seg = state.seg;
    final long capacity = seg.byteSize();
    checkBounds(offsetBytes, lengthBytes, capacity);
    final StringBuilder sb = new StringBuilder();
    final String segHCStr = "" + (seg.hashCode() & 0XFFFFFFFFL);
    final MemoryRequestServer memReqSvr = state.getMemoryRequestServer();
    final String memReqStr = memReqSvr != null
        ? memReqSvr.getClass().getSimpleName() + ", " + (memReqSvr.hashCode() & 0XFFFFFFFFL)
        : "null";
    sb.append(preamble).append(LS);
    sb.append("MemorySegment hashCode : ").append(segHCStr).append(LS);
    sb.append("Capacity               : ").append(capacity).append(LS);
    sb.append("MemReqSvr, hashCode    : ").append(memReqStr).append(LS);
    sb.append("Read Only              : ").append(state.isReadOnly()).append(LS);
    sb.append("Type Byte Order        : ").append(state.getTypeByteOrder().toString()).append(LS);
    sb.append("Native Byte Order      : ").append(ByteOrder.nativeOrder().toString()).append(LS);
    sb.append("JDK Runtime Version    : ").append(JDK).append(LS);
    //Data detail
    sb.append("Data, littleEndian  :  0  1  2  3  4  5  6  7");

    for (long i = 0; i < lengthBytes; i++) {
      final int b = getByteAtOffset(seg, offsetBytes + i) & 0XFF;
      if (i % 8 == 0) { //row header
        sb.append(String.format("%n%20s: ", offsetBytes + i));
      }
      sb.append(String.format("%02x ", b));
    }
    sb.append(LS);

    return sb.toString();
  }

  /**
   * Returns first two number groups of the java version string.
   * @param jdkVer the java version string from System.getProperty("java.version").
   * @return first two number groups of the java version string.
   */
  public static int[] parseJavaVersion(final String jdkVer) {
    final int p0, p1;
    try {
      String[] parts = jdkVer.trim().split("[^0-9\\.]");//grab only number groups and "."
      parts = parts[0].split("\\."); //split out the number groups
      p0 = Integer.parseInt(parts[0]); //the first number group
      p1 = (parts.length > 1) ? Integer.parseInt(parts[1]) : 0; //2nd number group, or 0
    } catch (final NumberFormatException | ArrayIndexOutOfBoundsException  e) {
      throw new IllegalArgumentException("Improper Java -version string: " + jdkVer + "\n" + e);
    }
    checkJavaVersion(jdkVer, p0, p1);
    return new int[] {p0, p1};
  }

  public static void checkJavaVersion(final String jdkVer, final int p0, final int p1) {
    if ( (p0 < 1) || ((p0 == 1) && (p1 < 8)) || (p0 > 13)  ) {
      throw new IllegalArgumentException(
          "Unsupported JDK Major Version, must be 17; " + jdkVer);
    }
  }

}
