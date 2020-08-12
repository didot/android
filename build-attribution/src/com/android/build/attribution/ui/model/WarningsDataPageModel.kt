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
package com.android.build.attribution.ui.model

import com.android.build.attribution.ui.data.AnnotationProcessorUiData
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskIssueUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation.NodeIconState
import com.android.build.attribution.ui.warningsCountString
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent.Page.PageType
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong
import javax.swing.tree.DefaultMutableTreeNode

interface WarningsDataPageModel {
  val reportData: BuildAttributionReportUiData

  /** Text of the header visible above the tree. */
  val treeHeaderText: String

  /** The root of the tree that should be shown now. View is supposed to set this root in the Tree on update. */
  val treeRoot: DefaultMutableTreeNode

  var groupByPlugin: Boolean

  /** Currently selected node. Can be null in case of an empty tree. */
  val selectedNode: WarningsTreeNode?

  /** True if there are no warnings to show. */
  val isEmpty: Boolean

  var filter: WarningsFilter

  /**
   * Selects node in a tree to provided.
   * Provided node object should be the one created in this model and exist in the trees it holds.
   * Notifies listener if model state changes.
   */
  fun selectNode(warningsTreeNode: WarningsTreeNode)

  /** Looks for the tree node by it's pageId and selects it as described in [selectNode] if found. */
  fun selectPageById(warningsPageId: WarningsPageId)

  /** Install the listener that will be called on model state changes. */
  fun setModelUpdatedListener(listener: (Boolean) -> Unit)

  /** Retrieve node descriptor by it's page id. Null if node does not exist in currently presented tree structure. */
  fun getNodeDescriptorById(pageId: WarningsPageId): WarningsTreePresentableNodeDescriptor?
}

class WarningsDataPageModelImpl(
  override val reportData: BuildAttributionReportUiData
) : WarningsDataPageModel {
  @VisibleForTesting
  var modelUpdatedListener: ((Boolean) -> Unit)? = null
    private set

  override val treeHeaderText: String =
    "${reportData.totalIssuesCount} ${StringUtil.pluralize("Warning", reportData.totalIssuesCount)}"

  override var filter: WarningsFilter = WarningsFilter.default()
    set(value) {
      field = value
      treeStructure.updateStructure(groupByPlugin, value)
      dropSelectionIfMissing()
      treeStructureChanged = true
      modelChanged = true
      notifyModelChanges()
    }

  override var groupByPlugin: Boolean = false
    set(value) {
      field = value
      treeStructure.updateStructure(value, filter)
      dropSelectionIfMissing()
      treeStructureChanged = true
      modelChanged = true
      notifyModelChanges()
    }

  private val treeStructure = WarningsTreeStructure(reportData).apply {
    updateStructure(groupByPlugin, filter)
  }

  override val treeRoot: DefaultMutableTreeNode
    get() = treeStructure.treeRoot

  // True when there are changes since last listener call.
  private var modelChanged = false

  // TODO (mlazeba): this starts look wrong. Can we provide TreeModel instead of a root node? what are the pros and cons? what are the other options?
  //   idea 1) make listener have multiple methods: tree updated, selection updated, etc.
  //   idea 2) provide TreeModel instead of root. then that model updates tree itself on changes.
  // True when tree changed it's structure since last listener call.
  private var treeStructureChanged = false

  private var selectedPageId: WarningsPageId = WarningsPageId.emptySelection
    private set(value) {
      if (value != field) {
        field = value
        modelChanged = true
      }
    }
  override val selectedNode: WarningsTreeNode?
    get() = treeStructure.pageIdToNode[selectedPageId]

  override val isEmpty: Boolean
    get() = reportData.totalIssuesCount == 0

  override fun selectNode(warningsTreeNode: WarningsTreeNode) {
    selectedPageId = warningsTreeNode.descriptor.pageId
    notifyModelChanges()
  }

  override fun selectPageById(warningsPageId: WarningsPageId) {
    treeStructure.pageIdToNode[warningsPageId]?.let { selectNode(it) }
  }

  override fun setModelUpdatedListener(listener: (Boolean) -> Unit) {
    modelUpdatedListener = listener
  }

  override fun getNodeDescriptorById(pageId: WarningsPageId): WarningsTreePresentableNodeDescriptor? =
    treeStructure.pageIdToNode[pageId]?.descriptor

  private fun dropSelectionIfMissing() {
    if (!treeStructure.pageIdToNode.containsKey(selectedPageId)) {
      selectedPageId = WarningsPageId.emptySelection
    }
  }

  private fun notifyModelChanges() {
    if (modelChanged) {
      modelUpdatedListener?.invoke(treeStructureChanged)
      modelChanged = false
      treeStructureChanged = false
    }
  }
}

private class WarningsTreeStructure(
  val reportData: BuildAttributionReportUiData
) {

  val pageIdToNode: MutableMap<WarningsPageId, WarningsTreeNode> = mutableMapOf()

  private fun treeNode(descriptor: WarningsTreePresentableNodeDescriptor) = WarningsTreeNode(descriptor).apply {
    pageIdToNode[descriptor.pageId] = this
  }

  var treeRoot = DefaultMutableTreeNode()

  fun updateStructure(groupByPlugin: Boolean, filter: WarningsFilter) {
    pageIdToNode.clear()
    treeRoot.let { rootNode ->
      rootNode.removeAllChildren()
      val taskWarnings = reportData.issues.asSequence()
        .flatMap { it.issues.asSequence() }
        .filter { filter.acceptTaskIssue(it) }
        .toList()

      if (groupByPlugin) {
        taskWarnings.groupBy { it.task.pluginName }.forEach { (pluginName, warnings) ->
          val warningsByTask = warnings.groupBy { it.task }
          val pluginTreeGroupingNode = treeNode(PluginGroupingWarningNodeDescriptor(pluginName, warningsByTask))
          rootNode.add(pluginTreeGroupingNode)
          warningsByTask.forEach { (task, warnings) ->
            pluginTreeGroupingNode.add(treeNode(TaskUnderPluginDetailsNodeDescriptor(task, warnings)))
          }
        }
      }
      else {
        taskWarnings.groupBy { it.type }.forEach { (type, warnings) ->
          val warningTypeGroupingNodeDescriptor = TaskWarningTypeNodeDescriptor(type, warnings)
          val warningTypeGroupingNode = treeNode(warningTypeGroupingNodeDescriptor)
          rootNode.add(warningTypeGroupingNode)
          warnings.map { TaskWarningDetailsNodeDescriptor(it) }.forEach { taskIssueNodeDescriptor ->
            warningTypeGroupingNode.add(treeNode(taskIssueNodeDescriptor))
          }
        }
      }
      reportData.annotationProcessors.nonIncrementalProcessors.asSequence()
        .map { AnnotationProcessorDetailsNodeDescriptor(it) }
        .filter { filter.acceptAnnotationProcessorIssue(it.annotationProcessorData) }
        .toList()
        .ifNotEmpty {
          val annotationProcessorsRootNode = treeNode(AnnotationProcessorsRootNodeDescriptor(reportData.annotationProcessors))
          rootNode.add(annotationProcessorsRootNode)
          forEach {
            annotationProcessorsRootNode.add(treeNode(it))
          }
        }
    }
  }
}

class WarningsTreeNode(
  val descriptor: WarningsTreePresentableNodeDescriptor
) : DefaultMutableTreeNode(descriptor)

enum class WarningsPageType {
  EMPTY_SELECTION,
  TASK_WARNING_DETAILS,
  TASK_WARNING_TYPE_GROUP,
  TASK_UNDER_PLUGIN,
  TASK_WARNING_PLUGIN_GROUP,
  ANNOTATION_PROCESSOR_DETAILS,
  ANNOTATION_PROCESSOR_GROUP
}

data class WarningsPageId(
  val pageType: WarningsPageType,
  val id: String
) {
  companion object {
    fun warning(warning: TaskIssueUiData) =
      WarningsPageId(WarningsPageType.TASK_WARNING_DETAILS, "${warning.type}-${warning.task.taskPath}")

    fun task(task: TaskUiData) = WarningsPageId(WarningsPageType.TASK_UNDER_PLUGIN, task.taskPath)

    fun warningType(warningType: TaskIssueType) = WarningsPageId(WarningsPageType.TASK_WARNING_TYPE_GROUP, warningType.name)
    fun warningPlugin(warningPluginName: String) = WarningsPageId(WarningsPageType.TASK_WARNING_PLUGIN_GROUP, warningPluginName)

    fun annotationProcessor(annotationProcessorData: AnnotationProcessorUiData) = WarningsPageId(
      WarningsPageType.ANNOTATION_PROCESSOR_DETAILS, annotationProcessorData.className)

    val annotationProcessorRoot = WarningsPageId(WarningsPageType.ANNOTATION_PROCESSOR_GROUP, "ANNOTATION_PROCESSORS")
    val emptySelection = WarningsPageId(WarningsPageType.EMPTY_SELECTION, "EMPTY")
  }
}

sealed class WarningsTreePresentableNodeDescriptor {
  abstract val pageId: WarningsPageId
  abstract val analyticsPageType: PageType
  abstract val presentation: BuildAnalyzerTreeNodePresentation
  override fun toString(): String = presentation.mainText
}

/** Descriptor for the task warning type group node. */
class TaskWarningTypeNodeDescriptor(
  val warningType: TaskIssueType,
  val presentedWarnings: List<TaskIssueUiData>

) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warningType(warningType)
  override val analyticsPageType = when (warningType) {
    TaskIssueType.ALWAYS_RUN_TASKS -> PageType.ALWAYS_RUN_ISSUE_ROOT
    TaskIssueType.TASK_SETUP_ISSUE -> PageType.TASK_SETUP_ISSUE_ROOT
  }

  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = warningType.uiName,
      suffix = warningsCountString(presentedWarnings.size),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(presentedWarnings.sumByLong { it.task.executionTime.timeMs })
    )
}

/** Descriptor for the task warning page node. */
class TaskWarningDetailsNodeDescriptor(
  val issueData: TaskIssueUiData
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warning(issueData)
  override val analyticsPageType = when (issueData) {
    is TaskIssueUiDataContainer.TaskSetupIssue -> PageType.TASK_SETUP_ISSUE_PAGE
    is TaskIssueUiDataContainer.AlwaysRunNoOutputIssue -> PageType.ALWAYS_RUN_NO_OUTPUTS_PAGE
    is TaskIssueUiDataContainer.AlwaysRunUpToDateOverride -> PageType.ALWAYS_RUN_UP_TO_DATE_OVERRIDE_PAGE
    else -> PageType.UNKNOWN_PAGE
  }
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = issueData.task.taskPath,
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(issueData.task.executionTime.timeMs)
    )
}

class PluginGroupingWarningNodeDescriptor(
  val pluginName: String,
  val presentedTasksWithWarnings: Map<TaskUiData, List<TaskIssueUiData>>

) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.warningPlugin(pluginName)

  override val analyticsPageType = PageType.PLUGIN_WARNINGS_ROOT

  private val warningsCount = presentedTasksWithWarnings.values.sumBy { it.size }

  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = pluginName,
      suffix = warningsCountString(warningsCount),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(presentedTasksWithWarnings.keys.sumByLong { it.executionTime.timeMs })
    )
}

/** Descriptor for the task warning page node. */
class TaskUnderPluginDetailsNodeDescriptor(
  val taskData: TaskUiData,
  val filteredWarnings: List<TaskIssueUiData>
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.task(taskData)

  // TODO (b/150295612): add new page type, there is no matching one in the old model.
  override val analyticsPageType = PageType.UNKNOWN_PAGE
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = taskData.taskPath,
      nodeIconState = NodeIconState.WARNING_ICON,
      suffix = warningsCountString(filteredWarnings.size),
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(taskData.executionTime.timeMs)
    )
}

/** Descriptor for the non-incremental annotation processors group node. */
class AnnotationProcessorsRootNodeDescriptor(
  val annotationProcessorsReport: AnnotationProcessorsReport
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.annotationProcessorRoot
  override val analyticsPageType = PageType.ANNOTATION_PROCESSORS_ROOT
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = "Non-incremental Annotation Processors",
      suffix = warningsCountString(annotationProcessorsReport.issueCount)
    )
}

/** Descriptor for the non-incremental annotation processor page node. */
class AnnotationProcessorDetailsNodeDescriptor(
  val annotationProcessorData: AnnotationProcessorUiData
) : WarningsTreePresentableNodeDescriptor() {
  override val pageId: WarningsPageId = WarningsPageId.annotationProcessor(annotationProcessorData)
  override val analyticsPageType = PageType.ANNOTATION_PROCESSOR_PAGE
  override val presentation: BuildAnalyzerTreeNodePresentation
    get() = BuildAnalyzerTreeNodePresentation(
      mainText = annotationProcessorData.className,
      nodeIconState = NodeIconState.WARNING_ICON,
      rightAlignedSuffix = rightAlignedNodeDurationTextFromMs(annotationProcessorData.compilationTimeMs)
    )
}

private fun rightAlignedNodeDurationTextFromMs(timeMs: Long) = "%.1fs".format(timeMs.toDouble() / 1000)