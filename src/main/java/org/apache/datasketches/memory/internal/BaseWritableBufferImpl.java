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

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Objects;

import org.apache.datasketches.memory.Buffer;
import org.apache.datasketches.memory.Memory;
import org.apache.datasketches.memory.MemoryRequestServer;
import org.apache.datasketches.memory.ReadOnlyException;
import org.apache.datasketches.memory.WritableBuffer;
import org.apache.datasketches.memory.WritableMemory;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

/*
 * Developer notes: The heavier methods, such as put/get arrays, duplicate, region, clear, fill,
 * compareTo, etc., use hard checks (check*() and incrementAndCheck*() methods), which execute at
 * runtime and throw exceptions if violated. The cost of the runtime checks are minor compared to
 * the rest of the work these methods are doing.
 *
 * <p>The light weight methods, such as put/get primitives, use asserts (assert*() and
 * incrementAndAssert*() methods), which only execute when asserts are enabled and JIT will remove
 * them entirely from production runtime code. The offset versions of the light weight methods will
 * simplify to a single unsafe call, which is further simplified by JIT to an intrinsic that is
 * often a single CPU instruction.
 */

/**
 * Common base of native-ordered and non-native-ordered {@link WritableBuffer} implementations.
 * Contains methods which are agnostic to the byte order.
 */
@SuppressWarnings("restriction")
public abstract class BaseWritableBufferImpl extends BaseBufferImpl implements WritableBuffer {

  //Pass-through ctor
  BaseWritableBufferImpl(
      final MemorySegment seg,
      final int typeId) {
    super(seg, typeId);
  }

  //HEAP

  public static WritableBuffer wrapSegment(
      final MemorySegment seg,
      final ByteOrder byteOrder,
      final MemoryRequestServer memReqSvr) {
    int type = BUFFER
        | (seg.isReadOnly() ? READONLY : 0);
    if (byteOrder == ByteOrder.nativeOrder()) {
      type |= NATIVE;
      return new HeapWritableBufferImpl(seg, type, memReqSvr);
    }
    type |= NONNATIVE;
    return new HeapNonNativeWritableBufferImpl(seg, type, memReqSvr);
  }


  //BYTE BUFFER

  public static WritableBuffer wrapByteBuffer(
      final ByteBuffer byteBuffer,
      final boolean localReadOnly,
      final ByteOrder byteOrder) {
    ByteBuffer byteBuf = localReadOnly ? byteBuffer.asReadOnlyBuffer() : byteBuffer;
    MemorySegment seg = MemorySegment.ofByteBuffer(byteBuf); //from Position to limit
    int type = BUFFER | BYTEBUF
        | (localReadOnly ? READONLY : 0)
        | (seg.isMapped() ? MAP : 0)
        | (seg.isNative() ? DIRECT : 0);
    if (byteOrder == ByteOrder.nativeOrder()) {
      type |= NATIVE;
      return new BBWritableBufferImpl(seg, type);
    }
    type |= NONNATIVE;
    return new BBNonNativeWritableBufferImpl(seg, type);
  }

  //MAP
  /**
   * The static constructor that chooses the correct Map leaf node based on the byte order.
   * @param file the file being wrapped. It must be non-null with length &gt; 0.
   * @param fileOffsetBytes the file offset bytes
   * @param capacityBytes the requested capacity of the memory mapped region. It must be &gt; 0
   * @param localReadOnly the requested read-only state
   * @param byteOrder the requested byte-order
   * @return this class constructed via the leaf node.
   * @throws Exception
   */
  @SuppressWarnings("resource")
  public static WritableBuffer wrapMap(final File file, final long fileOffsetBytes,
      final long capacityBytes, final boolean localReadOnly, final ByteOrder byteOrder)
      throws Exception {
    Objects.requireNonNull(file, "File must be non-null");
    Objects.requireNonNull(byteOrder, "ByteOrder must be non-null");
    FileChannel.MapMode mapMode = (localReadOnly) ? READ_ONLY : READ_WRITE;
    MemorySegment seg;
    try {
      seg = MemorySegment.mapFile(file.toPath(), fileOffsetBytes, capacityBytes, mapMode,
            ResourceScope.newConfinedScope()); }
    catch (final IllegalArgumentException | IllegalStateException | UnsupportedOperationException
        | IOException | SecurityException e) { throw e; }
    final boolean nativeBOType = Util.isNativeByteOrder(byteOrder);
    final int type = BUFFER | MAP | DIRECT
        | (localReadOnly ? READONLY : 0)
        | (nativeBOType ? NATIVE : NONNATIVE);
    return Util.isNativeByteOrder(byteOrder)
        ? new MapWritableBufferImpl(seg, type)
        : new MapNonNativeWritableBufferImpl(seg, type);
  }

  //NO DIRECTS
  //REGIONS

  @Override
  public Buffer region(final long offsetBytes, final long capacityBytes, final ByteOrder byteOrder) {
    return regionImpl(offsetBytes, capacityBytes, true, byteOrder);
  }

  @Override
  public WritableBuffer writableRegion(final long offsetBytes, final long capacityBytes, final ByteOrder byteOrder) {
    if (this.isReadOnly()) {
      throw new ReadOnlyException("Cannot create a writable region from a read-only Memory.");
    }
    return regionImpl(offsetBytes, capacityBytes, false, byteOrder);
  }

  private WritableBuffer regionImpl(
      final long offsetBytes,
      final long capacityBytes,
      final boolean localReadOnly,
      final ByteOrder byteOrder) {
    Objects.requireNonNull(byteOrder, "byteOrder must be non-null.");
    final boolean readOnly = isReadOnly() || localReadOnly || seg.isReadOnly();
    final MemorySegment slice = seg.asSlice(offsetBytes, capacityBytes);
    final boolean duplicateType = isDuplicateType();
    final boolean mapType = seg.isMapped();
    final boolean directType = seg.isNative();
    final boolean nativeBOType = Util.isNativeByteOrder(byteOrder);
    final boolean byteBufferType = isByteBufferType();
    final int type = BUFFER | REGION
        | (readOnly ? READONLY : 0)
        | (duplicateType ? DUPLICATE : 0)
        | (mapType ? MAP : 0)
        | (directType ? DIRECT : 0)
        | (nativeBOType ? NATIVE : NONNATIVE)
        | (byteBufferType ? BYTEBUF : 0);
    if (byteBufferType) {
      if (nativeBOType) { return new BBWritableBufferImpl(slice, type); }
      return new BBNonNativeWritableBufferImpl(slice, type);
    }
    if (mapType) {
      if (nativeBOType) { return new MapWritableBufferImpl(slice, type); }
      return new MapNonNativeWritableBufferImpl(slice, type);
    }
    if (directType) {
      if (nativeBOType) { return new DirectWritableBufferImpl(slice, type, memReqSvr); }
      return new DirectNonNativeWritableBufferImpl(slice, type, memReqSvr);
    }
    //else heap type
    if (nativeBOType) { return new HeapWritableBufferImpl(slice, type, memReqSvr); }
    return new HeapNonNativeWritableBufferImpl(slice, type, memReqSvr);
  }

  //DUPLICATES
  @Override
  public Buffer duplicate() {
    return writableDuplicateImpl(true, getTypeByteOrder());
  }

  @Override
  public Buffer duplicate(final ByteOrder byteOrder) {
    return writableDuplicateImpl(true, byteOrder);
  }

  @Override
  public WritableBuffer writableDuplicate() {
    if (isReadOnly()) {
      throw new ReadOnlyException("Cannot create a writable duplicate from a read-only Buffer.");
    }
    return writableDuplicateImpl(false, getTypeByteOrder());
  }

  @Override
  public WritableBuffer writableDuplicate(final ByteOrder byteOrder) {
    if (isReadOnly()) {
      throw new ReadOnlyException("Cannot create a writable duplicate from a read-only Buffer.");
    }
    return writableDuplicateImpl(false, byteOrder);
  }

  private WritableBuffer writableDuplicateImpl(final boolean localReadOnly, final ByteOrder byteOrder) {
    final boolean readOnly = isReadOnly() || localReadOnly;
    final WritableBuffer wbuf = toDuplicate(readOnly, byteOrder);
    wbuf.setStartPositionEnd(getStart(), getPosition(), getEnd());
    return wbuf;
  }

  abstract BaseWritableBufferImpl toDuplicate(boolean readOnly, ByteOrder byteOrder);

  //AS MEMORY
  @Override
  public Memory asMemory(final ByteOrder byteOrder) {
    return asWritableMemory(true, byteOrder);
  }

  @Override
  public WritableMemory asWritableMemory(final ByteOrder byteOrder) {
    if (isReadOnly()) {
      throw new ReadOnlyException(
          "Cannot create a writable Memory from a read-only Buffer.");
    }
    return asWritableMemory(false, byteOrder);
  }

  private WritableMemory asWritableMemory(final boolean localReadOnly, final ByteOrder byteOrder) {
    Objects.requireNonNull(byteOrder, "byteOrder must be non-null");
    final boolean readOnly = isReadOnly() || localReadOnly;
    final boolean duplicateType = isDuplicateType();
    final boolean mapType = seg.isMapped();
    final boolean directType = seg.isNative();
    final boolean nativeBOType = Util.isNativeByteOrder(byteOrder);
    final boolean byteBufferType = isByteBufferType();
    final int type = MEMORY
        | (readOnly ? READONLY : 0)
        | (duplicateType ? DUPLICATE : 0)
        | (mapType ? MAP : 0)
        | (directType ? DIRECT : 0)
        | (nativeBOType ? NATIVE : NONNATIVE)
        | (byteBufferType ? BYTEBUF : 0);
    WritableMemory wmem;
    if (byteBufferType) {
      if (nativeBOType) {
        wmem = new BBWritableMemoryImpl(seg, type);
      } else {
        wmem = new BBNonNativeWritableMemoryImpl(seg, type);
      }
    }
    if (mapType) {
      if (nativeBOType) {
        wmem = new MapWritableMemoryImpl(seg, type);
      } else {
        wmem = new MapNonNativeWritableMemoryImpl(seg, type);
      }
    }
    if (directType) {
      if (nativeBOType) {
        wmem = new DirectWritableMemoryImpl(seg, type, memReqSvr);
      } else {
        wmem = new DirectNonNativeWritableMemoryImpl(seg, type, memReqSvr);
      }
    }
    //else heap type
    if (nativeBOType) {
      wmem = new HeapWritableMemoryImpl(seg, type, memReqSvr);
    } else {
      wmem = new HeapNonNativeWritableMemoryImpl(seg, type, memReqSvr);
    }
    return wmem;
  }

  //PRIMITIVE getX() and getXArray()

  @Override
  public final byte getByte() {
    final long pos = getPosition();
    final byte aByte = MemoryAccess.getByteAtOffset(seg, pos);
    setPosition(pos + Byte.BYTES);
    return aByte;
  }

  @Override
  public final byte getByte(final long offsetBytes) {
    return MemoryAccess.getByteAtOffset(seg, offsetBytes);
  }

  @Override
  public final void getByteArray(final byte[] dstArray, final int dstOffsetBytes,
      final int lengthBytes) {
    MemorySegment dstSlice = MemorySegment.ofArray(dstArray).asSlice(dstOffsetBytes, lengthBytes);
    final long pos = getPosition();
    MemorySegment srcSlice = seg.asSlice(pos, lengthBytes);
    dstSlice.copyFrom(srcSlice);
    setPosition(pos + lengthBytes);
  }

  //PRIMITIVE getX() Native Endian (used by both endians)

  final char getNativeOrderedChar() {
    final long pos = getPosition();
    final char aChar = MemoryAccess.getCharAtOffset(seg, pos);
    setPosition(pos + Character.BYTES);
    return aChar;
  }

  final char getNativeOrderedChar(final long offsetBytes) {
    return MemoryAccess.getCharAtOffset(seg, offsetBytes);
  }

  final int getNativeOrderedInt() {
    final long pos = getPosition();
    final int anInt = MemoryAccess.getIntAtOffset(seg, pos);
    setPosition(pos + Integer.BYTES);
    return anInt;
  }

  final int getNativeOrderedInt(final long offsetBytes) {
    return MemoryAccess.getIntAtOffset(seg, offsetBytes);
  }

  final long getNativeOrderedLong() {
    final long pos = getPosition();
    final long aLong = MemoryAccess.getLongAtOffset(seg, pos);
    setPosition(pos + Long.BYTES);
    return aLong;
  }

  final long getNativeOrderedLong(final long offsetBytes) {
    return MemoryAccess.getLongAtOffset(seg, offsetBytes);
  }

  final short getNativeOrderedShort() {
    final long pos = getPosition();
    final short aShort = MemoryAccess.getShortAtOffset(seg, pos);
    setPosition(pos + Short.BYTES);
    return aShort;
  }

  final short getNativeOrderedShort(final long offsetBytes) {
    return MemoryAccess.getShortAtOffset(seg, offsetBytes);
  }

  //OTHER PRIMITIVE READ METHODS: copyTo, compareTo
  @Override
  public final int compareTo(final long thisOffsetBytes, final long thisLengthBytes,
      final Buffer that, final long thatOffsetBytes, final long thatLengthBytes) {
    return CompareAndCopy.compare(seg, thisOffsetBytes, thisLengthBytes,
        ((BaseStateImpl)that).seg, thatOffsetBytes, thatLengthBytes);
  }

  /*
   * Develper notes: There is no copyTo for Buffers because of the ambiguity of what to do with
   * the positional values. Switch to MemoryImpl view to do copyTo.
   */

  //PRIMITIVE putX() and putXArray() implementations

  @Override
  public final void putByte(final byte value) {
    final long pos = getPosition();
    MemoryAccess.setByteAtOffset(seg, pos, value);
    setPosition(pos + Byte.BYTES);
  }

  @Override
  public final void putByte(final long offsetBytes, final byte value) {
    MemoryAccess.setByteAtOffset(seg, offsetBytes, value);
  }

  @Override
  public final void putByteArray(final byte[] srcArray, final int srcOffsetBytes,
      final int lengthBytes) {
    MemorySegment srcSlice = MemorySegment.ofArray(srcArray).asSlice(srcOffsetBytes, lengthBytes);
    final long pos = getPosition();
    MemorySegment dstSlice = seg.asSlice(pos, lengthBytes);
    dstSlice.copyFrom(srcSlice);
    setPosition(pos + lengthBytes);
  }

  //PRIMITIVE putX() Native Endian (used by both endians)
  final void putNativeOrderedChar(final char value) {
    final long pos = getPosition();
    MemoryAccess.setCharAtOffset(seg, pos, value);
    setPosition(pos + Character.BYTES);
  }

  final void putNativeOrderedChar(final long offsetBytes, final char value) {
    MemoryAccess.setCharAtOffset(seg, offsetBytes, value);
  }

  final void putNativeOrderedInt(final int value) {
    final long pos = getPosition();
    MemoryAccess.setIntAtOffset(seg, pos, value);
    setPosition(pos + Integer.BYTES);
  }

  final void putNativeOrderedInt(final long offsetBytes, final int value) {
    MemoryAccess.setIntAtOffset(seg, offsetBytes, value);
  }

  final void putNativeOrderedLong(final long value) {
    final long pos = getPosition();
    MemoryAccess.setLongAtOffset(seg, pos, value);
    setPosition(pos + Long.BYTES);
  }

  final void putNativeOrderedLong(final long offsetBytes, final long value) {
    MemoryAccess.setLongAtOffset(seg, offsetBytes, value);
  }

  final void putNativeOrderedShort(final short value) {
    final long pos = getPosition();
    MemoryAccess.setShortAtOffset(seg, pos, value);
    setPosition(pos + Short.BYTES);
  }

  final void putNativeOrderedShort(final long offsetBytes, final short value) {
    MemoryAccess.setShortAtOffset(seg, offsetBytes, value);
  }

  //OTHER

  @Override
  public final void clear() {
    seg.fill((byte)0);
  }

  @Override
  public final void fill(final byte value) {
    seg.fill(value);
  }
}
