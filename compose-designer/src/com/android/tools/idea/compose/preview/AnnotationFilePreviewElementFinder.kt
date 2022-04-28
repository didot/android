package com.android.tools.idea.compose.preview

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
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.idea.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.annotations.hasAnnotatedMethods
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * [FilePreviewElementFinder] that uses `@Preview` annotations.
 */
object AnnotationFilePreviewElementFinder : FilePreviewElementFinder {
  override fun hasPreviewMethods(project: Project, vFile: VirtualFile) =
    hasAnnotatedMethods(project, vFile, setOf(COMPOSE_PREVIEW_ANNOTATION_FQN), COMPOSE_PREVIEW_ANNOTATION_NAME)

  override fun hasComposableMethods(project: Project, vFile: VirtualFile) =
    hasAnnotatedMethods(project, vFile, COMPOSABLE_FQ_NAMES, COMPOSABLE_ANNOTATION_NAME)

  /**
   * Returns all the `@Composable` functions in the [vFile] that are also tagged with `@Preview`.
   */
  override suspend fun findPreviewMethods(project: Project, vFile: VirtualFile) =
    findAnnotatedMethodsValues(project, vFile, COMPOSABLE_FQ_NAMES, COMPOSABLE_ANNOTATION_NAME, ::getPreviewElements)
}