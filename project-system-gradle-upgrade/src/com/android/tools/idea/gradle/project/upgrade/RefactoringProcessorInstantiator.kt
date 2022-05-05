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
package com.android.tools.idea.gradle.project.upgrade

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GradleVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.firstNotNullResult

private val LOG = Logger.getInstance("Upgrade Assistant")

class RefactoringProcessorInstantiator {
  /**
   * Show an appropriate dialog, and return whether the AGP upgrade should proceed by running the refactoring processor.  The
   * usual case is the return value from a dialog presenting information and options to the user, but we show a different
   * dialog if we detect that the upgrade will fail in some way.  If [preserveProcessorConfigurations] is false (the default), the
   * dialog is permitted to initialize the processors' state (whether they are enabled, and any configuration) appropriately; if it
   * is true, the processor is assumed to be already configured.
   */
  @Slow
  fun showAndGetAgpUpgradeDialog(processor: AgpUpgradeRefactoringProcessor, preserveProcessorConfigurations: Boolean = false): Boolean {
    val java8Processor = processor.componentRefactoringProcessors.firstNotNullResult { it as? Java8DefaultRefactoringProcessor }
    if (java8Processor == null) {
      LOG.error("no Java8Default processor found in AGP Upgrade Processor")
    }
    val r8FullModeProcessor = processor.componentRefactoringProcessors.firstNotNullResult { it as? R8FullModeDefaultRefactoringProcessor }
    if (r8FullModeProcessor == null) {
      LOG.error("no R8FullModeDefault processor found in AGP Upgrade Processor")
    }
    // we will need parsed models to decide what to show in the dialog.  Ensure that they are available now, while we are (in theory)
    // not on the EDT.
    processor.ensureParsedModels()
    val hasChangesInBuildFiles = !isCleanEnoughProject(processor.project)
    if (hasChangesInBuildFiles) {
      LOG.warn("changes found in project build files")
    }
    val runProcessor = invokeAndWaitIfNeeded(ModalityState.NON_MODAL) {
      if (processor.classpathRefactoringProcessor.isAlwaysNoOpForProject) {
        processor.trackProcessorUsage(UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FAILURE_PREDICTED)
        LOG.warn("cannot upgrade: classpath processor is always a no-op")
        val dialog = AgpUpgradeRefactoringProcessorCannotUpgradeDialog(processor)
        dialog.show()
        return@invokeAndWaitIfNeeded false
      }
      val dialog = AgpUpgradeRefactoringProcessorDialog(
        processor, java8Processor!!, r8FullModeProcessor!!, hasChangesInBuildFiles, preserveProcessorConfigurations
      )
      dialog.showAndGet()
    }
    return runProcessor
  }

  /**
   * Create a refactoring processor for upgrading from AGP version [current] to [new].
   */
  fun createProcessor(project: Project, current: GradleVersion, new: GradleVersion) =
    AgpUpgradeRefactoringProcessor(project, current, new)

}