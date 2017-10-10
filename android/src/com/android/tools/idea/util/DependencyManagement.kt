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
@file:JvmName("DependencyManagementUtil")

package com.android.tools.idea.util

import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Returns true iff the dependency with [artifactId] is transitively available to the [sourceContext].
 * @param artifactId the dependency's maven artifact id.
 * @param sourceContext location of the dependent.
 */
fun Project.hasDependency(artifactId: GoogleMavenArtifactId, sourceContext: VirtualFile): Boolean {
  return getProjectSystem().getResolvedVersion(artifactId, sourceContext) != null
}