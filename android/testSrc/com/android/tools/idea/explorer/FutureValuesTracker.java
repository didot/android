/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.explorer;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Utility class that enables unit tests to consume values produced asynchronously.
 * The class behaves like a FIFO queue, with the caveat that the removing an element
 * from the queue is non-blocking and returns a {@link ListenableFuture} that completes
 * when an element is added to the queue.
 */
public class FutureValuesTracker<V> {
  private final Object LOCK = new Object();
  private final Queue<V> myValues = new LinkedList<>();
  private final Queue<SettableFuture<V>> myWaitingFutures = new LinkedList<>();

  /**
   * Makes a new value available
   */
  public void produce(V value) {
    synchronized (LOCK) {
      // Look for a non-cancelled future
      while (myWaitingFutures.size() >= 1) {
        SettableFuture<V> future = myWaitingFutures.remove();
        if (future.set(value)) {
          return;
        }
      }

      // If none found, enqueue the value for later
      myValues.add(value);
    }
  }

  /**
   * Returns a {@link ListenableFuture} that completes when the next value
   * is made available.
   */
  public ListenableFuture<V> consume() {
    synchronized (LOCK) {
      // Look for available value
      if (myValues.size() >= 1) {
        V value = myValues.remove();
        return Futures.immediateFuture(value);
      }

      // Otherwise enqueue a future
      SettableFuture<V> futureResult = SettableFuture.create();
      myWaitingFutures.add(futureResult);
      return futureResult;
    }
  }

  public List<ListenableFuture<V>> consumeMany(@SuppressWarnings("SameParameterValue") int count) {
    List<ListenableFuture<V>> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      result.add(consume());
    }
    return result;
  }
}
