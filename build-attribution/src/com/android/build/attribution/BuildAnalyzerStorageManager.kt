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
package com.android.build.attribution

import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.data.BuildRequestHolder
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

interface BuildAnalyzerStorageManager {
  /**
   * Returns the analysis results from the latest build in the form of a BuildAnalysisResults object. There are no arguments.
   * If no build results have been stored, then an IllegalStatException is thrown as there is nothing to return.
   *
   * @return BuildAnalysisResults
   * @exception IllegalStateException
   */
  fun getLatestBuildAnalysisResults() : BuildAnalysisResults
  fun storeNewBuildResults(analyzersProxy: BuildEventsAnalyzersProxy, buildID: String, requestHolder: BuildRequestHolder)
  fun hasData() : Boolean

  interface Listener {
    fun newDataAvailable()
  }

  companion object {
    val DATA_IS_READY_TOPIC: Topic<Listener> =
      Topic.create("com.android.build.attribution.BuildAnalyzerStorageManager", Listener::class.java)

    fun getInstance(project: Project): BuildAnalyzerStorageManager {
      return project.getService(BuildAnalyzerStorageManager::class.java)
    }
  }
}