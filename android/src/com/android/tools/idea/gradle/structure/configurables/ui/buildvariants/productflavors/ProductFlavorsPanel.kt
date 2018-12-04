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
package com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors.FlavorDimensionConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors.ProductFlavorConfigurable
import com.android.tools.idea.gradle.structure.configurables.findChildFor
import com.android.tools.idea.gradle.structure.configurables.getModel
import com.android.tools.idea.gradle.structure.configurables.ui.ConfigurablesMasterDetailsPanel
import com.android.tools.idea.gradle.structure.configurables.ui.NameValidator
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsFlavorDimension
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.util.IconUtil
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.tree.TreePath

const val PRODUCT_FLAVORS_DISPLAY_NAME: String = "Flavors"
const val PRODUCT_FLAVORS_PLACE_NAME: String = "android.psd.product_flavor"
class ProductFlavorsPanel(
  val module: PsAndroidModule,
  val treeModel: ConfigurablesTreeModel,
  uiSettings: PsUISettings
) : ConfigurablesMasterDetailsPanel<PsProductFlavor>(
  PRODUCT_FLAVORS_DISPLAY_NAME,
  PRODUCT_FLAVORS_PLACE_NAME,
  treeModel, uiSettings
) {
  private val flavorDimensionNameValidator = NameValidator { module.validateFlavorDimensionName(it.orEmpty()) }
  private val productFlavorNameValidator = NameValidator { module.validateProductFlavorName(it.orEmpty()) }

  override fun getRemoveAction(): AnAction? {
    return object : DumbAwareAction(removeTextFor(null), removeDescriptionFor(null), IconUtil.getRemoveIcon()) {
      override fun update(e: AnActionEvent) {
        e.presentation.apply {
          isEnabled = selectedConfigurable != null && selectedConfigurable?.editableObject?.safeAs<PsFlavorDimension>()?.isInvalid != true
          text = removeTextFor(selectedConfigurable)
          description = removeDescriptionFor(selectedConfigurable)
        }
      }

      override fun actionPerformed(e: AnActionEvent) {
        when (selectedConfigurable) {
          is FlavorDimensionConfigurable -> {
            if (Messages.showYesNoDialog(
                e.project,
                "Remove flavor dimension '${selectedConfigurable?.displayName}' from the module?",
                "Remove Flavor Dimension",
                Messages.getQuestionIcon()
              ) == Messages.YES) {
              val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling
              module.removeFlavorDimension(selectedNode.getModel() ?: return)
              selectNode(nodeToSelectAfter)
            }
          }
          is ProductFlavorConfigurable -> {
            if (Messages.showYesNoDialog(
                e.project,
                "Remove product flavor '${selectedConfigurable?.displayName}' from the module?",
                "Remove Product Flavor",
                Messages.getQuestionIcon()
              ) == Messages.YES) {
              val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling ?: selectedNode.parent
              module.removeProductFlavor(selectedNode.getModel() ?: return)
              selectNode(nodeToSelectAfter)
            }
          }
        }
      }
    }
  }

  override fun getCreateActions(): List<AnAction> {
    return listOf(
        object : DumbAwareAction("Add Dimension", "", IconUtil.getAddIcon()) {
          override fun actionPerformed(e: AnActionEvent) {
            val newName =
                Messages.showInputDialog(
                  e.project,
                  "Enter a new flavor dimension name:",
                  "Create New Flavor Dimension",
                  null,
                  "",
                  flavorDimensionNameValidator)
            if (newName != null) {
              val flavorDimension = module.addNewFlavorDimension(newName)
              val node = treeModel.rootNode.findChildFor(flavorDimension)
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        },
        object : DumbAwareAction("Add Product Flavor", "", IconUtil.getAddIcon()) {
          override fun actionPerformed(e: AnActionEvent) {
            val selectedObject = selectedConfigurable
            val currentDimension = when (selectedObject) {
              is FlavorDimensionConfigurable -> selectedObject.flavorDimension.name
              is ProductFlavorConfigurable -> selectedObject.model.effectiveDimension
              else -> {
                Messages.showErrorDialog(
                  "All product flavors must belong to a named flavor dimension. Add a flavor dimension first.",
                  "Add Product Flavor")
                return
              }
            }
            val newName =
              Messages.showInputDialog(
                e.project,
                "Enter a new product flavor name:",
                "Create New Product Flavor",
                null,
                "",
                NameValidator { module.validateProductFlavorName(it.orEmpty()) }
              )
            if (newName != null) {
              val productFlavor = module.addNewProductFlavor(currentDimension.orEmpty(), newName)
              val dimension = module.findFlavorDimension(currentDimension.orEmpty())
              val node =
                dimension?.let { treeModel.rootNode.findChildFor(dimension) } ?: treeModel.rootNode
                  .findChildFor(productFlavor)
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        }
    )
  }

  override fun PsUISettings.getLastEditedItem(): String? = LAST_EDITED_FLAVOR_OR_DIMENSION

  override fun PsUISettings.setLastEditedItem(value: String?) {
    LAST_EDITED_FLAVOR_OR_DIMENSION = value
  }
}

private fun removeTextFor(configurable: NamedConfigurable<*>?) = when (configurable) {
  is FlavorDimensionConfigurable -> "Remove Flavor Dimension"
  is ProductFlavorConfigurable -> "Remove Product Flavor"
  else -> "Remove"
}

private fun removeDescriptionFor(configurable: NamedConfigurable<*>?) = when (configurable) {
  is FlavorDimensionConfigurable -> "Removes a flavor dimension"
  is ProductFlavorConfigurable -> "Removes a product flavor"
  else -> "Removes a product flavor or flavor dimension"
}

