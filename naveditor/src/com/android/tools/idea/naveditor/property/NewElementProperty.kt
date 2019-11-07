package com.android.tools.idea.naveditor.property

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.uibuilder.model.createChild
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeDefinitions

open class NewElementProperty(private val parent: NlComponent, private val tagName: String, private val attrName: String,
                              private val namespace: String?, private val attrDefs: AttributeDefinitions,
                              private val propertiesManager: NavPropertiesManager) : NlProperty {

  private var delegate: NlProperty? = null

  override fun getName(): String = delegate?.name ?: ""

  override fun getNamespace(): String? = delegate?.namespace

  override fun getValue(): String? = delegate?.value

  override fun getResolvedValue(): String? = delegate?.resolvedValue

  override fun isDefaultValue(value: String?): Boolean = delegate?.isDefaultValue(value) != false

  override fun resolveValue(value: String?): String? = delegate?.resolveValue(value)

  override fun setValue(value: Any?) {
    delegate?.let {
      it.setValue(value)
      return
    }
    if (value is String? && value.isNullOrEmpty()) {
      return
    }
    val newComponent = WriteCommandAction.runWriteCommandAction(parent.model.project, Computable<NlComponent> {
      val result = parent.createChild(tagName, false) ?: error ("Failed to create <$tagName>!")
      result.setAttribute(namespace, attrName, value as String)
      result
    })
    delegate = NlPropertyItem.create(XmlName(attrName, namespace), definition, listOf(newComponent), propertiesManager)
  }

  override fun getTooltipText(): String = delegate?.tooltipText ?: ""

  override fun getDefinition(): AttributeDefinition? = attrDefs.getAttrDefinition(
    ResourceReference.attr(namespace?.let { ResourceNamespace.fromNamespaceUri(namespace)!! } ?: ResourceNamespace.RES_AUTO, attrName))

  override fun getComponents(): List<NlComponent> = delegate?.components ?: listOf()

  override fun getResolver(): ResourceResolver? = model.configuration.resourceResolver

  override fun getModel(): NlModel = parent.model

  override fun getTag(): XmlTag? = delegate?.tag

  override fun getTagName(): String? = delegate?.tagName

  override fun getChildProperty(name: String): NlProperty = throw UnsupportedOperationException(attrName)

  override fun getDesignTimeProperty(): NlProperty = throw UnsupportedOperationException(attrName)

}