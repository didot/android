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
package org.jetbrains.android.refactoring

import com.android.annotations.VisibleForTesting
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.support.MigrationParserVisitor
import com.android.support.parseMigrationFile
import com.android.tools.idea.actions.ExportProjectZip
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.model.AndroidModuleInfo
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*
import javax.swing.JCheckBox

const val ACTION_WARNING_TEXT = """
Before proceeding, we recommend that you make a backup of your project.

Depending on your project dependencies, you might need to manually fix
some errors after the refactoring in order to successfully compile your project.

Do you want to proceed with the migration?
"""
const val RESULT_MIGRATE_WITH_BACKUP = 0
const val RESULT_MIGRATE = 1
const val RESULT_CANCEL = 2


class MigrateToAndroidxAction : BaseRefactoringAction() {

  override fun isAvailableInEditorOnly() = false

  override fun isEnabledOnDataContext(dataContext: DataContext) = true

  override fun isEnabledOnElements(elements: Array<out PsiElement>) = true

  override fun getHandler(dataContext: DataContext): RefactoringActionHandler? = MigrateToAndroidxHandler()

  override fun update(anActionEvent: AnActionEvent) {
    anActionEvent.presentation.isEnabledAndVisible = StudioFlags.MIGRATE_TO_ANDROID_X_REFACTORING_ENABLED.get()
  }

  override fun isAvailableForLanguage(language: Language) = true
}

class MigrateToAndroidxHandler : RefactoringActionHandler {

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    invoke(project, arrayOf<PsiElement>(file!!), dataContext)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    if (!checkRefactoringPrerequisites(project)) {
      return
    }

    val okCancelResult = Messages.showCheckboxMessageDialog(ACTION_WARNING_TEXT,
                                                            "Migrate to AndroidX",
                                                            arrayOf("Cancel", "Migrate"),
                                                            "Backup project as Zip file",
                                                            true,
                                                            0, 0,
                                                            Messages.getWarningIcon(),
                                                            { index: Int, checkbox: JCheckBox ->
                                                              when {
                                                                index != 1 -> RESULT_CANCEL
                                                                checkbox.isSelected -> RESULT_MIGRATE_WITH_BACKUP
                                                                else -> RESULT_MIGRATE
                                                              }
                                                            })

    when (okCancelResult) {
      RESULT_CANCEL -> return
      RESULT_MIGRATE_WITH_BACKUP -> {
        val exportZip = ExportProjectZip()
        val context = dataContext ?: SimpleDataContext.getProjectContext(project)
        ActionUtil.invokeAction(exportZip, context, "Migrate to AndroidX", null, Runnable {
          // Once the export has completed, run the migration
          runMigration(project)
        })
      }
      else -> runMigration(project)
    }
  }

  private fun runMigration(project: Project) {
    val processor = MigrateToAndroidxProcessor(project, parseMigrationMap())

    with(processor) {
      setPreviewUsages(true)
      run()
    }
  }

  /**
   *  Returns the [AndroidVersion] value for the given receiver [String] or
   *  null if it's not a valid version.
   */
  private fun String.toAndroidVersion(): AndroidVersion? = AndroidTargetHash.getPlatformVersion(this)

  /**
   * Returns the buildSdk value of a the receiver [Module] or null
   * if the [Module] buildSdk could not be found.
   */
  private fun Pair<Module, GradleBuildModel?>.findBuildSdk(): AndroidVersion? {
    val modelVersion = second?.android()
      ?.compileSdkVersion()
      ?.toString()
      ?.toAndroidVersion()

    // If we couldn't find the version from the model parser, try to get it
    // from the AndroidModuleInfo. This could happen if the version is coming
    // from a variable that the Gradle parser is not handling correctly
    return modelVersion ?: AndroidModuleInfo.getInstance(first)?.buildSdkVersion
  }

  private fun checkRefactoringPrerequisites(project: Project): Boolean {
    val buildModel = ProjectBuildModel.get(project)
    val moduleModels = ModuleManager.getInstance(project).modules
      .mapNotNull { it to buildModel.getModuleBuildModel(it) }
    val highestCompileSdkVersion = moduleModels
        .mapNotNull { it.findBuildSdk() }
        .sorted()
        .lastOrNull()
      val supportedCompileSdk = if (highestCompileSdkVersion != null) {
        AndroidVersion(28) <= highestCompileSdkVersion || highestCompileSdkVersion.codename != null
      }
      else {
        true // Enable by default when we can not find out the version
      }

      val gradleVersionString = moduleModels.mapNotNull { it.second }
        .map { it.buildscript().dependencies() }
        .flatMap { it.artifacts() }
        .filter { it.name().forceString() == "gradle" && it.group().forceString() == "com.android.tools.build" }
        .map { it.version().getValue(GradlePropertyModel.STRING_TYPE) }
        .firstOrNull()
      val supportedGradleVersion = if (gradleVersionString?.startsWith('$') == false) {
        GradleVersion.tryParse(gradleVersionString)?.isAtLeastIncludingPreviews(3, 2, 0) ?: false
      }
      else {
        // For now, ignore this case since the DSL parser does not seem to handle that correctly
        true
      }

    if (supportedCompileSdk && supportedGradleVersion) {
      return true
    }

    val warningContent = if (!supportedCompileSdk) {
      "You need to have at least have compileSdk 28 set in your module build.gradle to migrate to AndroidX.\n"
    } else {
      ""
    } + if (!supportedGradleVersion) {
      "The gradle plugin version in your project build.gradle file needs to be set to at least com.android.tools.build:gradle:3.2.0 " +
      "in order to migrate to AndroidX."
    } else {
      ""
    }

    Messages.showErrorDialog(warningContent, "Unable to migrate to AndroidX")
    return false
  }

  @VisibleForTesting
  private fun parseMigrationMap(): List<AppCompatMigrationEntry> {
    val classesAndCoordinates = mutableListOf<AppCompatMigrationEntry>()
    val packages =  mutableListOf<PackageMigrationEntry>()

    parseMigrationFile(object: MigrationParserVisitor {
      override fun visitClass(old: String, new: String) {
        classesAndCoordinates.add(ClassMigrationEntry(old, new))
      }

      override fun visitPackage(old: String, new: String) {
        packages.add(PackageMigrationEntry(old, new))
      }

      override fun visitGradleCoordinate(
        oldGroupName: String,
        oldArtifactName: String,
        newGroupName: String,
        newArtifactName: String,
        newBaseVersion: String
      ) {
        classesAndCoordinates.add(GradleDependencyMigrationEntry(oldGroupName, oldArtifactName, newGroupName, newArtifactName, newBaseVersion))
      }

      override fun visitGradleCoordinateUpgrade(groupName: String, artifactName: String, newBaseVersion: String) {
        classesAndCoordinates.add(
          UpdateGradleDepedencyVersionMigrationEntry(groupName, artifactName, newBaseVersion))
      }
    })

    // Packages need to be sorted so the refactoring is applied correctly. We need to apply
    // the longest names first so, if there are conflicting refactorings, the longest one
    // is applied first.
    packages.sortByDescending { it.myOldName.length }
    classesAndCoordinates.addAll(packages)
    return classesAndCoordinates
  }
}
