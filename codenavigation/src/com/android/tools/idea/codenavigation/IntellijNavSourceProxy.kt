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
package com.android.tools.idea.codenavigation

import com.android.tools.nativeSymbolizer.NativeSymbolizer
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

// TODO(vaage): Once [IntellijNavSource] has been fully broken-up, rename this [NavSource] to be
//  [IntellijNavSource].
class IntellijNavSourceProxy(project:Project, symbolizer: NativeSymbolizer): NavSource {
  private val sources = listOf(IntellijNavSource(project, symbolizer), PsiNavSource(project))

  override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
    return sources.asSequence().map { it.lookUp(location, arch) }.filterNotNull().firstOrNull()
  }
}