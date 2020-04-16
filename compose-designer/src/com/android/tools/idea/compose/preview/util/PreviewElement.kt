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

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.uipreview.ModuleClassLoaderManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties

/** Preview element name */
internal const val PREVIEW_NAME = "Preview"

/** Package containing the preview definitions */
private const val PREVIEW_PACKAGE = "androidx.ui.tooling.preview"

/** Only composables with this annotation will be rendered to the surface */
internal const val PREVIEW_ANNOTATION_FQN = "$PREVIEW_PACKAGE.$PREVIEW_NAME"

internal const val COMPOSABLE_ANNOTATION_FQN = "androidx.compose.Composable"

/** View included in the runtime library that will wrap the @Composable element so it gets rendered by layoutlib */
internal const val COMPOSE_VIEW_ADAPTER = "$PREVIEW_PACKAGE.ComposeViewAdapter"

/** Annotation FQN for `Preview` annotated parameters */
internal const val PREVIEW_PARAMETER_FQN = "$PREVIEW_PACKAGE.PreviewParameter"

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

// Max allowed API
@VisibleForTesting
const val MAX_WIDTH = 2000

@VisibleForTesting
const val MAX_HEIGHT = 2000

const val WIDTH_PARAMETER = "widthDp"
const val HEIGHT_PARAMETER = "heightDp"

/**
 * Default background to be used by the rendered elements when showBackground is set to true.
 */
private const val DEFAULT_PREVIEW_BACKGROUND = "?android:attr/windowBackground"

internal val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used as adapter
 * to be able to preview composable functions.
 * The contents of the file only reside in memory and contain some XML that will be passed to Layoutlib.
 */
internal class ComposeAdapterLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

/**
 * Transforms a dimension given on the [PreviewConfiguration] into the string value. If the dimension is [UNDEFINED_DIMENSION], the value
 * is converted to `wrap_content`. Otherwise, the value is returned concatenated with `dp`.
 * @param dimension the dimension in dp or [UNDEFINED_DIMENSION]
 * @param defaultValue the value to be used when the given dimension is [UNDEFINED_DIMENSION]
 */
fun dimensionToString(dimension: Int, defaultValue: String = VALUE_WRAP_CONTENT) = if (dimension == UNDEFINED_DIMENSION) {
  defaultValue
}
else {
  "${dimension}dp"
}

private fun KtClass.hasDefaultConstructor() = allConstructors.isEmpty().or(allConstructors.any { it.getValueParameters().isEmpty() })

/**
 * Returns whether a `@Composable` [PREVIEW_ANNOTATION_FQN] is defined in a valid location, which can be either:
 * 1. Top-level functions
 * 2. Non-nested functions defined in top-level classes that have a default (no parameter) constructor
 *
 */
internal fun KtNamedFunction.isValidPreviewLocation(): Boolean {
  if (isTopLevel) {
    return true
  }

  if (parentOfType<KtNamedFunction>() == null) {
    // This is not a nested method
    val containingClass = containingClass()
    if (containingClass != null) {
      // We allow functions that are not top level defined in top level classes that have a default (no parameter) constructor.
      if (containingClass.isTopLevel() && containingClass.hasDefaultConstructor()) {
        return true
      }
    }
  }
  return false
}

/**
 *  Whether this function is properly annotated with [PREVIEW_ANNOTATION_FQN] and is defined in a valid location.
 *
 *  @see [isValidPreviewLocation]
 */
fun KtNamedFunction.isValidComposePreview() =
  isValidPreviewLocation() && annotationEntries.any { annotation -> annotation.fqNameMatches(PREVIEW_ANNOTATION_FQN) }

/**
 * Truncates the given dimension value to fit between the [min] and [max] values. If the receiver is null,
 * this will return null.
 */
private fun Int?.truncate(min: Int, max: Int): Int? {
  if (this == null) {
    return null
  }

  if (this == UNDEFINED_DIMENSION) {
    return UNDEFINED_DIMENSION
  }

  return minOf(maxOf(this, min), max)
}

/**
 * Contain settings for rendering
 */
data class PreviewConfiguration internal constructor(val apiLevel: Int,
                                                     val theme: String?,
                                                     val width: Int,
                                                     val height: Int,
                                                     val fontScale: Float) {
  fun applyTo(renderConfiguration: Configuration) {
    if (apiLevel != UNDEFINED_API_LEVEL) {
      val highestTarget = renderConfiguration.configurationManager.highestApiTarget!!

      renderConfiguration.target = CompatibilityRenderTarget(highestTarget, apiLevel, null)
    }

    if (theme != null) {
      renderConfiguration.setTheme(theme)
    }

    renderConfiguration.fontScale = max(0f, fontScale)
  }

  companion object {
    /**
     * Cleans the given values and creates a PreviewConfiguration. The cleaning ensures that the user inputted value are within
     * reasonable values before the PreviewConfiguration is created
     */
    @JvmStatic
    fun cleanAndGet(apiLevel: Int?,
                    theme: String?,
                    width: Int?,
                    height: Int?,
                    fontScale: Float?): PreviewConfiguration =
    // We only limit the sizes. We do not limit the API because using an incorrect API level will throw an exception that
      // we will handle and any other error.
      PreviewConfiguration(apiLevel = apiLevel ?: UNDEFINED_API_LEVEL,
                           theme = theme,
                           width = width.truncate(1, MAX_WIDTH) ?: UNDEFINED_DIMENSION,
                           height = height.truncate(1, MAX_HEIGHT) ?: UNDEFINED_DIMENSION,
                           fontScale = fontScale ?: 1f)
  }
}

/** Configuration equivalent to defining a `@Preview` annotation with no parameters */
private val nullConfiguration = PreviewConfiguration.cleanAndGet(null, null, null, null, null)

/**
 * Settings that modify how a [PreviewElement] is rendered
 *
 * @param name display name of this preview element
 * @param group name that allows multiple previews in separate groups
 * @param showDecoration when true, the system decorations (navigation and status bars) should be displayed as part of the render
 * @param showBackground when true, the preview will be rendered with the material background as background color by default
 * @param backgroundColor when [showBackground] is true, this is the background color to be used by the preview. If null, the default
 * activity background specified in the system theme will be used.
 */
data class PreviewDisplaySettings(val name: String,
                                  val group: String?,
                                  val showDecoration: Boolean,
                                  val showBackground: Boolean,
                                  val backgroundColor: String?)

/**
 * Definition of a preview parameter provider. This is defined by annotating parameters with `PreviewParameter`
 *
 * @param name the name of the parameter using the provider
 * @param index the parameter position
 * @param providerClassFqn the class name for the provider
 * @param limit the limit passed to the annotation
 */
data class PreviewParameter(val name: String,
                            val index: Int,
                            val providerClassFqn: String,
                            val limit: Int)

/**
 * Definition of a preview element
 */
interface PreviewElement {
  /** Fully Qualified Name of the composable method */
  val composableMethodFqn: String

  /** Settings that affect how the [PreviewElement] is presented in the preview surface */
  val displaySettings: PreviewDisplaySettings

  /** [SmartPsiElementPointer] to the preview element definition */
  val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?

  /** [SmartPsiElementPointer] to the preview body. This is the code that will be ran during preview */
  val previewBodyPsi: SmartPsiElementPointer<PsiElement>?

  /** Preview element configuration that affects how LayoutLib resolves the resources */
  val configuration: PreviewConfiguration
}

/**
 * Definition of a preview element template. This element can dynamically spawn one or more [PreviewElementInstance]s.
 */
interface PreviewElementTemplate : PreviewElement {
  fun instances(): Sequence<PreviewElementInstance>
}

/**
 * Definition of a preview element
 */
interface PreviewElementInstance : PreviewElement, XmlSerializable {
  override fun toPreviewXml(xmlBuilder: PreviewXmlBuilder): PreviewXmlBuilder {
    val matchParent = displaySettings.showDecoration
    val width = dimensionToString(configuration.width, if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT)
    val height = dimensionToString(configuration.height, if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT)
    xmlBuilder
      .androidAttribute(ATTR_LAYOUT_WIDTH, width)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, height)
      // [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call
      .toolsAttribute("composableName", composableMethodFqn)

    if (displaySettings.showBackground) {
      xmlBuilder.androidAttribute(ATTR_BACKGROUND, displaySettings.backgroundColor ?: DEFAULT_PREVIEW_BACKGROUND)
    }

    return xmlBuilder
  }
}

/**
 * Definition of a single preview element instance. This represents a `Preview` with no parameters.
 */
data class SinglePreviewElementInstance(override val composableMethodFqn: String,
                                        override val displaySettings: PreviewDisplaySettings,
                                        override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
                                        override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
                                        override val configuration: PreviewConfiguration) : PreviewElementInstance {
  companion object {
    @JvmStatic
    @TestOnly
    fun forTesting(composableMethodFqn: String,
                   displayName: String = "", groupName: String? = null,
                   showDecorations: Boolean = false,
                   showBackground: Boolean = false,
                   backgroundColor: String? = null,
                   configuration: PreviewConfiguration = nullConfiguration) =
      SinglePreviewElementInstance(composableMethodFqn,
                                   PreviewDisplaySettings(
                                     displayName,
                                     groupName,
                                     showDecorations,
                                     showBackground,
                                     backgroundColor),
                                   null, null,
                                   configuration)
  }
}

private data class ParametrizedPreviewElementInstance(private val basePreviewElement: PreviewElement,
                                                      private val parameterName: String,
                                                      val providerClassFqn: String,
                                                      val index: Int) : PreviewElementInstance, PreviewElement by basePreviewElement {
  override val displaySettings: PreviewDisplaySettings = PreviewDisplaySettings(
    "${basePreviewElement.displaySettings.name} ($parameterName $index)",
    basePreviewElement.displaySettings.group,
    basePreviewElement.displaySettings.showDecoration,
    basePreviewElement.displaySettings.showBackground,
    basePreviewElement.displaySettings.backgroundColor
  )

  override fun toPreviewXml(xmlBuilder: PreviewXmlBuilder): PreviewXmlBuilder {
    super.toPreviewXml(xmlBuilder)
      // The index within the provider of the element to be rendered
      .toolsAttribute("parameterProviderIndex", index.toString())
      // The FQN of the ParameterProvider class
      .toolsAttribute("parameterProviderClass", providerClassFqn)

    return xmlBuilder
  }
}

/**
 * Definition of a preview element that can spawn multiple [PreviewElement]s based on parameters.
 */
data class ParametrizedPreviewElementTemplate(private val basePreviewElement: PreviewElement,
                                              val parameterProviders: Collection<PreviewParameter>) : PreviewElementTemplate, PreviewElement by basePreviewElement {
  /**
   * Returns a [Sequence] of "instantiated" [PreviewElement]s. The will be [PreviewElement] populated with data from the parameter
   * providers.
   */
  override fun instances(): Sequence<PreviewElementInstance> {
    assert(parameterProviders.isNotEmpty()) { "ParametrizedPreviewElement used with no parameters" }

    val module = ReadAction.compute<Module?, Throwable> {
      basePreviewElement.previewBodyPsi?.element?.module
    } ?: return sequenceOf()
    if (parameterProviders.size > 1) {
      Logger.getInstance(ParametrizedPreviewElementTemplate::class.java).warn(
        "Currently only one ParameterProvider is supported, rest will be ignored")
    }

    return parameterProviders.map {
      try {
        val parameterProviderClass = ModuleClassLoaderManager.get().getShared(null, module).loadClass(it.providerClassFqn).kotlin
        val parameterProviderSizeMethod = parameterProviderClass.memberProperties.single { "count" == it.name }
        val parameterProvider = parameterProviderClass.createInstance()
        val providerCount = min((parameterProviderSizeMethod.call(parameterProvider) as? Int ?: 0), it.limit)

        return (0 until providerCount).map { index ->
          ParametrizedPreviewElementInstance(basePreviewElement = basePreviewElement,
                                             parameterName = it.name,
                                             index = index,
                                             providerClassFqn = it.providerClassFqn)
        }.asSequence()
      }
      catch (e: Throwable) {
        Logger.getInstance(
          ParametrizedPreviewElementTemplate::class.java).debug { "Failed to instantiate ${it.providerClassFqn} parameter provider" }
      }

      return sequenceOf()
    }.first()
  }
}

/**
 * Interface to be implemented by classes able to find [PreviewElement]s on [VirtualFile]s.
 */
interface FilePreviewElementFinder {
  /**
   * Returns whether this Preview element finder might apply to the given Kotlin file.
   * The main difference with [findPreviewMethods] is that method might be called on Dumb mode so it must not use any indexes.
   */
  fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [PreviewElement]s present in the passed Kotlin [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  @Slow
  fun findPreviewMethods(project: Project, vFile: VirtualFile): Sequence<PreviewElement> {
    assert(!DumbService.getInstance(project).isDumb) { "findPreviewMethods can not be called on dumb mode" }

    val psiFile = ReadAction.compute<PsiFile?, Throwable> { PsiManager.getInstance(project).findFile(vFile) } ?: return emptySequence()
    val uFile: UFile = psiFile.toUElement() as? UFile ?: return emptySequence()

    return findPreviewMethods(uFile)
  }

  /**
   * Returns all the [PreviewElement]s present in the passed [UFile].
   *
   * This method always runs on smart mode.
   */
  @Slow
  fun findPreviewMethods(uFile: UFile): Sequence<PreviewElement>
}

/**
 * Returns the source offset within the file of the [PreviewElement].
 * We try to read the position of the method but fallback to the position of the annotation if the method body is not valid anymore.
 * If the passed element is null or the position can not be read, this method will return -1.
 *
 * This property needs a [ReadAction] to be read.
 */
private val PreviewElement?.sourceOffset: Int
  get() = this?.previewElementDefinitionPsi?.element?.startOffset ?: -1

/**
 * Sorts the [PreviewElement]s by source code line number, smaller first. This comparator needs
 * to be used under a [ReadAction].
 */
val previewElementComparatorBySourcePosition = Comparator<PreviewElement> { o1, o2 ->
  o1.sourceOffset - o2.sourceOffset
}

/**
 * Sorts the [PreviewElement]s in alphabetical order of their display name.
 */
val previewElementComparatorByDisplayName = Comparator<PreviewElement> { o1, o2 ->
  o1.displaySettings.name.compareTo(o2.displaySettings.name)
}