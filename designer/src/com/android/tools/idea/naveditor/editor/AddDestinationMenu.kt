// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.actions.NewAndroidComponentAction
import com.android.tools.idea.naveditor.scene.NavColorSet
import com.android.tools.idea.naveditor.scene.layout.NEW_DESTINATION_MARKER_PROPERTY
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.res.ResourceNotificationManager
import com.google.common.collect.ImmutableList
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DottedBorder
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.AndroidGotoRelatedProvider
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.resourceManagers.LocalResourceManager
import java.awt.BorderLayout
import java.awt.Image
import java.awt.Point
import java.awt.event.*
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent

/**
 * "Add" popup menu in the navigation editor.
 */
// open for testing only
@VisibleForTesting
open class AddDestinationMenu(surface: NavDesignSurface) :
  NavToolbarMenu(surface, "New Destination", StudioIcons.NavEditor.Toolbar.ADD_DESTINATION) {

  private lateinit var button: JComponent
  private var creatingInProgress = false
  private val createdFiles: MutableList<File> = mutableListOf()
  private var buttonPresentation: Presentation? = null

  @VisibleForTesting
  val destinations: List<Destination>
    get() {
      val model = surface.model!!
      val classToDestination = LinkedHashMap<PsiClass, Destination>()
      val module = model.module
      val schema = surface.sceneManager?.schema ?: return listOf()

      val scope = GlobalSearchScope.moduleWithDependenciesScope(module)
      val project = model.project
      val parent = surface.currentNavigation
      for (superClassName in NavigationSchema.DESTINATION_SUPERCLASS_TO_TYPE.keys) {
        val psiSuperClass = JavaPsiFacade.getInstance(project).findClass(superClassName, GlobalSearchScope.allScope(project)) ?: continue
        val tag = schema.getTagForComponentSuperclass(superClassName) ?: continue
        val query = ClassInheritorsSearch.search(psiSuperClass, scope, true)
        for (psiClass in query) {
          val destination = Destination.RegularDestination(parent, tag, null, psiClass.name, psiClass.qualifiedName)
          classToDestination[psiClass] = destination
        }
      }

      val resourceManager = LocalResourceManager.getInstance(module) ?: return listOf()

      for (resourceFile in resourceManager.findResourceFiles(ResourceFolderType.LAYOUT).filterIsInstance<XmlFile>()) {
        // TODO: refactor AndroidGotoRelatedProvider so this can be done more cleanly
        val itemComputable = AndroidGotoRelatedProvider.getLazyItemsForXmlFile(resourceFile, model.facet)
        for (item in itemComputable?.compute() ?: continue) {
          val element = item.element as? PsiClass ?: continue
          val tag = schema.findTagForComponent(element) ?: continue
          val destination =
            Destination.RegularDestination(parent, tag, null, element.name, element.qualifiedName, layoutFile = resourceFile)
          classToDestination[element] = destination
        }
      }

      val result = classToDestination.values.toMutableList()

      for (navPsi in resourceManager.findResourceFiles(ResourceFolderType.NAVIGATION).filterIsInstance<XmlFile>()) {
        if (surface.model!!.file == navPsi) {
          continue
        }
        result.add(Destination.IncludeDestination(navPsi.name, parent))
      }

      return result
    }

  @Suppress("UNCHECKED_CAST")
  val destinationsList: JBList<Destination> = object : JBList<Destination>() {
    override fun locationToIndex(location: Point): Int {
      val pointIndex = super.locationToIndex(location)
      return if (getCellBounds(pointIndex, pointIndex).contains(location)) pointIndex else -1
    }
  }

  @VisibleForTesting
  lateinit var searchField: SearchTextField

  private var _mainPanel: JPanel? = null

  override val mainPanel: JPanel
    get() {
      creatingInProgress = false
      return _mainPanel ?: createSelectionPanel().also { _mainPanel = it }
    }

  @VisibleForTesting
  lateinit var blankDestinationButton: ActionButtonWithText

  init {
    val resourceListener = ResourceNotificationManager.ResourceChangeListener { _ -> _mainPanel = null }
    val notificationManager = ResourceNotificationManager.getInstance(surface.project)
    val facet = surface.model!!.facet
    notificationManager.addListener(resourceListener, facet, null, null)

    val lafListener = { _: LafManager -> _mainPanel = null }
    LafManager.getInstance().addLafManagerListener(lafListener)

    Disposer.register(surface, Disposable {
      notificationManager.removeListener(resourceListener, facet, null, null)
      LafManager.getInstance().removeLafManagerListener(lafListener)
    })
  }

  private var neverShown = true

  private fun createSelectionPanel(): JPanel {
    val result = object : AdtSecondaryPanel(VerticalLayout(5)), DataProvider {
      override fun getData(dataId: String?): Any? {
        return if (NewAndroidComponentAction.CREATED_FILES.`is`(dataId)) {
          createdFiles
        }
        else {
          surface.getData(dataId)
        }
      }
    }

    searchField = SearchTextField()
    // leading space is required so text doesn't overlap magnifying glass
    searchField.textEditor.emptyText.text = "   Search existing destinations"
    result.add(searchField)

    val action: AnAction = object : AnAction("Create blank destination") {
      override fun actionPerformed(e: AnActionEvent?) {
        createBlankDestination(e)
      }
    }
    blankDestinationButton = ActionButtonWithText(action, action.templatePresentation, "Toolbar", JBDimension(0, 45))
    val buttonPanel = AdtSecondaryPanel(BorderLayout())
    buttonPanel.border = CompoundBorder(JBUI.Borders.empty(1, 7), DottedBorder(JBUI.emptyInsets(), NavColorSet.SUBDUED_FRAME_COLOR))
    buttonPanel.add(blankDestinationButton, BorderLayout.CENTER)
    val scrollable = AdtSecondaryPanel(BorderLayout())
    scrollable.add(buttonPanel, BorderLayout.NORTH)
    val scrollPane = JBScrollPane(scrollable)
    scrollPane.preferredSize = JBDimension(252, 300)
    scrollPane.border = BorderFactory.createEmptyBorder()

    result.add(scrollPane)

    val application = ApplicationManager.getApplication()

    result.addHierarchyListener { e ->
      if (e?.changeFlags?.and(HierarchyEvent.SHOWING_CHANGED.toLong())?.let { it > 0 } == true) {
        if (neverShown || balloon?.wasFadedOut() == true) {
          neverShown = false
          application.invokeLater { searchField.requestFocusInWindow() }
        }
      }
    }
    destinationsList.emptyText.text = "Loading..."
    destinationsList.setPaintBusy(true)
    destinationsList.setCellRenderer { _, value, _, selected, _ ->
      THUMBNAIL_RENDERER.icon = ImageIcon(value.thumbnail.getScaledInstance(JBUI.scale(50), JBUI.scale(64), Image.SCALE_SMOOTH))
      PRIMARY_TEXT_RENDERER.text = value.label
      SECONDARY_TEXT_RENDERER.text = value.typeLabel
      RENDERER.isOpaque = selected
      RENDERER
    }
    destinationsList.background = result.background

    destinationsList.addMouseListener(object : MouseAdapter() {
      override fun mouseExited(e: MouseEvent?) {
        destinationsList.clearSelection()
      }

      override fun mouseClicked(event: MouseEvent) {
        destinationsList.selectedValue?.let { addDestination(it) }
      }
    })

    destinationsList.background = null
    destinationsList.addMouseMotionListener(
      object : MouseAdapter() {
        override fun mouseMoved(event: MouseEvent) {
          val index = destinationsList.locationToIndex(event.point)
          if (index != -1) {
            destinationsList.selectedIndex = index
          }
          else {
            destinationsList.clearSelection()
          }
        }
      }
    )

    destinationsList.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent?) {
        searchField.requestFocus()
        application.invokeLater { searchField.dispatchEvent(e) }
      }
    })
    scrollable.add(destinationsList, BorderLayout.CENTER)


    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      object : Task.Backgroundable(surface.project, "Get Available Destinations") {
        override fun run(indicator: ProgressIndicator) {
          val listModel = application.runReadAction(Computable {
            FilteringListModel<Destination>(CollectionListModel<Destination>(destinations))
          })
          listModel.setFilter { destination -> destination.label.toLowerCase().contains(searchField.text.toLowerCase()) }
          searchField.addDocumentListener(
            object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                listModel.refilter()
              }
            }
          )

          application.invokeLater {
            @Suppress("UNCHECKED_CAST")
            destinationsList.model = listModel as ListModel<Destination>

            destinationsList.setPaintBusy(false)
            destinationsList.emptyText.text = "No existing destinations"
          }

        }
      }, EmptyProgressIndicator())

    return result
  }

  private fun createBlankDestination(e: AnActionEvent?) {
    balloon?.hide()
    val action = NewAndroidComponentAction("Fragment", "Fragment (Blank)", 7)
    action.setShouldOpenFiles(false)
    createBlankDestination(e, action)
  }

  @VisibleForTesting
  fun createBlankDestination(e: AnActionEvent?, action: AnAction) {
    createdFiles.clear()
    action.actionPerformed(e)
    val project = e?.project ?: return
    val sceneManager = surface.sceneManager ?: return
    val schema = sceneManager.schema
    val model = surface.model ?: return
    val resourceManager = LocalResourceManager.getInstance(model.module) ?: return

    DumbService.getInstance(project).runWhenSmart {
      var layoutFile: XmlFile? = null
      var psiClass: PsiClass? = null

      for (file in createdFiles) {
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: continue
        val psiFile = PsiUtil.getPsiFile(project, virtualFile)
        if (psiFile is XmlFile && resourceManager.getFileResourceFolderType(psiFile) == ResourceFolderType.LAYOUT) {
          layoutFile = psiFile
        }
        if (psiFile is PsiClassOwner) {
          psiClass = psiFile.classes[0]
        }
      }
      if (psiClass != null) {
        val tag = schema.findTagForComponent(psiClass) ?: return@runWhenSmart
        val destination =
          Destination.RegularDestination(surface.currentNavigation, tag, null, psiClass.name, psiClass.qualifiedName,
                                         layoutFile = layoutFile)
        addDestination(destination)
      }
      createdFiles.clear()
    }
  }

  private fun addDestination(destination: Destination) {
    if (creatingInProgress) {
      return
    }
    creatingInProgress = true
    destination.addToGraph()
    // explicitly update so the new SceneComponent is created
    surface.sceneManager!!.update()
    val component = destination.component
    component!!.putClientProperty(NEW_DESTINATION_MARKER_PROPERTY, true)
    surface.selectionModel.setSelection(ImmutableList.of(component))
    balloon?.hide()
  }

  override fun createCustomComponent(presentation: Presentation): JComponent {
    button = super.createCustomComponent(presentation)
    buttonPresentation = presentation
    return button
  }

  // open for testing only
  open fun show() {
    show(button)
  }

  override fun update(e: AnActionEvent?) {
    e?.project?.let { buttonPresentation?.isEnabled = !DumbService.isDumb(it) }
  }

  companion object {

    private val RENDERER = AdtSecondaryPanel(BorderLayout())
    private val THUMBNAIL_RENDERER = JBLabel()
    private val PRIMARY_TEXT_RENDERER = JBLabel()
    private val SECONDARY_TEXT_RENDERER = JBLabel()

    init {
      SECONDARY_TEXT_RENDERER.foreground = NavColorSet.SUBDUED_TEXT_COLOR
      RENDERER.add(THUMBNAIL_RENDERER, BorderLayout.WEST)
      val rightPanel = JPanel(VerticalLayout(8))
      rightPanel.isOpaque = false
      rightPanel.border = JBUI.Borders.empty(12, 6, 0, 0)
      rightPanel.add(PRIMARY_TEXT_RENDERER, VerticalLayout.CENTER)
      rightPanel.add(SECONDARY_TEXT_RENDERER, VerticalLayout.CENTER)
      RENDERER.add(rightPanel, BorderLayout.CENTER)
      RENDERER.background = NavColorSet.LIST_MOUSEOVER_COLOR
      RENDERER.isOpaque = false
    }
  }
}
