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

import java.lang.ref.Cleaner;

import org.apache.datasketches.memory.internal.MemoryScopeImpl;


/**
 * A wrapper around jdk.incubator.foreign.ResourceScope
 */
public abstract class MemoryScope {

  /**
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.globalScope()</i>.
   */
  public static MemoryScope globalScope() {
     return MemoryScopeImpl.getGlobalScope();
  }

  /**
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newConfiledScope()</i>.
   */
  public static MemoryScope newConfinedScope() {
    return MemoryScopeImpl.getNewConfinedScope();
  }

  /**
   * @param cleaner a user defined <i>Cleaner</i> for this scope
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newConfinedScope(Cleaner)</i>.
   */
  public static MemoryScope newConfinedScope(final Cleaner cleaner) {
    return MemoryScopeImpl.getNewConfinedScope(cleaner);
  }

  /**
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newSharedScope()</i>.
   */
  public static MemoryScope newSharedScope() {
    return MemoryScopeImpl.getNewSharedScope();
  }

  /**
   * @param cleaner a user defined <i>Cleaner</i> for this scope
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newSharedScope(Cleaner)</i>.
   */
  public static MemoryScope newSharedScope(final Cleaner cleaner) {
    return MemoryScopeImpl.getNewSharedScope(cleaner);
  }

  /**
   * Acquires a <i>MemoryScope.Handle</i> associated with this <i>MemoryScope</i>.
   * The underlying explicit <i>ResourceScope</i> cannot be closed until all the scope handles
   * acquired from it have been <i>release(Handle)</i> released.
   * @return a <i>MemoryScope.Handle</i>.
   */
  public abstract Handle acquire();

  /**
   * Add a custom cleanup action which will be executed when the underlying <i>ResourceScope</i> is closed.
   * The order in which custom cleanup actions are invoked once the scope is closed is unspecified.
   *
   * @param runnable the custom cleanup action to be associated with this scope.
   * @throws IllegalStateException if this scope has already been closed.
   */
  public abstract void addCloseAction(final Runnable runnable);

  /**
   * Closes this the underlying <i>ResourceScope</i>.
   */
  public abstract void close();

  /**
   * Is the underlying <i>ResourceScope</i> alive?
   * @return true if this resource scope is alive.
   */
  public abstract boolean isAlive();

  /**
   * Is the underlying <i>ResourceScope</i> alive?
   * @return true if the underlying <i>ResourceScope</i> is alive.
   */
  public abstract boolean isImplicit();

  /**
   * The thread owning the underlying <i>ResourceScope</i>.
   * @return the thread owning the underlying <i>ResourceScope</i>.
   */
  public abstract Thread ownerThread();

  /**
   * Releases the given handle
   * @param handle the given handle
   */
  public abstract void release(final MemoryScope.Handle handle);

  /**
   * A handle for this <i>MemoryScope</i>.
   */
  public abstract class Handle {

    /**
     * Returns the <i>MemoryScope</i> associated with this handle.
     * @return the <i>MemoryScope</i> associated with this handle.
     */
    public abstract MemoryScope scope();

    /**
     * Releases the given handle if valid.
     * @param handle the given handle
     */
    public abstract void release(final MemoryScope.Handle handle);

  }
}
