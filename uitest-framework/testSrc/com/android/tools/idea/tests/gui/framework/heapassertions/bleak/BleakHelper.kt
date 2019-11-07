/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

import com.intellij.testFramework.LeakHunter
import com.intellij.util.ReflectionUtil
import java.util.Vector

interface BleakHelper {
  fun traversalRoots(): Collection<Any>
  fun allLoadedClasses(): Collection<Any>
  fun pauseThreads()
  fun resumeThreads()
}

// non-JNI-based implementation so that at least something can be done without an agent or native code
class JavaBleakHelper: BleakHelper {
  override fun pauseThreads() {}
  override fun resumeThreads() {}

  // this isn't all the classes, just the ones from this class loader
  override fun allLoadedClasses(): Collection<Any> {
    val classLoader = JavaBleakHelper::class.java.classLoader
    return ReflectionUtil.getField(classLoader.javaClass, classLoader, Vector::class.java, "classes").toList()
  }

  override fun traversalRoots() = LeakHunter.allRoots().get().keys
}