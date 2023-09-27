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

import jdk.incubator.foreign.ResourceScope;

/**
 * A wrapper around jdk.incubator.foreign.ResourceScope
 */
@SuppressWarnings("resource")
public final class MemoryScope {
  private ResourceScope resourceScope;

  private MemoryScope(final ResourceScope resourceScope) {
    this.resourceScope = resourceScope;
  }

  /**
   * Acquires a <i>MemoryScope.Handle</i> associated with this <i>MemoryScope</i>.
   * The underlying explicit <i>ResourceScope</i> cannot be closed until all the scope handles
   * acquired from it have been <i>release(Handle)</i> released.
   * @return a <i>MemoryScope.Handle</i>.
   */
  public Handle acquire() {
    return new Handle();
  }

  /**
   * Add a custom cleanup action which will be executed when the underlying <i>ResourceScope</i> is closed.
   * The order in which custom cleanup actions are invoked once the scope is closed is unspecified.
   *
   * @param runnable the custom cleanup action to be associated with this scope.
   * @throws IllegalStateException if this scope has already been closed.
   */
  public void addCloseAction(final Runnable runnable) {
    resourceScope.addCloseAction(runnable);
  }

  /**
   * Closes this the underlying <i>ResourceScope</i>.
   */
  public void close() {
    if (resourceScope != null) {
    resourceScope.close();
    }
  }

  /**
   * Returns the underlying <i>ResourceScope</i>.
   * @return the underlying <i>ResourceScope</i>.
   */
  ResourceScope getResourceScope() {
    return resourceScope;
  }

  /**
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.globalScope()</i>.
   */
  public static MemoryScope globalScope() {
     return new MemoryScope(ResourceScope.globalScope());
  }

  /**
   * Is the underlying <i>ResourceScope</i> alive?
   * @return true if this resource scope is alive.
   */
  public boolean isAlive() {
    return resourceScope.isAlive();
  }

  /**
   * Is the underlying <i>ResourceScope</i> alive?
   * @return true if the underlying <i>ResourceScope</i> is alive.
   */
  public boolean isImplicit() {
    return resourceScope.isImplicit();
  }

  /**
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newConfiledScope()</i>.
   */
  public static MemoryScope newConfinedScope() {
    return new MemoryScope(ResourceScope.newConfinedScope());
  }

  /**
   * @param cleaner a user defined <i>Cleaner</i> for this scope
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newConfinedScope(Cleaner)</i>.
   */
  public static MemoryScope newConfinedScope(final Cleaner cleaner) {
    return new MemoryScope(ResourceScope.newConfinedScope(cleaner));
  }

  /**
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newSharedScope()</i>.
   */
  public static MemoryScope newSharedScope() {
    return new MemoryScope(ResourceScope.newSharedScope());
  }

  /**
   * @param cleaner a user defined <i>Cleaner</i> for this scope
   * @return a new <i>MemoryScope</i> that wraps a <i>ResourceScope.newSharedScope(Cleaner)</i>.
   */
  public static MemoryScope newSharedScope(final Cleaner cleaner) {
    return new MemoryScope(ResourceScope.newSharedScope(cleaner));
  }

  /**
   * The thread owning the underlying <i>ResourceScope</i>.
   * @return the thread owning the underlying <i>ResourceScope</i>.
   */
  public Thread ownerThread() {
    return resourceScope.ownerThread();
  }

  public void release(final MemoryScope.Handle handle) {
    if (handle.scope() == this) { handle.release(handle); }
  }

  /**
   * A handle for this <i>MemoryScope</i>.
   */
  public class Handle {
    private ResourceScope.Handle myResourceHandle;

    Handle() {
      this.myResourceHandle = resourceScope.acquire();
    }

    /**
     * Returns the <i>MemoryScope</i> associated with this handle.
     * @return the <i>MemoryScope</i> associated with this handle.
     */
    public MemoryScope scope() { return MemoryScope.this; }

    void release(final MemoryScope.Handle handle) {
      if (handle.myResourceHandle == myResourceHandle) {
        MemoryScope.this.resourceScope.release(myResourceHandle);
      }
    }
  }
}
