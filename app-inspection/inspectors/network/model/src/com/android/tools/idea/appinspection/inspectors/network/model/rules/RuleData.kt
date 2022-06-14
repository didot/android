/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.android.tools.idea.protobuf.ByteString
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.TestOnly
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import studio.network.inspection.NetworkInspectorProtocol.InterceptRule
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type
import studio.network.inspection.NetworkInspectorProtocol.Transformation
import javax.swing.JTable
import kotlin.reflect.KProperty

class RuleData(
  val id: Int,
  name: String,
  isActive: Boolean,
  val ruleDataListener: RuleDataListener = RuleDataAdapter()
) {
  companion object {
    private var count = 0

    fun newId(): Int {
      count += 1
      return count
    }

    @TestOnly
    fun getLatestId() = count
  }

  /**
   * An inner Delegate class to help define variables that need to notify
   * [ruleDataListener] with value changes.
   */
  inner class Delegate<T>(private var value: T, private val onSet: (RuleData) -> Unit) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): T {
      return value
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
      if (this.value != value) {
        this.value = value
        onSet(this@RuleData)
      }
    }
  }

  inner class CriteriaData(
    protocol: Protocol = Protocol.HTTPS,
    host: String = "",
    port: String = "",
    path: String = "",
    query: String = "",
    method: Method = Method.GET
  ) {
    var protocol: Protocol by Delegate(protocol, ruleDataListener::onRuleDataChanged)
    var host: String by Delegate(host, ruleDataListener::onRuleDataChanged)
    var port: String by Delegate(port, ruleDataListener::onRuleDataChanged)
    var path: String by Delegate(path, ruleDataListener::onRuleDataChanged)
    var query: String by Delegate(query, ruleDataListener::onRuleDataChanged)
    var method: Method by Delegate(method, ruleDataListener::onRuleDataChanged)

    val url: String
      get() = "$protocol://${host.ifBlank { "*" }}${port.withPrefixIfNotEmpty(':')}$path${query.withPrefixIfNotEmpty('?')}"

    fun toProto(): InterceptCriteria = InterceptCriteria.newBuilder().apply {
      protocol = this@CriteriaData.protocol.toProto()
      host = this@CriteriaData.host
      port = this@CriteriaData.port
      path = this@CriteriaData.path
      query = this@CriteriaData.query
      method = this@CriteriaData.method.toProto()
    }.build()

    private fun String.withPrefixIfNotEmpty(prefix: Char) = if (isBlank()) "" else prefix + this
  }

  interface TransformationRuleData {
    fun toProto(): Transformation
  }

  inner class StatusCodeRuleData(findCode: String, isActive: Boolean, newCode: String) : TransformationRuleData {
    var findCode: String by Delegate(findCode, ruleDataListener::onRuleDataChanged)
    var isActive: Boolean by Delegate(isActive, ruleDataListener::onRuleDataChanged)
    var newCode: String by Delegate(newCode, ruleDataListener::onRuleDataChanged)

    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      statusCodeReplacedBuilder.apply {
        targetCodeBuilder.apply {
          type = Type.PLAIN
          text = findCode
        }
        newCode = this@StatusCodeRuleData.newCode
      }
    }.build()
  }

  class HeaderAddedRuleData(val name: String, val value: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      headerAddedBuilder.apply {
        name = this@HeaderAddedRuleData.name
        value = this@HeaderAddedRuleData.value
      }
    }.build()
  }

  class HeaderReplacedRuleData(
    val findName: String?,
    val isFindNameRegex: Boolean,
    val findValue: String?,
    val isFindValueRegex: Boolean,
    val newName: String?,
    val newValue: String?) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      headerReplacedBuilder.apply {
        if (findName != null) {
          targetNameBuilder.apply {
            text = findName
            type = matchingTextTypeFrom(isFindNameRegex)
          }
        }
        if (findValue != null) {
          targetValueBuilder.apply {
            text = findValue
            type = matchingTextTypeFrom(isFindValueRegex)
          }
        }
        if (this@HeaderReplacedRuleData.newName != null) {
          newName = this@HeaderReplacedRuleData.newName
        }
        if (this@HeaderReplacedRuleData.newValue != null) {
          newValue = this@HeaderReplacedRuleData.newValue
        }
      }
    }.build()
  }

  inner class HeaderRulesTableModel : ListTableModel<TransformationRuleData>() {
    init {
      columnInfos = arrayOf(
        object : ColumnInfo<TransformationRuleData, String>("Type") {
          override fun getWidth(table: JTable) = JBUIScale.scale(40)

          override fun getRenderer(item: TransformationRuleData) = MyRenderer

          override fun valueOf(item: TransformationRuleData) = when (item) {
            is HeaderAddedRuleData -> "Add"
            is HeaderReplacedRuleData -> "Edit"
            else -> ""
          }
        },
        object : ColumnInfo<TransformationRuleData, Pair<String?, String?>>("Name") {
          override fun getRenderer(item: TransformationRuleData) = MyRenderer

          override fun valueOf(item: TransformationRuleData): Pair<String?, String?> {
            return when (item) {
              is HeaderAddedRuleData -> item.name to null
              is HeaderReplacedRuleData -> item.findName to item.newName
              else -> throw UnsupportedOperationException("Unknown item $item")
            }
          }
        },
        object : ColumnInfo<TransformationRuleData, Pair<String?, String?>>("Value") {
          override fun getRenderer(item: TransformationRuleData) = MyRenderer

          override fun valueOf(item: TransformationRuleData): Pair<String?, String?> {
            return when (item) {
              is HeaderAddedRuleData -> item.value to null
              is HeaderReplacedRuleData -> item.findValue to item.newValue
              else -> throw UnsupportedOperationException("Unknown item $item")
            }
          }
        })
      addTableModelListener {
        ruleDataListener.onRuleDataChanged(this@RuleData)
      }
    }
  }

  class BodyReplacedRuleData(val body: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      bodyReplacedBuilder.apply {
        body = ByteString.copyFrom(this@BodyReplacedRuleData.body.toByteArray())
      }
    }.build()
  }

  class BodyModifiedRuleData(val targetText: String, val isRegex: Boolean, val newText: String) : TransformationRuleData {
    override fun toProto(): Transformation = Transformation.newBuilder().apply {
      bodyModifiedBuilder.apply {
        targetTextBuilder.apply {
          text = this@BodyModifiedRuleData.targetText
          type = matchingTextTypeFrom(isRegex)
        }
        newText = this@BodyModifiedRuleData.newText
      }
    }.build()
  }

  inner class BodyRulesTableModel : ListTableModel<TransformationRuleData>() {
    init {
      columnInfos = arrayOf(
        object : ColumnInfo<TransformationRuleData, String>("Type") {
          override fun getWidth(table: JTable) = JBUIScale.scale(45)

          override fun getRenderer(item: TransformationRuleData) = MyRenderer

          override fun valueOf(item: TransformationRuleData) = when (item) {
            is BodyReplacedRuleData -> "Replace"
            is BodyModifiedRuleData -> "Edit"
            else -> ""
          }
        },
        object : ColumnInfo<TransformationRuleData, String>("Find") {
          override fun valueOf(item: TransformationRuleData): String {
            return when (item) {
              is BodyReplacedRuleData -> ""
              is BodyModifiedRuleData -> item.targetText
              else -> ""
            }
          }
        },
        object : ColumnInfo<TransformationRuleData, String>("Replace with") {
          override fun valueOf(item: TransformationRuleData): String {
            return when (item) {
              is BodyReplacedRuleData -> item.body
              is BodyModifiedRuleData -> item.newText
              else -> ""
            }
          }
        })
      addTableModelListener {
        ruleDataListener.onRuleDataChanged(this@RuleData)
      }
    }
  }

  var name: String by Delegate(name, ruleDataListener::onRuleNameChanged)
  var isActive: Boolean by Delegate(isActive, ruleDataListener::onRuleIsActiveChanged)

  val criteria = CriteriaData()
  val statusCodeRuleData = StatusCodeRuleData("", false, "")
  val headerRuleTableModel = HeaderRulesTableModel()
  val bodyRuleTableModel = BodyRulesTableModel()

  fun toProto(): InterceptRule = InterceptRule.newBuilder().apply {
    enabled = isActive
    criteria = this@RuleData.criteria.toProto()
    if (statusCodeRuleData.isActive) {
      addTransformation(statusCodeRuleData.toProto())
    }
    addAllTransformation(headerRuleTableModel.items.map { it.toProto() })
    addAllTransformation(bodyRuleTableModel.items.map { it.toProto() })
  }.build()
}

fun matchingTextTypeFrom(isRegex: Boolean): Type = if (isRegex) Type.REGEX else Type.PLAIN

private object MyRenderer : ColoredTableCellRenderer() {
  override fun customizeCellRenderer(table: JTable, item: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    clear()
    border = JBUI.Borders.empty()
    when (item) {
      is Pair<*, *> -> {
        if (item.first == null && item.second == null) {
          append("Unchanged", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        else {
          (item.first as? String)?.let { append(it) } ?: append("Any", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          (item.second as? String)?.let {
            append("  ➔  ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
            append(it)
          }
        }
      }
      is String -> append(item)
      else -> Unit
    }
  }
}