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

package org.apache.datasketches.memory.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.datasketches.memory.BaseState;
import org.apache.datasketches.memory.DefaultMemoryRequestServer;
import org.apache.datasketches.memory.MemoryRequestServer;
import org.apache.datasketches.memory.WritableMemory;
import org.testng.annotations.Test;

import jdk.incubator.foreign.ResourceScope;

public class AllocateDirectMemoryTest {
  private final MemoryRequestServer memReqSvr = new DefaultMemoryRequestServer();

  @SuppressWarnings("resource")
  @Test
  public void simpleAllocateDirect() throws Exception {
    int longs = 32;
    WritableMemory wMem2 = null;
    try (WritableMemory wMem = WritableMemory.allocateDirect(longs << 3, memReqSvr)) {
      for (int i = 0; i<longs; i++) {
        wMem.putLong(i << 3, i);
        assertEquals(wMem.getLong(i << 3), i);
      }
      //inside the TWR block the memory scope should be alive
      assertTrue(wMem.isAlive());
      wMem2 = wMem;
    }
    //The TWR block has exited, so the memory should be invalid
    assertFalse(wMem2.isAlive());
    wMem2.close();
  }

  @Test
  public void checkMemoryRequestServer() throws Exception {
    int longs1 = 32;
    int bytes1 = longs1 << 3;
    try (WritableMemory origWmem = WritableMemory.allocateDirect(bytes1, memReqSvr)) {

      for (int i = 0; i < longs1; i++) { //puts data in origWmem
        origWmem.putLong(i << 3, i);
        assertEquals(origWmem.getLong(i << 3), i);
      }
      println(origWmem.toHexString("Test", 0, 32 * 8));

      int longs2 = 64;
      int bytes2 = longs2 << 3;
      MemoryRequestServer memReqSvr = origWmem.getMemoryRequestServer();
      if (memReqSvr == null) {
        memReqSvr = new DefaultMemoryRequestServer();
      }
      WritableMemory newWmem = memReqSvr.request(origWmem, bytes2);
      assertFalse(newWmem.isDirect()); //on heap by default
      for (int i = 0; i < longs2; i++) {
          newWmem.putLong(i << 3, i);
          assertEquals(newWmem.getLong(i << 3), i);
      }
      memReqSvr.requestClose(origWmem, newWmem);
      //The default MRS doesn't actually release because it could be easily misused.
    } // So we let the TWR release it here
  }

  @Test
  public void checkNonNativeDirect() throws Exception {
    try (WritableMemory wmem =
        WritableMemory.allocateDirect(
            128,
            8,
            ResourceScope.newConfinedScope(),
            BaseState.NON_NATIVE_BYTE_ORDER,
            memReqSvr)) {
      wmem.putChar(0, (char) 1);
      assertEquals(wmem.getByte(1), (byte) 1);
    }
  }

  @Test
  public void checkExplicitClose() throws Exception {
    final long cap = 128;
    try (WritableMemory wmem = WritableMemory.allocateDirect(cap, memReqSvr)) {
      wmem.close(); //explicit close. Does the work of closing
    } //end of scope
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
