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

import static org.apache.datasketches.memory.internal.Util.getResourceBytes;
import static org.apache.datasketches.memory.internal.Util.getResourceFile;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.File;
import java.io.IOException;

import org.apache.datasketches.memory.Memory;
import org.apache.datasketches.memory.MemoryScope;
import org.testng.annotations.Test;

/**
 * blah
 */
public class ResourceFromJarTest {

  @Test
  public void simpleMap() throws IOException {
    File file = getResourceFile("GettysburgAddress.txt");
    file.setReadOnly();
    Memory mem = null;
    try (MemoryScope scope = MemoryScope.newConfinedScope()) {
      mem = Memory.map(file, scope);
      int cap = (int)mem.getCapacity();
      byte[] byteArr = new byte[cap];
      mem.getByteArray(0, byteArr, 0, cap);
      String out = new String(byteArr);
      println(out);
      assertEquals(out.length(),1527);
    }
    assertFalse(mem.isAlive());
  }

  @Test
  public void simpleResourceToBytes() throws IOException {
    byte[] bytes = getResourceBytes("GettysburgAddress.txt");
    String out = new String(bytes);
    assertEquals(out.length(),1527);
  }

  private final static boolean enablePrinting = false;

  /**
   * @param o the Object to println
   */
  static final void println(final Object o) {
    if (enablePrinting) { System.out.println(o.toString()); }
  }
}
