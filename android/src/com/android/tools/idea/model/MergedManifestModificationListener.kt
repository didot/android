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
package com.android.tools.idea.model

import com.android.SdkConstants
import com.android.ide.common.util.PathString
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.LazyFileListenerSubscriber
import com.android.tools.idea.util.PoliteAndroidVirtualFileListener
import com.android.tools.idea.util.listenUntilNextSync
import com.intellij.AppTopics
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.facet.isManifestFile

/**
 * A project-wide listener that determines which modules' merged manifests are affected by VFS
 * changes or Document changes and tells the corresponding [MergedManifestModificationTracker]s to
 * increment counter. [MergedManifestModificationListener] registers itself to start actively listening for
 * VFS changes and Document changes after the first project sync of the session.
 */
class MergedManifestModificationListener(
  project: Project
) : PoliteAndroidVirtualFileListener(project),
    DocumentListener,
    FileDocumentManagerListener {

  private val psiDocumentManager = PsiDocumentManager.getInstance(project)
  private val fileDocumentManager = FileDocumentManager.getInstance()

  // If a directory was deleted, we won't get a separate event for each descendant, so we
  // must let directories pass through this fail-fast filter in case they contain relevant files.
  override fun isPossiblyRelevant(file: VirtualFile) = file.isDirectory || file.extension == "xml"

  /**
   * Determines if the changed file contributes to merged manifest attributes computed using the [AndroidManifestIndex].
   * This means the file is either:
   * 1. An AndroidManifest.xml belonging to one of the containing module's source providers.
   * 2. A directory that is an ancestor of a file matching (1).
   *
   * Note that we don't care about navigation files here, since we don't use navigation files for
   * any [AndroidManifestIndex]-based attribute computations.
   *
   * @see [MergedManifestModificationTracker]
   */
  override fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean {
    if (file.name == SdkConstants.FN_ANDROID_MANIFEST_XML) return isManifestFile(facet, file)

    if (!file.isDirectory) {
      return false
    }

    return SourceProviderManager.getInstance(facet).sources.manifestFiles.any { VfsUtilCore.isAncestor(file, it, false) }
  }

  override fun contentsChanged(event: VirtualFileEvent) {
    // Content changes are not handled at the VFS level but either in fileWithNoDocumentChanged or documentChanged
  }

  override fun fileWithNoDocumentChanged(file: VirtualFile) = possiblyIrrelevantFileChanged(file)

  override fun documentChanged(event: DocumentEvent) {
    val document = event.document
    val psiFile = psiDocumentManager.getCachedPsiFile(document)

    if (psiFile == null) {
      fileDocumentManager.getFile(document)?.let { possiblyIrrelevantFileChanged(it) }
    }
    else {
      psiFile.virtualFile?.let { possiblyIrrelevantFileChanged(it) }
    }
  }

  override fun fileChanged(path: PathString, facet: AndroidFacet) = updateModificationTrackers(facet)

  private fun updateModificationTrackers(facet: AndroidFacet) {
    facet.module.getTransitiveResourceDependents().forEach {
      MergedManifestModificationTracker.getInstance(it).manifestChanged()
    }
  }

  /**
   * [ProjectComponent] responsible for ensuring that a [Project] has a [MergedManifestModificationListener]
   * subscribed to listen for both VFS and Document changes once the initial project sync has completed.
   */
  private class SubscriptionComponent(
    val project: Project
  ) : LazyFileListenerSubscriber<MergedManifestModificationListener>(MergedManifestModificationListener(project), project),
      ProjectComponent {
    override fun projectOpened() {
      project.listenUntilNextSync(listener = object : ProjectSystemSyncManager.SyncResultListener {
        override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) = ensureSubscribed()
      })
    }

    override fun subscribe() {
      // To receive all changes happening in the VFS. File modifications may
      // not be picked up immediately if such changes are not saved on the disk yet
      VirtualFileManager.getInstance().addVirtualFileListener(listener, parent)

      // To receive all changes to documents that are open in an editor
      EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, parent)

      // To receive notifications when any Documents are saved or reloaded from disk
      project.messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, listener)
    }
  }

  companion object {
    @JvmStatic
    fun ensureSubscribed(project: Project) = project.getComponent(SubscriptionComponent::class.java).ensureSubscribed()
  }
}

private fun Module.getTransitiveResourceDependents() = TRANSITIVE_RESOURCE_DEPENDENTS.`fun`(this)

private val TRANSITIVE_RESOURCE_DEPENDENTS = TreeTraversal.PLAIN_BFS.unique().traversal<Module> {
  it.getModuleSystem().getDirectResourceModuleDependents()
}