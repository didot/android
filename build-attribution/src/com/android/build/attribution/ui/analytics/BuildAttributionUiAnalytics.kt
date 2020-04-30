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
package com.android.build.attribution.ui.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class BuildAttributionUiAnalytics(private val project: Project) {

  enum class TabOpenEventSource {
    WNA_BUTTON,
    BUILD_OUTPUT_LINK,
    TAB_HEADER
  }

  private val unknownPage: BuildAttributionUiEvent.Page = BuildAttributionUiEvent.Page.newBuilder()
    .setPageType(BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE)
    .build()
  private var currentPage: BuildAttributionUiEvent.Page = unknownPage

  private var nodeLinkClickRegistered = false
  private var tabOpenEventSource: TabOpenEventSource = TabOpenEventSource.TAB_HEADER

  private var buildAttributionReportSessionId: String? = null

  private val pagesVisited = mutableMapOf<AnalyticsPageId, BuildAttributionUiEvent.Page>()
  private val pagesCountByType = mutableMapOf<BuildAttributionUiEvent.Page.PageType, Int>()

  init {
    Disposer.register(project, Disposable { sendSessionOverIfExist() })
  }

  private fun sendSessionOverIfExist() {
    if (buildAttributionReportSessionId != null) {
      doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER))
    }
  }

  /**
   * Called when new "Build Analyzer" tab is created.
   */
  fun tabCreated() = doLog(newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.TAB_CREATED))

  /**
   * Called when "Build Analyzer" tab becomes selected in Build toolwindow.
   * If [registerOpenEventSource] was called just before this call then this event will be reported using provided there value.
   */
  fun tabOpened() {
    val eventType = when (tabOpenEventSource) {
      TabOpenEventSource.WNA_BUTTON -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_WNA_BUTTON
      TabOpenEventSource.BUILD_OUTPUT_LINK -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK
      TabOpenEventSource.TAB_HEADER -> BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK
    }
    doLog(newUiEventBuilderWithPage().setEventType(eventType))
  }

  /**
   * Called when other tab becomes opened on Build toolwindow.
   */
  fun tabHidden() {
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.TAB_HIDDEN))
  }

  /**
   * Called when "Build Analyzer" tab is getting closed.
   */
  fun tabClosed() {
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.TAB_CLOSED))
  }

  /**
   * Called when report about new build replaces current one in the opened "Build Analyzer" tab.
   */
  fun buildReportReplaced() {
    doLog(newUiEventBuilder().setEventType(BuildAttributionUiEvent.EventType.CONTENT_REPLACED))
  }

  /**
   * Registers that page is going to be changed as the result of link click so next [pageChange] call should report it properly.
   * This state will be cleared with any next event sent.
   */
  fun registerNodeLinkClick() {
    nodeLinkClickRegistered = true
  }

  /**
   * Registers what action was clicked to open Build Analyzer tab so next [tabOpened] call should report it's event as opened using that action.
   * This state will be cleared with any next event sent.
   */
  fun registerOpenEventSource(eventSource: TabOpenEventSource) {
    tabOpenEventSource = eventSource
  }

  /**
   * Called when tree selection changes and new page is shown to the user.
   * If [registerNodeLinkClick] was called just before this call then this event will be reported as PAGE_CHANGE_LINK_CLICK.
   */
  @Deprecated("Left to support older version", replaceWith = ReplaceWith("pageChange(pageId, eventType)"))
  fun pageChange(selectedNodeId: String, pageType: BuildAttributionUiEvent.Page.PageType) {
    val eventType = if (nodeLinkClickRegistered) {
      BuildAttributionUiEvent.EventType.PAGE_CHANGE_LINK_CLICK
    }
    else {
      // TODO mlazeba Find how to easily track what was used to update tree selection: mouse or keystrokes.
      // Report both cases as TREE_CLICK for now.
      BuildAttributionUiEvent.EventType.PAGE_CHANGE_TREE_CLICK
    }
    pageChange(AnalyticsPageId(pageType, selectedNodeId), eventType)
  }

  /**
   * Called when page selection changes and new page is shown to the user.
   * Called from the action handler which should be aware of and provide in the parameters
   * what page user is navigation to and what method is used.
   */
  fun pageChange(pageId: AnalyticsPageId, eventType: BuildAttributionUiEvent.EventType) {
    val newPage = toPage(pageId)
    val uiEvent = newUiEventBuilderWithPage().setEventType(eventType).setTargetPage(newPage)
    doLog(uiEvent)

    currentPage = newPage
  }

  fun bugReportLinkClicked() =
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.GENERATE_REPORT_LINK_CLICKED))

  fun reportingWindowCopyButtonClicked() =
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.REPORT_DIALOG_TEXT_COPY_CLICKED))

  fun reportingWindowClosed() =
    doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.REPORT_DIALOG_CLOSED))


  fun helpLinkClicked() = doLog(newUiEventBuilderWithPage().setEventType(BuildAttributionUiEvent.EventType.HELP_LINK_CLICKED))

  private fun newUiEventBuilder(): BuildAttributionUiEvent.Builder {
    requireNotNull(buildAttributionReportSessionId)
    return BuildAttributionUiEvent.newBuilder().setBuildAttributionReportSessionId(buildAttributionReportSessionId)
  }

  private fun newUiEventBuilderWithPage() = newUiEventBuilder().setCurrentPage(currentPage)

  private fun registerPage(pageId: AnalyticsPageId): BuildAttributionUiEvent.Page {
    val newPageEntryIndex = pagesCountByType.compute(pageId.pageType) { _, count -> count?.inc() ?: 1 }!!
    return BuildAttributionUiEvent.Page.newBuilder().setPageType(pageId.pageType).setPageEntryIndex(newPageEntryIndex).build()
  }

  private fun toPage(pageId: AnalyticsPageId): BuildAttributionUiEvent.Page =
    pagesVisited.computeIfAbsent(pageId) { registerPage(it) }

  private fun doLog(uiEvent: BuildAttributionUiEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT)
        .withProjectId(project)
        .setBuildAttributionUiEvent(uiEvent)
    )
    //Clear up state variables
    tabOpenEventSource = TabOpenEventSource.TAB_HEADER
    nodeLinkClickRegistered = false
  }

  /**
   * Called instead of [pageChange] when it is a first page opened by default.
   */
  fun initFirstPage(pageId: AnalyticsPageId) {
    currentPage = toPage(pageId)
  }

  /**
   * Set new build id to be sent with the events.
   * If previous session existed, send closing event for it.
   */
  fun newReportSessionId(buildSessionId: String) {
    sendSessionOverIfExist()
    pagesVisited.clear()
    pagesCountByType.clear()
    currentPage = unknownPage
    buildAttributionReportSessionId = buildSessionId
  }

  data class AnalyticsPageId(
    val pageType: BuildAttributionUiEvent.Page.PageType,
    val pageId: String
  )
}