/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.editors.literals

import com.android.ddmlib.IDevice
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditDeployMonitor
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.android.tools.idea.util.ListenerCollection
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.Callable
import java.util.concurrent.Executor

/**
 * @param file: Where the file event originate
 * @param origin: The most narrow PSI Element where the edit event occurred.
 */
data class EditEvent(val file: PsiFile,
                     val origin: KtElement) {

  // A list of all functions that encapsulate the origin of the event in the source code ordered by nesting level
  // from inner-most to outer-most. This will be use to determine which compose groups to invalidate on the given change.
  val parentGroup = ArrayList<KtFunction>()
}

enum class EditState {
  ERROR, PAUSED, IN_PROGRESS, UP_TO_DATE, DISABLED
}

data class EditStatus(val editState: EditState, val message: String)

/**
 * Allows any component to listen to all method body edits of a project.
 */
@Service
class LiveEditService private constructor(project: Project, var listenerExecutor: Executor) : Disposable {

  val inlineCandidateCache = SourceInlineCandidateCache()

  constructor(project: Project) : this(project,
                                       AppExecutorUtil.createBoundedApplicationPoolExecutor(
                                         "Document changed listeners executor", 1))

  fun resetState() = inlineCandidateCache.clear()

  fun interface EditListener {
    operator fun invoke(method: EditEvent)
  }

  fun interface EditStatusProvider {
    operator fun invoke() : EditStatus
  }

  private val onEditListeners = ListenerCollection.createWithExecutor<EditListener>(listenerExecutor)

  private val deployMonitor: AndroidLiveEditDeployMonitor

  private val editStatusProviders = mutableListOf<EditStatusProvider>()

  fun addOnEditListener(listener: EditListener) {
    onEditListeners.add(listener)
  }

  fun addEditStatusProvider(provider: EditStatusProvider) {
    editStatusProviders.add(provider)
  }

  init {
    // TODO: Deactivate this when not needed.
    val listener = MyPsiListener(::onMethodBodyUpdated)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
    deployMonitor = AndroidLiveEditDeployMonitor(this, project)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LiveEditService = project.getService(LiveEditService::class.java)

    @JvmField
    val DISABLED_STATUS = EditStatus(EditState.DISABLED, "")
    @JvmField
    val UP_TO_DATE_STATUS = EditStatus(EditState.UP_TO_DATE, "All changes applied.")
  }

  fun editStatus(): EditStatus {
    var editStatus = DISABLED_STATUS
    for (provider in editStatusProviders) {
      val nextStatus = provider.invoke()
      // TODO make this state transition more robust/centralized
      if (nextStatus.editState.ordinal < editStatus.editState.ordinal) {
        editStatus = nextStatus
      }
    }
    return editStatus
  }

  fun getCallback(packageName: String, device: IDevice) : Callable<*>? {
    return deployMonitor.getCallback(packageName, device)
  }

  @com.android.annotations.Trace
  private fun onMethodBodyUpdated(event: EditEvent) {
    onEditListeners.forEach {
      it(event)
    }
  }

  private inner class MyPsiListener(private val editListener: EditListener) : PsiTreeChangeListener {
    @com.android.annotations.Trace
    private fun handleChangeEvent(event: PsiTreeChangeEvent) {
      // THIS CODE IS EXTREMELY FRAGILE AT THE MOMENT.
      // According to the PSI listener doc, there is no guarantee what events we get.
      // Changing a single variable name can result with a "replace" of the whole file.
      //
      // While this works "ok" for the most part, we need to figure out a better way to detect
      // the change is actually a function change somehow.

      if (event.file == null || event.file !is KtFile) {
        return
      }

      val file = event.file as KtFile
      var parent = event.parent;

      // The code might not be valid at this point, so we should not be making any
      // assumption based on the Kotlin language structure.

      while (parent != null) {
        when (parent) {
          is KtNamedFunction -> {
            val event = EditEvent(file, parent)
            editListener(event)
            break;
          }
          is KtFunction -> {
            val event = EditEvent(file, parent)

            // Record each unnamed function as part of the event until we reach a named function.
            // This will be used to determine how partial recomposition is done on this edit in a later stage.
            var groupParent = parent.parent
            while (groupParent != null) {
              when (groupParent) {
                is KtNamedFunction -> {
                  event.parentGroup.add(groupParent)
                  break
                }
                is KtNamedFunction -> {
                  event.parentGroup.add(groupParent)
                }
              }
              groupParent = groupParent.parent
            }
            editListener(event)
            break;
          }
          is KtClass -> {
            val event = EditEvent(file, parent)
            editListener(event)
            break;
          }
        }
        parent = parent.parent
      }

      // This is a workaround to experiment with partial recomposition. Right now any simple edit would create multiple
      // edit events and one of them is usually a spurious whole file event that will trigger an unnecessary whole recompose.
      // For now we just ignore that event until Live Edit becomes better at diff'ing changes.
      if (!LiveEditAdvancedConfiguration.getInstance().usePartialRecompose) {
        // If there's no Kotlin construct to use as a parent for this event, use the KtFile itself as the parent.
        val event = EditEvent(file, file)
        editListener(event)
      }
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }
  }

  override fun dispose() {
    //TODO: "Not yet implemented"
  }
}