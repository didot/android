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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.property.NavPropertyWrapper
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.uibuilder.property.NlPropertyItem

val NlProperty.isCustomProperty
  get() = ((this as? NavPropertyWrapper)?.isPropertyItem == true || this is NlPropertyItem) &&
          definition?.libraryName?.startsWith(GoogleMavenArtifactId.NAVIGATION_FRAGMENT.mavenGroupId) != true &&
          definition?.libraryName?.startsWith(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_FRAGMENT.mavenGroupId) != true &&
          namespace != SdkConstants.ANDROID_URI &&
          !(name == SdkConstants.ATTR_LAYOUT && namespace == SdkConstants.TOOLS_URI)

