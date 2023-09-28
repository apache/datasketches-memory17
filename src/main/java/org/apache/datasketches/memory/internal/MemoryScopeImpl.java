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

import java.lang.ref.Cleaner;

import org.apache.datasketches.memory.MemoryScope;

import jdk.incubator.foreign.ResourceScope;

/**
 * Implementation of MemoryScope
 */
@SuppressWarnings("resource")
public class MemoryScopeImpl extends MemoryScope {
  private ResourceScope resourceScope;

  /**
   * Constructor
   * @param resourceScope the given ResourceScope.
   */
  public MemoryScopeImpl(final ResourceScope resourceScope) {
    this.resourceScope = resourceScope;
  }

  /**
   * Gets the GlobalScope
   * @return the MemoryScope
   */
  public static MemoryScope getGlobalScope() {
    return new MemoryScopeImpl(ResourceScope.globalScope());
  }

  /**
   * Gets a new ConfinedScope
   * @return the MemoryScope
   */
  public static MemoryScope getNewConfinedScope() {
    return new MemoryScopeImpl(ResourceScope.newConfinedScope());
  }

  /**
   * Gets a new ConfinedScope with Cleaner
   * @param cleaner the given Cleaner
   * @return the MemoryScope
   */
  public static MemoryScope getNewConfinedScope(final Cleaner cleaner) {
    return new MemoryScopeImpl(ResourceScope.newConfinedScope(cleaner));
  }

  /**
   * Gets a new SharedScope
   * @return the MemoryScope
   */
  public static MemoryScope getNewSharedScope() {
    return new MemoryScopeImpl(ResourceScope.newSharedScope());
  }

  /**
   * Gets a new SharedScope with Cleaner
   * @param cleaner the given Cleaner
   * @return the MemoryScope
   */
  public static MemoryScope getNewSharedScope(final Cleaner cleaner) {
    return new MemoryScopeImpl(ResourceScope.newSharedScope(cleaner));
  }

  @Override
  public Handle acquire() {
    return new HandleImpl();
  }

  @Override
  public void addCloseAction(final Runnable runnable) {
    resourceScope.addCloseAction(runnable);
  }

  @Override
  public void close() {
    if (resourceScope != null) {
    resourceScope.close();
    }
  }

  /**
   * Returns the underlying <i>ResourceScope</i>.
   * @return the underlying <i>ResourceScope</i>.
   */
  public ResourceScope getResourceScope() {
    return resourceScope;
  }

  @Override
  public boolean isAlive() {
    return resourceScope.isAlive();
  }

  @Override
  public boolean isImplicit() {
    return resourceScope.isImplicit();
  }

  @Override
  public Thread ownerThread() {
    return resourceScope.ownerThread();
  }

  @Override
  public void release(final MemoryScope.Handle handle) {
    handle.release(handle);
  }

  /**
   * Implements a handle for this <i>MemoryScope</i>.
   */
  public class HandleImpl extends MemoryScope.Handle {
    private ResourceScope.Handle myResourceHandle;

    /**
     * Constructor
     */
    public HandleImpl() {
      this.myResourceHandle = resourceScope.acquire();
    }

    @Override
    public MemoryScopeImpl scope() { return MemoryScopeImpl.this; }

    @Override
    public void release(final MemoryScope.Handle handle) {
      if (((MemoryScopeImpl.HandleImpl) handle).myResourceHandle == myResourceHandle) {
        resourceScope.release(myResourceHandle);
      }
    }
  }

}
