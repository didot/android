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
package com.android.build.attribution.ui

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.intellij.build.BuildContentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.impl.ContentImpl

/**
 * This class is responsible for creating, opening and properly disposing of Build attribution UI.
 */
class BuildAttributionUiManager(
  private val project: Project
) {

  private val buildContentManager: BuildContentManager by lazy {
    ServiceManager.getService(project, BuildContentManager::class.java)
  }
  private val uiAnalytics = BuildAttributionUiAnalytics(project)
  private val contentManagerListener = object : ContentManagerAdapter() {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.content !== buildContent) {
        return
      }
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        uiAnalytics.tabOpened()
      }
      else if (event.operation == ContentManagerEvent.ContentOperation.remove) {
        uiAnalytics.tabHidden()
      }
    }
  }

  private var buildContent: Content? = null
  private var manager: ContentManager? = null
  private var buildAttributionTreeView: BuildAttributionTreeView? = null

  private lateinit var reportUiData: BuildAttributionReportUiData


  fun showNewReport(reportUiData: BuildAttributionReportUiData, buildSessionId: String) {
    this.reportUiData = reportUiData
    ApplicationManager.getApplication().invokeLater {
      uiAnalytics.newReportSessionId(buildSessionId)
      updateReportUI()
    }
  }

  @UiThread
  private fun updateReportUI() {
    createNewView()
    buildContent?.takeIf { it.isValid }?.apply { replaceContentView() } ?: run {
      createNewTab()
    }
  }

  private fun createNewView() {
    buildAttributionTreeView?.let { treeView -> Disposer.dispose(treeView) }
    val issueReporter = TaskIssueReporter(reportUiData, project, uiAnalytics)
    buildAttributionTreeView = BuildAttributionTreeView(reportUiData, issueReporter, uiAnalytics)
      .also { newView -> newView.setInitialSelection() }
  }

  private fun Content.replaceContentView() {
    buildAttributionTreeView?.let { view ->
      component = view.component
      Disposer.register(this, view)
      uiAnalytics.buildReportReplaced()
    }
  }

  private fun createNewTab() {
    buildAttributionTreeView?.let { view ->
      buildContent = ContentImpl(view.component, "Build Speed", true).also { content ->
        Disposer.register(project, content)
        Disposer.register(content, view)
        // When tab is getting closed (and disposed) we want to release the reference on the view.
        Disposer.register(content, Disposable { onContentClosed() })
        buildContentManager.addContent(content)
        uiAnalytics.tabCreated()
        manager = content.manager
        manager?.addContentManagerListener(contentManagerListener)
      }
    }
  }

  private fun onContentClosed() {
    uiAnalytics.tabClosed()
    manager?.removeContentManagerListener(contentManagerListener)
    manager = null
    buildAttributionTreeView = null
    buildContent = null
  }

  fun openTab() {
    ApplicationManager.getApplication().invokeLater {
      if (buildContent?.isValid != true) {
        createNewView()
        createNewTab()
      }
      uiAnalytics.registerBuildOutputLinkClick()
      buildContentManager.setSelectedContent(buildContent, true, true, true) {}
    }
  }
}