/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.rendering.classloading.loadClassBytes
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

private class TestClass
private val testClassName = TestClass::class.java.canonicalName

internal class ModuleClassLoaderOverlaysTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `empty overlay does not return classes`() {
    assertNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))
  }

  @Test
  fun `overlay finds classes`() {
    // Copy the classes into a temp directory to use as overlay
    val tempOverlayPath = Files.createTempDirectory("overlayTest")
    val packageDirPath = Files.createDirectories(tempOverlayPath.resolve(TestClass::class.java.packageName.replace(".", "/")))

    ModuleClassLoaderOverlays.getInstance(projectRule.module).overlayPath = tempOverlayPath
    assertNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))

    val classFilePath = packageDirPath.resolve(TestClass::class.java.simpleName + ".class")
    Files.write(classFilePath, loadClassBytes(TestClass::class.java))
    assertNotNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))

    // If deleted, the class should disappear
    Files.delete(classFilePath)
    assertNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))
  }
}