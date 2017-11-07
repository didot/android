/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package trebuchet.model

import trebuchet.model.fragments.ModelFragment

class Model constructor(fragments: Iterable<ModelFragment>) {
    val processes: List<ProcessModel>
    val beginTimestamp: Double
    val endTimestamp: Double
    val duration get() = endTimestamp - beginTimestamp

    init {
        val processBuilder = mutableListOf<ProcessModel>()
        var beginTimestamp = Double.MAX_VALUE
        var endTimestamp = 0.0
        fragments.forEach {
            it.autoCloseOpenSlices()
            beginTimestamp = minOf(beginTimestamp, it.globalStartTime)
            endTimestamp = maxOf(endTimestamp, it.globalEndTime)
            it.processes.forEach {
                if (it.id != InvalidId) {
                    // TODO: Merge
                    processBuilder.add(ProcessModel(this, it))
                }
            }
        }
        processBuilder.sortBy { it.id }
        processes = processBuilder
        this.beginTimestamp = minOf(beginTimestamp, endTimestamp)
        this.endTimestamp = endTimestamp
    }

    constructor(fragment: ModelFragment) : this(listOf(fragment))

    fun isEmpty(): Boolean = processes.isEmpty()
}