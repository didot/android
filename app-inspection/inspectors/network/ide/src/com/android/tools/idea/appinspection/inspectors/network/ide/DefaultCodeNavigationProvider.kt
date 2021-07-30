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
package com.android.tools.idea.appinspection.inspectors.network.ide

import com.android.tools.idea.appinspection.inspectors.network.model.CodeNavigationProvider
import com.android.tools.inspectors.common.api.ide.stacktrace.IntellijCodeNavigator
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator
import com.android.tools.nativeSymbolizer.ProjectSymbolSource
import com.android.tools.nativeSymbolizer.SymbolFilesLocator
import com.android.tools.nativeSymbolizer.createNativeSymbolizer
import com.intellij.openapi.project.Project

/**
 * The Android Studio implementation of [CodeNavigationProvider] that provides a single
 * instance of [IntellijCodeNavigator].
 */
class DefaultCodeNavigationProvider(project: Project) : CodeNavigationProvider {
  private val locator = SymbolFilesLocator(ProjectSymbolSource(project))
  private val symbolizer = createNativeSymbolizer(locator)

  override val codeNavigator: CodeNavigator = IntellijCodeNavigator(project, symbolizer)
}