/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependencyCore
import com.android.tools.idea.gradle.model.LibraryReference
import java.io.Serializable

data class IdeJavaLibraryDependencyImpl(
  override val target: IdeJavaLibrary,
  override val isProvided: Boolean
) : IdeJavaLibraryDependency {
  val displayName: String get() = target.name
}

data class IdeJavaLibraryDependencyCoreImpl(
  override val target: LibraryReference,
  override val isProvided: Boolean
): IdeJavaLibraryDependencyCore, Serializable
