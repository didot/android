/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_PARENT_TAG
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.NULL_RESOURCE
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.TOOLS_URI
import com.android.annotations.VisibleForTesting
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceVisitor
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.resources.ResourceVisibility
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property2.api.ActionIconButton
import com.android.tools.idea.common.property2.api.HelpSupport
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.aar.AarResourceItem
import com.android.tools.idea.res.parseColor
import com.android.tools.idea.res.resolveAsIcon
import com.android.tools.idea.res.resolveColor
import com.android.tools.idea.uibuilder.property2.support.ColorSelectionAction
import com.android.tools.idea.uibuilder.property2.support.EmptyBrowseActionIconButton
import com.android.tools.idea.uibuilder.property2.support.IdEnumSupport
import com.android.tools.idea.uibuilder.property2.support.OpenResourceManagerAction
import com.android.tools.idea.uibuilder.property2.support.ToggleShowResolvedValueAction
import com.android.utils.HashCodes
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.text.nullize
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.dom.AttributeProcessingUtil
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.awt.Color
import javax.swing.Icon

/**
 * [PropertyItem] for Nele layouts, menus, preferences.
 *
 * Enables editing of attributes from an XmlTag that is wrapped
 * in one or more [NlComponent]s. If there are multiple components
 * only common values are shown. Setting the [value] property
 * writes the value back to all components.
 *
 * Resolved values are computed using the [ResourceResolver] from
 * the current [Configuration]. If the user changes the current
 * configuration the properties panel should be updated with
 * potentially different resolved values.
 */
open class NelePropertyItem(
  override val namespace: String,
  override val name: String,
  val type: NelePropertyType,
  val definition: AttributeDefinition?,
  val libraryName: String,
  val model: NelePropertiesModel,
  val optionalValue: Any?,
  val components: List<NlComponent>
) : PropertyItem, HelpSupport {

  override var value: String?
    get() {
      val rawValue = rawValue
      return if (model.showResolvedValues) resolveValue(rawValue) else rawValue
    }
    set(value) {
      model.setPropertyValue(this, value)
    }

  override val defaultValue: String?
    get() {
      val defValue = model.provideDefaultValue(this) ?: return null
      return resolveValue(asResourceValue(defValue.reference) ?: defValue)
    }

  override val namespaceIcon: Icon?
    get() = when (namespace) {
      "",
      ANDROID_URI,
      AUTO_URI -> null
      TOOLS_URI -> StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE
      else -> StudioIcons.LayoutEditor.Toolbar.INSERT_VERT_CHAIN
    }

  override val tooltipForName: String
    get() = computeTooltipForName()

  override val tooltipForValue: String
    get() = computeTooltipForValue()

  override val isReference: Boolean
    get() = isReferenceValue(rawValue)

  open val rawValue: String?
    get() = model.getPropertyValue(this)

  override val resolvedValue: String?
    get() = resolveValue(rawValue)

  // TODO: Use the namespace resolver in ResourceHelper when it no longer returns [ResourceNamespace.Resolver.TOOLS_ONLY].
  // We need to find the prefix even when namespacing is turned off.
  val namespaceResolver: ResourceNamespace.Resolver
    get() {
      val element = firstTag ?: return ResourceNamespace.Resolver.EMPTY_RESOLVER

      fun withTag(compute: (XmlTag) -> String?): String? {
        return ReadAction.compute<String, RuntimeException> {
          if (!element.isValid) {
            null
          }
          else {
            val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
            tag?.let(compute).let(StringUtil::nullize)
          }
        }
      }

      return object : ResourceNamespace.Resolver {
        override fun uriToPrefix(namespaceUri: String): String? = withTag { tag -> tag.getPrefixByNamespace(namespaceUri) }
        override fun prefixToUri(namespacePrefix: String): String? = withTag { tag -> tag.getNamespaceByPrefix(namespacePrefix).nullize() }
      }
    }

  override val editingSupport = object : EditingSupport {
    override val completion = { getCompletionValues() }
    override val validation = { text: String? -> validate(text) }
    override val execution = { runnable: Runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable) }
  }

  // TODO: b/121259587 Implement help
  override fun help() {
  }

  // TODO: b/121259587 Implement secondaryHelp
  override fun secondaryHelp() {
  }

  override fun browse() {
    val tag = firstTag ?: return
    val attribute = tag.getAttribute(name, namespace) ?: return
    val attributeValue = attribute.valueElement ?: return
    val file = tag.containingFile
    val ref = file.findReferenceAt(attributeValue.textOffset)
    val navigable = ref?.resolve() as? Navigatable ?: return
    if (navigable != attributeValue) {
      navigable.navigate(true)
    }
  }

  override val designProperty: NelePropertyItem
    get() = if (namespace == TOOLS_URI) this else
      NelePropertyItem(TOOLS_URI, name, type, definition, libraryName, model, optionalValue, components)

  override fun equals(other: Any?) =
    when (other) {
      is NelePropertyItem -> namespace == other.namespace && name == other.name
      else -> false
    }

  override fun hashCode() = HashCodes.mix(namespace.hashCode(), name.hashCode())

  private fun resolveValue(value: String?): String? {
    return resolveValue(asResourceValue(value)) ?: value
  }

  private fun asResourceValue(value: String?): ResourceValue? {
    if (value == null) return null
    return asResourceValue(ResourceUrl.parse(value)?.resolve(defaultNamespace, namespaceResolver))
  }

  private fun asResourceValue(reference: ResourceReference?): ResourceValue? {
    if (reference == null) {
      return null
    }
    if (reference.resourceType == ResourceType.ATTR) {
      val resValue = resolver?.findItemInTheme(reference) ?: return null
      return resolver?.resolveResValue(resValue)
    }
    else {
      return resolver?.getResolvedResource(reference)
    }
  }

  private fun resolveValue(resValue: ResourceValue?): String? {
    if (resValue == null) {
      return null
    }
    when (resValue.resourceType) {
      ResourceType.BOOL,
      ResourceType.DIMEN,
      ResourceType.FRACTION,
      ResourceType.STYLE_ITEM,  // Hack for default values from LayoutLib
      ResourceType.INTEGER,
      ResourceType.STRING -> if (resValue.value != null) return resValue.value
      ResourceType.COLOR -> if (resValue.value?.startsWith("#") == true) return resValue.value
      else -> {}
    }
    // The value of the remaining resource types are file names or ids.
    // We don't want to show the file names and the ids don't have a value.
    // Instead show the url of this resolved resource.
    return resValue.asReference().getRelativeResourceUrl(defaultNamespace, namespaceResolver).toString()
  }

  val resolver: ResourceResolver?
    get() = nlModel?.configuration?.resourceResolver

  val tagName: String
    get() {
      val tagName = firstComponent?.tagName ?: return ""
      for (component in components) {
        if (component.tagName != tagName) {
          return ""
        }
      }
      return tagName
    }

  protected open val firstComponent: NlComponent?
    get() = components.firstOrNull()

  val project: Project
    get() = model.facet.module.project

  protected val firstTag: XmlTag?
    get() = firstComponent?.tag

  private val nlModel: NlModel?
    get() = firstComponent?.model

  private val defaultNamespace: ResourceNamespace
    get() = ReadAction.compute<ResourceNamespace, RuntimeException> { ResourceRepositoryManager.getInstance(model.facet).namespace }

  private fun isReferenceValue(value: String?): Boolean {
    return value != null && (value.startsWith("?") || value.startsWith("@") && !isId(value))
  }

  private fun isId(value: String): Boolean {
    val url = ResourceUrl.parse(value)
    return url?.type == ResourceType.ID
  }

  private fun computeTooltipForName(): String {
    val sb = StringBuilder(100)
    sb.append(findNamespacePrefix())
    sb.append(name)
    val value = definition?.getDescription(null) ?: ""
    if (value.isNotEmpty()) {
      sb.append(":\n")
      sb.append(filterRawAttributeComment(value))
    }
    return sb.toString()
  }

  private fun computeTooltipForValue(): String {
    val currentValue = rawValue
    val defaultValue = defaultValue
    val resolvedValue = resolvedValue
    val actualValue = resolvedValue ?: defaultValue
    if (currentValue == actualValue) return ""
    val defaultText = if (currentValue == null) "[default] " else ""
    val keyStroke = KeymapUtil.getShortcutText(ToggleShowResolvedValueAction.SHORTCUT)
    val resolvedText = if (resolvedValue != currentValue) " = \"$resolvedValue\" ($keyStroke)" else ""
    return "$defaultText\"${currentValue?:defaultValue}\"$resolvedText"
  }

  private fun findNamespacePrefix(): String {
    val resolver = namespaceResolver
    // TODO: This should not be required, but it is for as long as getNamespaceResolver returns TOOLS_ONLY:
    if (resolver == ResourceNamespace.Resolver.TOOLS_ONLY && namespace == ANDROID_URI) return PREFIX_ANDROID
    val prefix = namespaceResolver.uriToPrefix(namespace) ?: return ""
    @Suppress("ConvertToStringTemplate")
    return prefix + ":"
  }

  protected open fun getCompletionValues(): List<String> {
    if (namespace == TOOLS_URI && name == ATTR_PARENT_TAG) {
      // Exception:
      val tags = ReadAction.compute<Collection<String>, RuntimeException> {
        AndroidDomUtil.removeUnambiguousNames(AttributeProcessingUtil.getViewGroupClassMap(model.facet))
      }.toMutableList()
      tags.sort()
      return tags
    }
    if (type == NelePropertyType.ID) {
      return IdEnumSupport(this).values.map { it.value }
    }
    val values = mutableListOf<String>()
    if (definition != null && definition.values.isNotEmpty()) {
      values.addAll(definition.values)
    }
    val resourceManagers = ModuleResourceManagers.getInstance(model.facet)
    val localRepository = resourceManagers.localResourceManager.resourceRepository
    val frameworkRepository = resourceManagers.frameworkResourceManager?.resourceRepository
    val types = type.resourceTypes
    val toName = { item: ResourceItem -> item.referenceToSelf.getRelativeResourceUrl(defaultNamespace, namespaceResolver).toString() }
    if (types.isNotEmpty()) {
      // Local resources.
      for (type in types) {
        // TODO(namespaces): Exclude non-public resources from library modules.
        localRepository.getResources(defaultNamespace, type).values().filter { it.libraryName == null }.mapTo(values, toName)
      }

      // AAR resources.
      localRepository.accept(object : ResourceVisitor {
        override fun visit(resourceItem: ResourceItem): ResourceVisitor.VisitResult {
          if (resourceItem is AarResourceItem && resourceItem.visibility == ResourceVisibility.PUBLIC) {
            values.add(toName(resourceItem))
          }
          return ResourceVisitor.VisitResult.CONTINUE
        }

        override fun shouldVisitResourceType(resourceType: ResourceType): Boolean {
          return types.contains(resourceType)
        }
      })

      // Framework resources.
      for (type in types) {
        frameworkRepository?.getPublicResources(ResourceNamespace.ANDROID, type)?.mapTo(values, toName)
      }
    }
    return values
  }

  protected open fun validate(text: String?): Pair<EditingErrorCategory, String> {
    val value = (text ?: rawValue).nullize() ?: return EDITOR_NO_ERROR
    return validateEditedValue(value) ?: lintValidation() ?: EDITOR_NO_ERROR
  }

  private fun validateEditedValue(text: String): Pair<EditingErrorCategory, String>? {
    return when {
      text.startsWith("@") -> validateResourceReference(text)
      text.startsWith("?") -> validateThemeReference(text)
      else -> validateExplicitValue(text)
    }
  }

  private fun validateResourceReference(text: String): Pair<EditingErrorCategory, String>? {
    if (text == NULL_RESOURCE) {
      return EDITOR_NO_ERROR
    }
    val parsed = org.jetbrains.android.dom.resources.ResourceValue.parse(text, true, true, false)!!
    val error = parsed.errorMessage
    if (error != null) {
      return Pair(EditingErrorCategory.ERROR, error)
    }
    val parsedType = parsed.type!!
    if (parsedType == ResourceType.SAMPLE_DATA) {
      // TODO: Check the syntax and type of the sample data
      return EDITOR_NO_ERROR
    }
    if (!type.resourceTypes.contains(parsedType)) {
      val expected = type.resourceTypes.joinToString { it.getName() }
      val message = when {
        type.resourceTypes.size > 1 -> "Unexpected resource type: '${parsedType.getName()}' expected one of: $expected"
        else -> "Unexpected resource type: '${parsedType.getName()}' expected: $expected"
      }
      return Pair(EditingErrorCategory.ERROR, message)
    }
    val value = asResourceValue(text)
    return if (value == null) Pair(EditingErrorCategory.ERROR, "Cannot resolve symbol: '${parsed.resourceName}'") else null
  }

  private fun validateThemeReference(text: String): Pair<EditingErrorCategory, String>? {
    val value = asResourceValue(text)
    return if (value == null) Pair(EditingErrorCategory.ERROR, "Cannot resolve theme reference: '${text.substring(1)}'") else null
  }

  private fun validateExplicitValue(text: String): Pair<EditingErrorCategory, String>? {
    if (definition?.values?.contains(text) == true) return null
    val message = type.validateLiteral(text) ?: return null
    return Pair(EditingErrorCategory.ERROR, message)
  }

  protected fun lintValidation(): Pair<EditingErrorCategory, String>? {
    val component = firstComponent ?: return null
    val issue = nlModel?.lintAnnotationsModel?.findIssue(component, namespace, name) ?: return null
    return when (issue.level) {
      HighlightDisplayLevel.ERROR -> Pair(EditingErrorCategory.ERROR, issue.message)
      else -> Pair(EditingErrorCategory.WARNING, issue.message)
    }
  }

  override val browseButton = createBrowseButton()

  override val colorButton = createColorButton()

  // region Implementation of browseButton

  private fun createBrowseButton(): ActionIconButton? {
    if (name == ATTR_ID || type.resourceTypes.isEmpty()) {
      return EmptyBrowseActionIconButton
    }
    return BrowseActionIconButton()
  }

  private inner class BrowseActionIconButton: ActionIconButton {
    override val actionButtonFocusable
      get() = true

    override fun getActionIcon(focused: Boolean): Icon {
      val reference = isReferenceValue(rawValue)
      return when {
        reference && !focused -> StudioIcons.Common.PROPERTY_BOUND
        reference && focused -> StudioIcons.Common.PROPERTY_BOUND_FOCUS
        !reference && !focused -> StudioIcons.Common.PROPERTY_UNBOUND
        else -> StudioIcons.Common.PROPERTY_UNBOUND_FOCUS
      }
    }

    override val action: AnAction?
      get() = OpenResourceManagerAction(this@NelePropertyItem)
  }

  // endregion

  // region Implementation of colorButton

  private fun createColorButton(): ActionIconButton? {
    if (!type.resourceTypes.contains(ResourceType.COLOR) &&
        !type.resourceTypes.contains(ResourceType.DRAWABLE)) {
      return null
    }
    return ColorActionIconButton()
  }

  private inner class ColorActionIconButton: ActionIconButton {
    override val actionButtonFocusable
      get() = true

    override fun getActionIcon(focused: Boolean): Icon {
      val value = rawValue ?: defaultValue
      return resolveValueAsIcon(value) ?: getActionIconFromUnfinishedValue(value)
    }

    private fun getActionIconFromUnfinishedValue(value: String?): Icon =
      if (isColor(value)) StudioIcons.LayoutEditor.Extras.PIPETTE else StudioIcons.LayoutEditor.Properties.IMAGE_PICKER

    private fun isColor(value: String?): Boolean {
      val parsed = org.jetbrains.android.dom.resources.ResourceValue.parse(value, true, true, false)
      return when {
        parsed?.type == ResourceType.COLOR -> true
        parsed?.type == ResourceType.DRAWABLE -> false
        name == ATTR_BACKGROUND -> true
        type == NelePropertyType.DRAWABLE -> false
        type.resourceTypes.contains(ResourceType.COLOR) -> true
        type.resourceTypes.contains(ResourceType.DRAWABLE) -> false
        else -> true
      }
    }

    override val action: AnAction?
      get() {
        val value = rawValue
        if (isColor(value)) {
          return ColorSelectionAction(this@NelePropertyItem, resolveValueAsColor(value))
        }
        else {
          return OpenResourceManagerAction(this@NelePropertyItem)
        }
      }

      private fun resolveValueAsColor(value: String?): Color? {
        if (value != null && !isReferenceValue(value)) {
          return parseColor(value)
        }
        val resValue = asResourceValue(value) ?: return null
        return resolver?.resolveColor(resValue, project)
      }

      private fun resolveValueAsIcon(value: String?): Icon? {
        if (value != null && !isReferenceValue(value)) {
          val color = parseColor(value) ?: return null
          return JBUI.scale(ColorIcon(RESOURCE_ICON_SIZE, color, false))
        }
        val resValue = asResourceValue(value) ?: return null
        return resolver?.resolveAsIcon(resValue, project, model.facet)
      }
    }

  // endregion

  companion object {
    private val lineEndingRegex = Regex("\n *")

    // TODO: b/121033944 Give access to links and format code sections as well.
    @VisibleForTesting
    fun filterRawAttributeComment(comment: String): String {
      return comment.replace(lineEndingRegex, " ")
    }
  }
}
