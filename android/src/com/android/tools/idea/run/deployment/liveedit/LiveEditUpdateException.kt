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
package com.android.tools.idea.run.deployment.liveedit

import com.intellij.psi.PsiFile

class LiveEditUpdateException(val error: Error, val details: String = "", val source: PsiFile?, cause : Throwable?) : RuntimeException(details, cause) {

  /**
   * @param message Short description
   * @param details Detailed information of the error if available.
   * @param recoverable If this flag is flags, the current deployment of the application can no longer be live edited and
   *                    a build and re-run would be required for future live edits.
   */
  enum class Error (val message: String, val details: String = "", val recoverable: Boolean = true) {
    // Sorted lexicographically for readability and consistency
    ANALYSIS_ERROR("Resolution Analysis Error", "%", true),
    COMPILATION_ERROR("Compilation Error", "%", true),
    UNABLE_TO_INLINE("Unable to inline function", "%", true),
    UNABLE_TO_LOCATE_COMPOSE_GROUP("Unable to locate Compose Invalid Group", "%", false),
    INTERNAL_ERROR("Internal Error", "%", false),
    KNOWN_ISSUE("Known Issue", "%", true),
  }

  companion object {
    // Sorted lexicographically for readability and consistency

    fun analysisError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.ANALYSIS_ERROR, details, source, cause)

    @JvmStatic
    fun compilationError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.COMPILATION_ERROR, details, source, cause)

    fun internalError(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.INTERNAL_ERROR, details, source, cause)

    /**
     * We are unable to locate the Invalidate Group ID of a given Composable function's offsets.
     * This is unlikely to happen unless the Compose compiler changes how the offset-to-ID mapping works.
     */
    fun noInvalidateGroup(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_LOCATE_COMPOSE_GROUP, details, source, cause)

    fun inlineFailure(details: String, source: PsiFile? = null, cause: Throwable? = null) =
      LiveEditUpdateException(Error.UNABLE_TO_INLINE, "$details", source, cause)
  }
}

