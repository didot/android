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
package com.android.tools.idea.compose.preview.util

import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.scene.render
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeCallbacks
import com.android.tools.idea.uibuilder.scene.executeInRenderSession
import com.intellij.openapi.diagnostic.Logger


/**
 * Extension implementing some heuristics to detect Compose rendering errors. This allows to identify render
 * errors better.
 */
internal fun RenderResult?.isComposeErrorResult(): Boolean {
  if (this == null) {
    return true
  }

  // Compose renders might fail with onLayout exceptions hiding actual errors. This will return an empty image
  // result. We can detect this by checking for a 1x1 image and the logger having errors.
  if (logger.hasErrors() && renderedImage.width == 1 && renderedImage.height == 1) {
    return true
  }

  return logger.brokenClasses.values
    .any {
      it is ReflectiveOperationException && it.stackTrace.any { ex -> COMPOSE_VIEW_ADAPTER_FQN == ex.className }
    }
}

/**
 * Utility method that requests a given [LayoutlibSceneManager] to render. It applies logic that specific to compose to render components
 * that do not simply render in a first pass.
 */
internal suspend fun LayoutlibSceneManager.requestComposeRender() {
  render()
  if (StudioFlags.COMPOSE_PREVIEW_DOUBLE_RENDER.get()) {
    executeCallbacks()
    render()
  }
}

/**
 * Uses the `androidx.compose.runtime.HotReloader` in the Compose runtime mechanism to force a recomposition.
 * This action will run in the render thread so this method returns immediately with a future that will complete when the
 * invalidation has completed.
 * If [forceLayout] is true, a `View#requestLayout` will be sent to the `ComposeViewAdapter` to force a relayout of the whole view.
 */
internal suspend fun LayoutlibSceneManager.invalidateCompositions(forceLayout: Boolean) {
  executeInRenderSession {
    val composeViewAdapter = renderResult.findComposeViewAdapter() ?: return@executeInRenderSession
    try {
      val hotReloader = composeViewAdapter.javaClass.classLoader.loadClass("androidx.compose.runtime.HotReloader")
      val hotReloaderInstance = hotReloader.getDeclaredField("Companion").let {
        it.isAccessible = true
        it.get(null)
      }
      val saveStateAndDisposeMethod = hotReloaderInstance.javaClass.getDeclaredMethod("saveStateAndDispose", Any::class.java).also {
        it.isAccessible = true
      }
      val loadStateAndCompose = hotReloaderInstance.javaClass.getDeclaredMethod("loadStateAndCompose", Any::class.java).also {
        it.isAccessible = true
      }
      val state = saveStateAndDisposeMethod.invoke(hotReloaderInstance, composeViewAdapter)
      loadStateAndCompose.invoke(hotReloaderInstance, state)
      if (forceLayout) {
        composeViewAdapter::class.java.getMethod("requestLayout").invoke(composeViewAdapter)
      }
    }
    catch (t: Throwable) {
      Logger.getInstance(RenderResult::class.java).warn(t)
    }
  }
}

/**
 * Returns all the [LayoutlibSceneManager] belonging to the [DesignSurface].
 */
internal val DesignSurface<*>.layoutlibSceneManagers: Sequence<LayoutlibSceneManager>
  get() = models.asSequence()
    .mapNotNull { getSceneManager(it) }
    .filterIsInstance<LayoutlibSceneManager>()