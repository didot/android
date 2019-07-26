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
package com.android.build.attribution.analyzers

import org.gradle.tooling.events.ProgressEvent

class BuildEventsAnalyzersWrapper(val analyzers: List<BuildEventsAnalyzer>): BuildEventsAnalyzer {
  override fun onBuildStart() {
    analyzers.forEach(BuildEventsAnalyzer::onBuildStart)
  }

  override fun onBuildSuccess() {
    analyzers.forEach(BuildEventsAnalyzer::onBuildSuccess)
  }

  override fun onBuildFailure() {
    analyzers.forEach(BuildEventsAnalyzer::onBuildFailure)
  }

  override fun receiveEvent(event: ProgressEvent) {
    analyzers.forEach { it.receiveEvent(event) }
  }
}