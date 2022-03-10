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
import com.android.SdkConstants.ATTR_MIN_HEIGHT
import com.android.SdkConstants.ATTR_MIN_WIDTH
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.resources.Density
import com.android.resources.ScreenRound
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.compose.PREVIEW_ANNOTATION_FQNS
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.compose.preview.PreviewElementProvider
import com.android.tools.idea.compose.preview.pickers.properties.utils.findOrParseFromDefinition
import com.android.tools.idea.compose.preview.pickers.properties.utils.getDefaultPreviewDevice
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.projectsystem.isTestFile
import com.android.tools.idea.projectsystem.isUnitTestFile
import com.android.ide.common.resources.Locale
import com.android.tools.idea.compose.preview.hasPreviewElements
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.model.updateConfigurationScreenSize
import com.google.common.annotations.VisibleForTesting
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.android.sdk.CompatibilityRenderTarget
import org.jetbrains.android.uipreview.ModuleClassLoaderManager
import org.jetbrains.android.uipreview.ModuleRenderContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import java.awt.Dimension
import java.util.Objects
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

// Max allowed API
@VisibleForTesting
const val MAX_WIDTH = 2000

@VisibleForTesting
const val MAX_HEIGHT = 2000

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
internal class ComposeAdapterLightVirtualFile(
  name: String,
  content: String,
  private val originFileProvider: () -> VirtualFile?
) : LightVirtualFile(name, content), BackedVirtualFile {
  override fun getParent() = FAKE_LAYOUT_RES_DIR

  override fun getOriginFile(): VirtualFile = originFileProvider() ?: this
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
 * Returns whether a `@Composable` [PREVIEW_ANNOTATION_FQNS] is defined in a valid location, which can be either:
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

internal fun KtNamedFunction.isInTestFile() = isTestFile(this.project, this.containingFile.virtualFile)

internal fun KtNamedFunction.isInUnitTestFile() = isUnitTestFile(this.project, this.containingFile.virtualFile)

/**
 *  Whether this function is not in a test file and is properly annotated
 *  with [PREVIEW_ANNOTATION_FQNS], considering indirect annotations when
 *  the Multipreview flag is enabled, and validating the location of Previews
 *
 *  @see [isValidPreviewLocation]
 */
fun KtNamedFunction.isValidComposePreview() =
  !isInTestFile() && isValidPreviewLocation() && this.toUElementOfType<UMethod>()?.let { hasPreviewElements(it) } == true

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

/** Empty device spec when the user has not specified any. */
private const val NO_DEVICE_SPEC = ""

/**
 * Returns if the device has any state with [ScreenRound.ROUND] configuration.
 */
private fun Device.hasRoundFrame(): Boolean =
  allStates.any { it.hardware.screen.screenRound == ScreenRound.ROUND }

/**
 * Returns the same device without any round screen frames.
 */
private fun Device.withoutRoundScreenFrame(): Device = if (hasRoundFrame()) {
  Device.Builder(this).build().also { newDevice ->
    newDevice.allStates
      .filter { it.hardware.screen.screenRound == ScreenRound.ROUND }
      .onEach { it.hardware.screen.screenRound = ScreenRound.NOTROUND }
  }
}
else this

/**
 * Applies the [PreviewConfiguration] to the given [Configuration].
 *
 * [highestApiTarget] should return the highest api target available for a given [Configuration].
 * [devicesProvider] should return all the devices available for a [Configuration].
 * [defaultDeviceProvider] should return which device to use for a [Configuration] if the device specified in the
 * [PreviewConfiguration.deviceSpec] is not available or does not exist in the devices returned by [devicesProvider].
 *
 * If [useDeviceFrame] is false, the device frame configuration will be not used. For example, if the frame is round, this will be ignored
 * and a regular square frame will be applied. This can be used when the `@Preview` element is not displaying the device decorations so the
 * device frame sizes and ratios would not match.
 *
 * If [customSize] is not null, the dimensions will be forced
 * in the resulting configuration.
 */
private fun PreviewConfiguration.applyTo(renderConfiguration: Configuration,
                                         highestApiTarget: (Configuration) -> IAndroidTarget?,
                                         devicesProvider: (Configuration) -> Collection<Device>,
                                         defaultDeviceProvider: (Configuration) -> Device?,
                                         @AndroidDpCoordinate customSize: Dimension? = null,
                                         useDeviceFrame: Boolean = false) {
  fun updateRenderConfigurationTargetIfChanged(newTarget: CompatibilityRenderTarget) {
    if ((renderConfiguration.target as? CompatibilityRenderTarget)?.hashString() != newTarget.hashString()) {
      renderConfiguration.target = newTarget
    }
  }

  renderConfiguration.startBulkEditing()
  if (apiLevel != UNDEFINED_API_LEVEL) {
    highestApiTarget(renderConfiguration)?.let {
      updateRenderConfigurationTargetIfChanged(CompatibilityRenderTarget(it, apiLevel, it))
    }
  }
  else {
    // Use the highest available one when not defined.
    highestApiTarget(renderConfiguration)?.let {
      updateRenderConfigurationTargetIfChanged(CompatibilityRenderTarget(it, it.version.apiLevel, it))
    }
  }

  if (theme != null) {
    renderConfiguration.setTheme(theme)
  }

  renderConfiguration.locale = Locale.create(locale)
  renderConfiguration.uiModeFlagValue = uiMode
  renderConfiguration.fontScale = max(0f, fontScale)

  val allDevices = devicesProvider(renderConfiguration)
  val device = allDevices.findOrParseFromDefinition(deviceSpec) ?: defaultDeviceProvider(renderConfiguration)
  if (device != null) {
    // Ensure the device is reset
    renderConfiguration.setEffectiveDevice(null, null)
    // If the user is not using the device frame, we never want to use the round frame around. See b/215362733
    renderConfiguration.setDevice(
      if (useDeviceFrame) device else device.withoutRoundScreenFrame(),
      false)
  }

  customSize?.let {
    // When the device frame is not being displayed and the user has given us some specific sizes, we want to apply those to the
    // device itself.
    // This is to match the intuition that those sizes always determine the size of the composable.
    renderConfiguration.device?.let { device ->
      // The PX are converted to DP by multiplying it by the dpiFactor that is the ratio of the current dpi vs the default dpi (160).
      val dpiFactor = renderConfiguration.density.dpiValue / Density.DEFAULT_DENSITY
      updateConfigurationScreenSize(renderConfiguration,
                                    it.width * dpiFactor,
                                    it.height * dpiFactor, device)
    }
  }
  renderConfiguration.finishBulkEditing()
}

/**
 * If specified in the [PreviewElement], this method will return the `widthDp` and `heightDp` dimensions as a [Pair] as long as
 * the device frame is disabled (i.e. `showDecorations` is false).
 */
@AndroidDpCoordinate
private fun PreviewElement.getCustomDeviceSize(): Dimension? =
  if (!displaySettings.showDecoration && configuration.width != -1 && configuration.height != -1) {
    Dimension(configuration.width, configuration.height)
  }
  else null

/**
 * Applies the [PreviewElement] settings to the given [renderConfiguration].
 */
fun PreviewElement.applyTo(renderConfiguration: Configuration) {
  configuration.applyTo(renderConfiguration,
                        { it.configurationManager.highestApiTarget },
                        { it.configurationManager.devices },
                        { it.configurationManager.getDefaultPreviewDevice() },
                        getCustomDeviceSize(),
                        this.displaySettings.showDecoration)
}

@TestOnly
fun PreviewConfiguration.applyConfigurationForTest(renderConfiguration: Configuration,
                                                   highestApiTarget: (Configuration) -> IAndroidTarget?,
                                                   devicesProvider: (Configuration) -> Collection<Device>,
                                                   defaultDeviceProvider: (Configuration) -> Device?,
                                                   useDeviceFrame: Boolean = false) {
  applyTo(renderConfiguration, highestApiTarget, devicesProvider, defaultDeviceProvider, null, useDeviceFrame)
}

@TestOnly
fun PreviewElement.applyConfigurationForTest(renderConfiguration: Configuration,
                                             highestApiTarget: (Configuration) -> IAndroidTarget?,
                                             devicesProvider: (Configuration) -> Collection<Device>,
                                             defaultDeviceProvider: (Configuration) -> Device?) {
  configuration.applyTo(renderConfiguration, highestApiTarget, devicesProvider, defaultDeviceProvider, getCustomDeviceSize())
}

/**
 * Contains settings for rendering.
 */
data class PreviewConfiguration internal constructor(val apiLevel: Int,
                                                     val theme: String?,
                                                     val width: Int,
                                                     val height: Int,
                                                     val locale: String,
                                                     val fontScale: Float,
                                                     val uiMode: Int,
                                                     val deviceSpec: String) {
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
                    locale: String?,
                    fontScale: Float?,
                    uiMode: Int?,
                    device: String?): PreviewConfiguration =
    // We only limit the sizes. We do not limit the API because using an incorrect API level will throw an exception that
      // we will handle and any other error.
      PreviewConfiguration(apiLevel = apiLevel ?: UNDEFINED_API_LEVEL,
                           theme = theme,
                           width = width.truncate(1, MAX_WIDTH) ?: UNDEFINED_DIMENSION,
                           height = height.truncate(1, MAX_HEIGHT) ?: UNDEFINED_DIMENSION,
                           locale = locale ?: "",
                           fontScale = fontScale ?: 1f,
                           uiMode = uiMode ?: 0,
                           deviceSpec = device ?: NO_DEVICE_SPEC)
  }
}

/** Configuration equivalent to defining a `@Preview` annotation with no parameters */
private val nullConfiguration = PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null, null)

enum class DisplayPositioning {
  TOP, // Previews with this priority will be displayed at the top
  NORMAL
}

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
                                  val backgroundColor: String?,
                                  val displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL)

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
  /** [ComposeLibraryNamespace] to identify the package name used for this [PreviewElement] annotations */
  val composeLibraryNamespace: ComposeLibraryNamespace

  /** Fully Qualified Name of the composable method */
  val composableMethodFqn: String

  /** Settings that affect how the [PreviewElement] is presented in the preview surface */
  val displaySettings: PreviewDisplaySettings

  /** [SmartPsiElementPointer] to the preview element definition.
   *  This means the annotation annotating the composable method, that
   *  won't necessarily be a '@Preview' when Multipreview is enabled.
   */
  val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?

  /** [SmartPsiElementPointer] to the preview body. This is the code that will be ran during preview */
  val previewBodyPsi: SmartPsiElementPointer<PsiElement>?

  /** [PsiFile] containing this PreviewElement. null if there is not source file, like in synthetic preview elements */
  val containingFile: PsiFile?
    get() = runReadAction {
      previewBodyPsi?.containingFile ?: previewElementDefinitionPsi?.containingFile
    }

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
abstract class PreviewElementInstance : PreviewElement, XmlSerializable {
  /**
   * Unique identifier that can be used for filtering.
   */
  abstract val instanceId: String

  /**
   * Whether the Composable being previewed contains animations. If true, the Preview should allow opening the animation inspector.
   */
  var hasAnimations = false

  override fun toPreviewXml(xmlBuilder: PreviewXmlBuilder): PreviewXmlBuilder {
    val matchParent = displaySettings.showDecoration
    val width = dimensionToString(configuration.width, if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT)
    val height = dimensionToString(configuration.height, if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT)
    xmlBuilder
      .setRootTagName(composeLibraryNamespace.composableAdapterName)
      .androidAttribute(ATTR_LAYOUT_WIDTH, width)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, height)
      // Compose will fail if the top parent is 0,0 in size so avoid that case by setting a min 1x1 parent (b/169230467).
      .androidAttribute(ATTR_MIN_WIDTH, "1px")
      .androidAttribute(ATTR_MIN_HEIGHT, "1px")
      // [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call
      .toolsAttribute("composableName", composableMethodFqn)

    if (displaySettings.showBackground) {
      xmlBuilder.androidAttribute(ATTR_BACKGROUND, displaySettings.backgroundColor ?: DEFAULT_PREVIEW_BACKGROUND)
    }

    return xmlBuilder
  }

  final override fun equals(other: Any?): Boolean {
    // PreviewElement objects can be repeated in the same element. They are considered equals only if they annotate exactly the same
    // element with the same configuration.
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PreviewElementInstance

    return composableMethodFqn == other.composableMethodFqn &&
           instanceId == other.instanceId &&
           displaySettings == other.displaySettings &&
           configuration == other.configuration
  }

  override fun hashCode(): Int =
    Objects.hash(composableMethodFqn, displaySettings, configuration, instanceId)
}

/**
 * Definition of a single preview element instance. This represents a `Preview` with no parameters.
 */
class SinglePreviewElementInstance(override val composableMethodFqn: String,
                                   override val displaySettings: PreviewDisplaySettings,
                                   override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
                                   override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
                                   override val configuration: PreviewConfiguration,
                                   override val composeLibraryNamespace: ComposeLibraryNamespace) : PreviewElementInstance() {
  override val instanceId: String = composableMethodFqn

  companion object {
    @JvmStatic
    @TestOnly
    fun forTesting(composableMethodFqn: String,
                   displayName: String = "", groupName: String? = null,
                   showDecorations: Boolean = false,
                   showBackground: Boolean = false,
                   backgroundColor: String? = null,
                   displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL,
                   configuration: PreviewConfiguration = nullConfiguration,
                   uiToolingPackageName: ComposeLibraryNamespace = ComposeLibraryNamespace.ANDROIDX_COMPOSE_WITH_API) =
      SinglePreviewElementInstance(composableMethodFqn,
                                   PreviewDisplaySettings(
                                     displayName,
                                     groupName,
                                     showDecorations,
                                     showBackground,
                                     backgroundColor,
                                     displayPositioning),
                                   null, null,
                                   configuration,
                                   uiToolingPackageName)
  }
}

private class ParametrizedPreviewElementInstance(private val basePreviewElement: PreviewElement,
                                                 parameterName: String,
                                                 val providerClassFqn: String,
                                                 val index: Int) : PreviewElementInstance(), PreviewElement by basePreviewElement {
  override val instanceId: String = "$composableMethodFqn#$parameterName$index"

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
 * If the [PreviewElement] is a [ParametrizedPreviewElementInstance], returns the provider class FQN and the target value index.
 */
internal fun PreviewElement.previewProviderClassAndIndex() =
  if (this is ParametrizedPreviewElementInstance) Pair(providerClassFqn, index) else null

/**
 * Definition of a preview element that can spawn multiple [PreviewElement]s based on parameters.
 */
class ParametrizedPreviewElementTemplate(private val basePreviewElement: PreviewElement,
                                         val parameterProviders: Collection<PreviewParameter>) : PreviewElementTemplate, PreviewElement by basePreviewElement {
  /**
   * Returns a [Sequence] of "instantiated" [PreviewElement]s. The will be [PreviewElement] populated with data from the parameter
   * providers.
   */
  override fun instances(): Sequence<PreviewElementInstance> {
    assert(parameterProviders.isNotEmpty()) { "ParametrizedPreviewElement used with no parameters" }

    val file = basePreviewElement.containingFile ?: return sequenceOf()
    if (parameterProviders.size > 1) {
      Logger.getInstance(ParametrizedPreviewElementTemplate::class.java).warn(
        "Currently only one ParameterProvider is supported, rest will be ignored")
    }

    val moduleRenderContext = ModuleRenderContext.forFile(file)
    val classLoader = ModuleClassLoaderManager.get().getPrivate(ParametrizedPreviewElementTemplate::class.java.classLoader,
                                                                moduleRenderContext, this)
    try {
      return parameterProviders.map { previewParameter ->
        try {
          val parameterProviderClass = classLoader.loadClass(previewParameter.providerClassFqn).kotlin
          val parameterProviderSizeMethod = parameterProviderClass
            .functions
            .single { "getCount" == it.name }
            .also { it.isAccessible = true }
          val parameterProvider = parameterProviderClass.constructors
            .single { it.parameters.isEmpty() } // Find the default constructor
            .also { it.isAccessible = true }
            .call()
          val providerCount = min((parameterProviderSizeMethod.call(parameterProvider) as? Int ?: 0), previewParameter.limit)

          return (0 until providerCount).map { index ->
            ParametrizedPreviewElementInstance(basePreviewElement = basePreviewElement,
                                               parameterName = previewParameter.name,
                                               index = index,
                                               providerClassFqn = previewParameter.providerClassFqn)
          }.asSequence()
        }
        catch (e: Throwable) {
          Logger.getInstance(
            ParametrizedPreviewElementTemplate::class.java).debug {
            "Failed to instantiate ${previewParameter.providerClassFqn} parameter provider"
          }
        }

        return sequenceOf()
      }.first()
    }
    finally {
      ModuleClassLoaderManager.get().release(classLoader, this)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ParametrizedPreviewElementTemplate

    return basePreviewElement == other.basePreviewElement &&
           parameterProviders == other.parameterProviders
  }

  override fun hashCode(): Int =
    Objects.hash(basePreviewElement, parameterProviders)
}

/**
 * A [PreviewElementProvider] that instantiates any [PreviewElementTemplate]s in the [delegate].
 */
class PreviewElementTemplateInstanceProvider(private val delegate: PreviewElementProvider<PreviewElement>)
  : PreviewElementProvider<PreviewElementInstance> {
  override suspend fun previewElements(): Sequence<PreviewElementInstance> =
    delegate.previewElements().flatMap {
      when (it) {
        is PreviewElementTemplate -> it.instances()
        is PreviewElementInstance -> sequenceOf(it)
        else -> {
          Logger.getInstance(PreviewElementTemplateInstanceProvider::class.java).warn(
            "Class was not instance or template ${it::class.qualifiedName}")
          emptySequence()
        }
      }
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
   * Returns if this file contains `@Composable` methods. This is similar to [hasPreviewMethods] but allows deciding
   * if this file might allow previews to be added.
   */
  fun hasComposableMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [PreviewElement]s present in the passed Kotlin [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  suspend fun findPreviewMethods(project: Project, vFile: VirtualFile): Collection<PreviewElement>
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

private val sourceOffsetComparator = compareBy<PreviewElement> { it.sourceOffset }
private val displayPriorityComparator = compareBy<PreviewElement> { it.displaySettings.displayPositioning }
private val lexicographicalNameComparator = compareBy<PreviewElement> {it.displaySettings.name }

/**
 * Sorts the [PreviewElement]s by [DisplayPositioning] (top first) and then by source code line number, smaller first.
 * When Multipreview is enabled, different Previews may have the same [PreviewElement.previewElementDefinitionPsi] value,
 * and those will be ordered lexicographically between them, as the actual Previews may be defined in different files and/or
 * in a not structured way, so it is not possible to order them based on code source offsets.
 */
fun <T : PreviewElement> Collection<T>.sortByDisplayAndSourcePosition(): List<T> = runReadAction {
  sortedWith(displayPriorityComparator.thenComparing(sourceOffsetComparator).thenComparing(lexicographicalNameComparator))
}