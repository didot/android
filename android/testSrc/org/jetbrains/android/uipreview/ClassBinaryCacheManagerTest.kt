/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.google.common.base.Ticker
import com.intellij.openapi.module.Module
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClassBinaryCacheManagerTest {
  private class ManualTicker : Ticker() {
    var timeNanos = 0L
    override fun read(): Long = timeNanos
  }

  @Mock
  private lateinit var module: Module

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testPutAndGet() {
    val manager = ClassBinaryCacheManager.getTestInstance(ManualTicker(), 100, 1)

    val moduleCache = manager.getCache(module)
    moduleCache.setDependencies(listOf("A", "B"))

    moduleCache.put("a.b.c", "A", "hello".toByteArray())
    moduleCache.put("d.e.f", "B", "bye".toByteArray())

    assertEquals("hello", moduleCache.get("a.b.c")?.toString(Charsets.UTF_8))
    assertEquals("bye", moduleCache.get("d.e.f")?.toString(Charsets.UTF_8))

    val sameModuleCache = manager.getCache(module)

    assertEquals("hello", sameModuleCache.get("a.b.c")?.toString(Charsets.UTF_8))
    assertEquals("bye", sameModuleCache.get("d.e.f")?.toString(Charsets.UTF_8))
  }

  @Test
  fun testInvalidateWhenDependenciesChanged() {
    val manager = ClassBinaryCacheManager.getTestInstance(ManualTicker(), 100, 1)

    val moduleCache = manager.getCache(module)
    moduleCache.setDependencies(listOf("A"))

    moduleCache.put("a.b.c", "A", "hello".toByteArray())

    val sameModuleCache = manager.getCache(module)
    sameModuleCache.setDependencies(listOf("B"))

    assertNull(sameModuleCache.get("a.b.c"))
  }

  @Test
  fun testInvalidateWhenOverweight() {
    val manager = ClassBinaryCacheManager.getTestInstance(ManualTicker(), 100, 1)

    val moduleCache = manager.getCache(module)
    moduleCache.setDependencies(listOf("A"))

    moduleCache.put("a.b.c", "A", ByteArray(80))

    assertNotNull(moduleCache.get("a.b.c"))

    moduleCache.put("a.b.d", "A", ByteArray(80))

    assertNull(moduleCache.get("a.b.c"))
  }

  @Test
  fun testInvalidateWhenTimeout() {
    val manualTicker = ManualTicker()
    val manager = ClassBinaryCacheManager.getTestInstance(manualTicker, 100, 1)

    val moduleCache = manager.getCache(module)
    moduleCache.setDependencies(listOf("A"))

    moduleCache.put("a.b.c", "A", ByteArray(80))

    assertNotNull(moduleCache.get("a.b.c"))

    manualTicker.timeNanos += 2L * 60L * 1000_000_000L // In 2 minutes

    assertNull(moduleCache.get("a.b.c"))
  }
}