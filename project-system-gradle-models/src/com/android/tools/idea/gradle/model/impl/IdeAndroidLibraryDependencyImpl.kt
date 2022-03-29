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

import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependencyCore
import com.android.tools.idea.gradle.model.LibraryReference
import java.io.Serializable

data class IdeAndroidLibraryDependencyImpl(
  override val target: IdeAndroidLibrary,
  override val isProvided: Boolean
) : IdeAndroidLibraryDependency {
  val displayName: String get() = target.name
}

data class IdeAndroidLibraryDependencyCoreImpl(
  override val target: LibraryReference,
  override val isProvided: Boolean
): IdeAndroidLibraryDependencyCore, Serializable
