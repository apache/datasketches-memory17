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

package org.apache.datasketches.memory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import jdk.incubator.foreign.ResourceScope;

public class AllocateDirectMemoryTest {
  private final MemoryRequestServer memReqSvr = new DefaultMemoryRequestServer();

  @SuppressWarnings("resource")
  @Test
  public void simpleAllocateDirect() {
    int longs = 32;
    WritableMemory wMem = null;
    try (ResourceScope scope = (wMem = WritableMemory.allocateDirect(longs << 3, memReqSvr)).scope()) {
      for (int i = 0; i<longs; i++) {
        wMem.putLong(i << 3, i);
        assertEquals(wMem.getLong(i << 3), i);
      }
      //inside the TWR block the memory scope should be alive
      assertTrue(wMem.isAlive());
    }
    //The TWR block has exited, so the memory should be invalid
    assertFalse(wMem.isAlive());
    wMem.close();
  }

  @Test
  public void checkMemoryRequestServer() {
    int longs1 = 32;
    int bytes1 = longs1 << 3;
    WritableMemory wmem = null;
    try (ResourceScope scope = (wmem = WritableMemory.allocateDirect(bytes1, memReqSvr)).scope()) {

      for (int i = 0; i < longs1; i++) { //puts data in origWmem
        wmem.putLong(i << 3, i);
        assertEquals(wmem.getLong(i << 3), i);
      }
      println(wmem.toHexString("Test", 0, 32 * 8));

      int longs2 = 64;
      int bytes2 = longs2 << 3;
      MemoryRequestServer memReqSvr = wmem.getMemoryRequestServer();
      if (memReqSvr == null) {
        memReqSvr = new DefaultMemoryRequestServer();
      }
      WritableMemory newWmem = memReqSvr.request(wmem, bytes2);
      assertFalse(newWmem.isDirect()); //on heap by default
      for (int i = 0; i < longs2; i++) {
          newWmem.putLong(i << 3, i);
          assertEquals(newWmem.getLong(i << 3), i);
      }
      memReqSvr.requestClose(wmem, newWmem);
      //The default MRS doesn't actually release because it could be easily misused.
    } // So we let the TWR release it here
  }

  @SuppressWarnings("resource")
  @Test
  public void checkNonNativeDirect() {
    WritableMemory wmem = null;
    try (ResourceScope scope = (wmem =
        WritableMemory.allocateDirect(
            128,
            8,
            ResourceScope.newConfinedScope(),
            BaseState.NON_NATIVE_BYTE_ORDER,
            memReqSvr)).scope()) {
      wmem.putChar(0, (char) 1);
      assertEquals(wmem.getByte(1), (byte) 1);
    }
  }

  @Test
  public void checkExplicitCloseNoTWR() {
    final long cap = 128;
    WritableMemory wmem = null;
    try {
      wmem = WritableMemory.allocateDirect(cap, memReqSvr);
      wmem.close(); //explicit close
    } catch (final Exception e) {
      throw e;
    }
  }

  @Test
  public void printlnTest() {
    println("PRINTING: "+this.getClass().getName());
  }

  /**
   * @param s value to print
   */
  static void println(String s) {
    //System.out.println(s); //disable here
  }
}
