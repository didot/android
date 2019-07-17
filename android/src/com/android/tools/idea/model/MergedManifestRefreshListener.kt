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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.ide.common.util.PathString
import com.android.resources.ResourceFolderType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.LazyVirtualFileListenerSubscriber
import com.android.tools.idea.util.PoliteAndroidVirtualFileListener
import com.android.tools.idea.util.listenUntilNextSync
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.IdeaSourceProvider


/**
 * A project-wide listener that determines which modules' merged manifests are affected by VFS
 * changes and tells the corresponding [MergedManifestManager]s to recompute them in the background
 * accordingly. [MergedManifestRefreshListener] registers itself to start actively listening for
 * VFS changes after the first project sync of the session.
 *
 * This listener is necessary to ensure that the merged manifest is reasonably up to date for
 * callers who are forced to acquire it synchronously (and therefore must use whatever [MergedManifestManager]
 * has cached for performance reasons).
 */
class MergedManifestRefreshListener(project: Project) : PoliteAndroidVirtualFileListener(project) {

  // If a directory was deleted, we won't get a separate event for each descendant, so we
  // must let directories pass through this fail-fast filter in case they contain relevant files.
  override fun isPossiblyRelevant(file: VirtualFile) = file.isDirectory || file.extension == "xml"

  /**
   * Determines if a file contributes to the merged manifest of the module to which it belongs.
   * This means the file is either:
   *
   *  1. An AndroidManifest.xml belonging to one of the module's source providers.
   *  2. A navigation XML file belonging to one of the module's resource directories.
   *  3. One of the module's navigation resource folders which contains a navigation file.
   *  4. One of the module's resource directories which contains a navigation file.
   *
   *  @see [MergedManifestContributors]
   */
  override fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean {
    if (file.name == FN_ANDROID_MANIFEST_XML) return IdeaSourceProvider.isManifestFile(facet, file)

    fun VirtualFile.couldBeNavigationFolder(): Boolean {
      return isDirectory && parent != null && ResourceFolderType.getFolderType(name) == ResourceFolderType.NAVIGATION
    }

    val couldBeNavigationFile = !file.isDirectory && file.parent?.couldBeNavigationFolder() == true
    if (!file.isDirectory && !couldBeNavigationFile) return false

    val couldBeRelevantNavigationFolder = file.couldBeNavigationFolder() && file.children.isNotEmpty()
    return IdeaSourceProvider.getCurrentSourceProviders(facet)
      .asSequence()
      .flatMap { it.resDirectories.asSequence() }
      .any { resDir ->
        resDir == file && resDir.children.any { it.couldBeNavigationFolder() && it.children.isNotEmpty() }
        || couldBeRelevantNavigationFolder && resDir == file.parent
        || couldBeNavigationFile && resDir == file.parent.parent
      }
  }

  override fun fileChanged(path: PathString, facet: AndroidFacet) = refreshAffectedMergedManifests(facet)

  private fun refreshAffectedMergedManifests(facet: AndroidFacet) {
    // While a freshness check for a single manifest should be fast enough to run on any thread, we want to run this off the EDT
    // because determining the dependents of a module and then checking the freshness of each of their merged manifests can take
    // some time (depending on the size of the project). The thread pool doesn't necessarily need to be limited to a single thread,
    // but we do want to avoid spinning off an unbounded number of them since relevant events can be very frequent and the freshness
    // checks aren't throttled. If necessary, the MergedManifestManagers will perform the actual re-computation on background threads
    // from another pool.
    FRESHNESS_EXECUTOR.submit {
      // If the merged manifest of this module is stale, then so are the merged manifests
      // of every module that depends on this one. Merged manifest computation and caching
      // is recursive, so we can save ourselves redundant freshness checks by getting the
      // merged manifests for just the top-level dependents.
      facet.module.getTopLevelResourceDependents().forEach { MergedManifestManager.getMergedManifest(it) }
    }
  }

  /**
   * [ProjectComponent] responsible for ensuring that a [Project] has a [MergedManifestRefreshListener]
   * subscribed once the initial project sync has completed.
   */
  private class SubscriptionComponent(val project: Project) :
    LazyVirtualFileListenerSubscriber<MergedManifestRefreshListener>(MergedManifestRefreshListener(project), project),
    ProjectComponent {

    override fun projectOpened() {
      project.listenUntilNextSync(listener = object : SyncResultListener {
        override fun syncEnded(result: SyncResult) = ensureSubscribed()
      })
    }
  }

  companion object {
    @JvmStatic
    fun ensureSubscribed(project: Project) = project.getComponent(SubscriptionComponent::class.java).ensureSubscribed()
  }
}

private val FRESHNESS_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor("Merged Manifest Freshness Check Pool", 1)

/**
 * Returns an iterator over the top-level modules that transitively depend on this module for
 * resources, possibly including this module itself (i.e. if nothing depends on this module for
 * resources then the iterator will yield only this module).
 */
private fun Module.getTopLevelResourceDependents() = TOP_LEVEL_RESOURCE_DEPENDENTS.`fun`(this)

private val TOP_LEVEL_RESOURCE_DEPENDENTS = TreeTraversal.LEAVES_DFS.unique().traversal<Module> {
  it.getModuleSystem().getDirectResourceModuleDependents()
}
