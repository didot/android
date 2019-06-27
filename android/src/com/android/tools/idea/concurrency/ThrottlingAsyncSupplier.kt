/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.concurrency

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.concurrent.transform
import com.android.utils.concurrency.AsyncSupplier
import com.android.utils.concurrency.CachedAsyncSupplier
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD
import com.intellij.util.AlarmFactory
import org.jetbrains.annotations.TestOnly
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference


/**
 * A caching [AsyncSupplier] which ensures an expensive-to-compute value isn't computed more than once in
 * a set period. It makes sense to use a [ThrottlingAsyncSupplier] when you expect [AsyncSupplier.get]
 * to be called more frequently than what's reasonable given time or resource constraints.
 *
 * [ThrottlingAsyncSupplier] works by delaying computation in order to merge rapid successions of
 * [AsyncSupplier.get] requests. It guarantees that the computed value reflects the state of the world
 * at or after the time that [AsyncSupplier.get] was called. This is in contrast to [CachedAsyncSupplier],
 * for which [AsyncSupplier.get] may return the future corresponding to an already in-progress computation.
 */
class ThrottlingAsyncSupplier<V : Any>(
  private val compute: () -> V,
  @AnyThread private val isUpToDate: (value: V) -> Boolean,
  private val mergingPeriod: Duration
) : AsyncSupplier<V>,
    Disposable,
    ModificationTracker {

  private val alarm = AlarmFactory.getInstance().create(POOLED_THREAD, this)
  private val scheduledComputation = AtomicReference<Computation<V>?>(null)
  private val lastComputation = AtomicReference<Computation<V>?>(null)

  private var updateCallback: Runnable? = null

  @TestOnly
  fun setUpdateCallback(callback: Runnable?) {
    updateCallback = callback
  }

  override fun dispose() {
    updateCallback = null
  }

  override fun getModificationCount() = lastComputation.get()?.let { it.modificationCountWhenScheduled + 1 } ?: -1L

  override val now: V?
    get() = lastComputation.get()?.getResultNow()

  override fun get(): ListenableFuture<V> {
    val cachedComputation = lastComputation.get()
    val cachedValue = cachedComputation?.getResultNow()
    if (cachedValue != null && isUpToDate(cachedValue)) {
      return Futures.immediateFuture(cachedValue)
    }
    val computation = Computation<V>(modificationCount)
    val scheduled = scheduledComputation.updateAndGet { it ?: computation }
    if (scheduled === computation) {
      // Our thread won and our computation is considered scheduled. Let's schedule it:
      alarm.addRequest(this::runScheduledComputation, determineDelay(cachedComputation))
    }
    return scheduled!!.getResult()
  }

  private fun runScheduledComputation() {
    // After this point, any subsequent calls to get() will be merged into a newly-scheduled computation.
    // The Alarm uses a bounded thread pool of size 1, so runScheduledComputation() is never running in
    // more than one thread at a time. Since this is the only place scheduledComputation is set to null,
    // we know the computation is non-null.
    val computation = scheduledComputation.getAndSet(null)!!
    // It's possible that this computation was scheduled while we were in the middle of
    // another computation. In that case, we haven't yet checked the freshness of the result
    // of the first computation, so we should see if it's still valid before recomputing.
    val cachedComputation = lastComputation.get()
    if (cachedComputation != null
        && cachedComputation.modificationCountWhenScheduled == computation.modificationCountWhenScheduled
        && isUpToDate(cachedComputation.getResultNow())) {
      computation.complete(cachedComputation.getResultNow())
      computation.broadcastResult()
      return
    }
    // Set lastComputation before broadcasting the result since we don't know how long
    // that will take and we want to offer getNow() callers as fresh a value as possible.
    computation.complete(compute())
    lastComputation.set(computation)
    computation.broadcastResult()
    updateCallback?.run()
  }

  private fun determineDelay(lastComputation: Computation<V>?): Long {
    lastComputation ?: return mergingPeriod.toMillis()
    val timeSinceLastComputation = System.currentTimeMillis() - lastComputation.getCompletionTimestamp()
    return (mergingPeriod.toMillis() - timeSinceLastComputation).coerceAtLeast(0L)
  }
}

private data class ValueWithTimestamp<V>(val value: V, val timestampMs: Long)

private class Computation<V>(val modificationCountWhenScheduled: Long) {
  private val result = AtomicReference<ValueWithTimestamp<V>>(null)
  private val future = SettableFuture.create<ValueWithTimestamp<V>>()!!

  private fun getResultAndCheckComplete(): ValueWithTimestamp<V> {
    return result.get() ?: throw IllegalStateException("This Computation hasn't been executed yet.")
  }

  fun complete(result: V) {
    val resultWithTimestamp = ValueWithTimestamp(result, System.currentTimeMillis())
    if (!this.result.compareAndSet(null, resultWithTimestamp)) {
      throw IllegalStateException("This Computation has already been executed.")
    }
  }

  fun broadcastResult() {
    future.set(getResultAndCheckComplete())
  }

  fun getResult() = Futures.nonCancellationPropagating(future.transform { it.value })!!

  fun getResultNow() = getResultAndCheckComplete().value

  fun getCompletionTimestamp() = getResultAndCheckComplete().timestampMs
}