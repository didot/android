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
package com.android.tools.idea.uibuilder.surface

import android.view.View
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Validator for [NlDesignSurface].
 * It retrieves validation results from the [RenderResult] and update the lint accordingly.
 */
class NlLayoutScanner(
  issueModel: IssueModel,
  parent: Disposable,
  private val metricTracker: NlLayoutScannerMetricTracker): Disposable {

  interface Listener {
    fun lintUpdated(result: ValidatorResult?)
  }

  /** Returns list of issues generated by linter that are specific to the layout. */
  val issues get() = lintIntegrator.issues

  /** Helper class for displaying output to lint system */
  private val lintIntegrator = AccessibilityLintIntegrator(issueModel)

  /**
   * Maps required for Accessibility Testing Framework.
   * This is used to link a11y lint output with the source [NlComponent].
   */
  @VisibleForTesting
  val viewToComponent: BiMap<View, NlComponent> = HashBiMap.create()
  @VisibleForTesting
  val idToComponent: BiMap<Int, NlComponent> = HashBiMap.create()
  @VisibleForTesting
  val listeners = HashSet<Listener>()

  init {
    Disposer.register(parent, this)
  }

  /**
   * Validate the layout and update the lint accordingly.
   */
  fun validateAndUpdateLint(renderResult: RenderResult, model: NlModel) {
    when (val validatorResult = renderResult.validatorResult) {
      is ValidatorHierarchy -> {
        if (!validatorResult.isHierarchyBuilt) {
          // Result not available
          listeners.forEach { it.lintUpdated(null) }
          return
        }
        validateAndUpdateLint(renderResult, LayoutValidator.validate(validatorResult), model)
      }
      is ValidatorResult -> {
        validateAndUpdateLint(renderResult, validatorResult, model)
      }
      else -> {
        // Result not available.
        listeners.forEach { it.lintUpdated(null) }
      }
    }
  }

  private fun validateAndUpdateLint(
      renderResult: RenderResult,
      validatorResult: ValidatorResult,
      model: NlModel) {
    lintIntegrator.clear()
    viewToComponent.clear()
    idToComponent.clear()

    var result: ValidatorResult? = null
    try {
      val components = model.components
      if (components.isEmpty()) {
        // Result not available.
        return
      }
      val root = components[0]
      buildViewToComponentMap(root)
      validatorResult.issues.forEach {
        if ((it.mLevel == ValidatorData.Level.ERROR || it.mLevel == ValidatorData.Level.WARNING) &&
            it.mType == ValidatorData.Type.ACCESSIBILITY) {
          lintIntegrator.createIssue(it, findComponent(it, validatorResult.srcMap))
        }
        metricTracker.trackIssue(it)
      }
      lintIntegrator.populateLints()
      result = validatorResult
    } finally {
      viewToComponent.clear()
      idToComponent.clear()
      metricTracker.trackResult(renderResult, validatorResult)
      metricTracker.logEvents()
      listeners.forEach { it.lintUpdated(result) }
    }
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  /**
   * Disable the validator. It removes any existing issue visible to the panel.
   */
  fun disable() {
    lintIntegrator.clear()
  }

  /**
   * Find the source [NlComponent] based on issue. If no source is found it returns null.
   */
  @VisibleForTesting
  fun findComponent(result: ValidatorData.Issue, map: BiMap<Long, View>): NlComponent? {
    val view = map[result.mSrcId] ?: return null
    var toReturn = viewToComponent[view]
    if (toReturn == null) {
      // attempt to see if we can do id matching.
      toReturn = idToComponent[view.id]
    }
    return toReturn
  }

  /**
   * It's needed to build bridge from [Long] to [View] to [NlComponent].
   */
  @VisibleForTesting
  fun buildViewToComponentMap(component: NlComponent) {
    val root = tryFindingRootWithViewInfo(component)
    root.viewInfo?.viewObject?.let { viewObj ->
      val view = viewObj as View
      viewToComponent[view] = component

      if (View.NO_ID != view.id) {
        idToComponent[view.id] = component
      }

      component.children.forEach { buildViewToComponentMap(it) }
    }
  }

  /**
   * Look for the root view with appropriate view information from the immediate
   * children. Returns itself if it cannot find one.
   *
   * This is done to support views with data binding.
   */
  @VisibleForTesting
  fun tryFindingRootWithViewInfo(component: NlComponent): NlComponent {
    if (component.viewInfo?.viewObject != null) {
      return component
    }

    component.children.forEach {
      if (it.viewInfo?.viewObject != null) {
        return it
      }
    }

    return component
  }

  override fun dispose() {
    viewToComponent.clear()
    idToComponent.clear()
    listeners.clear()
    lintIntegrator.clear()
  }
}