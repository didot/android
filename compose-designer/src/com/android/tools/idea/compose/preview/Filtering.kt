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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.google.common.annotations.VisibleForTesting

/**
 * A [PreviewElementProvider] that filters by [groupName].
 *
 * @param delegate the source [PreviewElementProvider] to be filtered.
 * @param groupName the name of the group that will be used to filter the [PreviewElement]s returned from the [delegate].
 */
@VisibleForTesting
class GroupNameFilteredPreviewProvider(private val delegate: PreviewElementProvider, var groupName: String? = null) :
  PreviewElementProvider {
  private val filteredPreviewElementProvider = FilteredPreviewElementProvider(
    delegate) {
    groupName == null || groupName == it.displaySettings.group
  }

  /**
   * Returns a [Set] with all the available groups in the source [delegate]. Only groups returned can be set on [groupName].
   */
  val availableGroups: Set<String>
    get() = delegate.previewElements.mapNotNull { it.displaySettings.group }.filter { it.isNotBlank() }.toSet()

  override val previewElements: Sequence<PreviewElement>
    get() = filteredPreviewElementProvider.previewElements.ifEmpty { delegate.previewElements }
}

/**
 *  A [PreviewElementProvider] that filters [PreviewElementInstance] by the Composable instance ID.
 *
 * @param delegate the source [PreviewElementProvider] to be filtered.
 */
@VisibleForTesting
class SinglePreviewElementInstanceFilteredPreviewProvider(private val delegate: PreviewElementProvider): PreviewElementProvider {
  /**
   * The Composable instance ID to filter. If no [PreviewElement] is defined by that name, then this filter will return all the available
   * previews.
   */
  var instanceId: String? = null

  private val filteredPreviewElementProvider = FilteredPreviewElementProvider(delegate) {
    (it as? PreviewElementInstance)?.instanceId == instanceId
  }

  override val previewElements: Sequence<PreviewElement>
    get() = filteredPreviewElementProvider.previewElements.ifEmpty { delegate.previewElements }

}