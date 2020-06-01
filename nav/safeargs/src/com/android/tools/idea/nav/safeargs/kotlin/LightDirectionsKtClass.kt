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
package com.android.tools.idea.nav.safeargs.kotlin

import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.psi.toCamelCase
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.utils.Printer

/**
 * Kt class descriptors for Directions classes generated from navigation xml files.
 *
 * A "Direction" represents functionality that takes you away from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 *  <navigation>
 *    <fragment id="@+id/mainMenu">
 *      <action id="@+id/actionToOptions" />
 *      <destination="@id/options" />
 *    </fragment>
 *    <fragment id="@+id/options">
 *  </navigation>
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  class MainMenuDirections {
 *    companion object {
 *        fun actionMainMenuToOptions(): NavDirections
 *    }
 *  }
 * ```
 */
class LightDirectionsKtClass(
  name: Name,
  private val destination: NavDestinationData,
  private val navResourceData: NavXmlData,
  sourceElement: SourceElement,
  containingDescriptor: DeclarationDescriptor,
  private val storageManager: StorageManager
) : ClassDescriptorImpl(containingDescriptor, name, Modality.FINAL, ClassKind.CLASS, emptyList(), sourceElement, false, storageManager) {

  private val _primaryConstructor = storageManager.createLazyValue { computePrimaryConstructor() }
  private val _companionObject = storageManager.createLazyValue { computeCompanionObject() }

  override fun getUnsubstitutedMemberScope(): MemberScope = MemberScope.Empty
  override fun getConstructors() = listOf(_primaryConstructor())
  override fun getUnsubstitutedPrimaryConstructor() = _primaryConstructor()
  override fun getCompanionObjectDescriptor() = _companionObject()

  private fun computePrimaryConstructor() = this.createConstructor()

  private fun computeCompanionObject(): ClassDescriptor {
    val directionsClassDescriptor = this@LightDirectionsKtClass
    return object : ClassDescriptorImpl(directionsClassDescriptor, directionsClassDescriptor.name, Modality.FINAL,
                                        ClassKind.OBJECT, emptyList(), directionsClassDescriptor.source, false, storageManager) {

      private val companionScope = storageManager.createLazyValue { CompanionObjectScope() }
      private val companionObject = this
      override fun isCompanionObject() = true
      override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = null
      override fun getConstructors(): Collection<ClassConstructorDescriptor> = emptyList()
      override fun getUnsubstitutedMemberScope() = companionScope()

      private inner class CompanionObjectScope : MemberScopeImpl() {
        private val companionMethods = storageManager.createLazyValue {
          // action methods
          val navDirectionType = directionsClassDescriptor.builtIns.getKotlinType("androidx.navigation.NavDirections", null,
                                                                                  directionsClassDescriptor.module)

          destination.actions
            .asSequence()
            .mapNotNull { action ->
              val targetDestination = navResourceData.root.allDestinations.firstOrNull { it.id == action.destination }
                                      ?: return@mapNotNull null

              val valueParametersProvider = { method: SimpleFunctionDescriptorImpl ->
                var index = 0
                targetDestination.arguments
                  .asSequence()
                  .map { arg ->
                    val pName = Name.identifier(arg.name)
                    val isNonNull = arg.nullable != "true"
                    val fallbackType = directionsClassDescriptor.builtIns.stringType
                    val pType = directionsClassDescriptor.builtIns
                      .getKotlinType(arg.type, arg.defaultValue, directionsClassDescriptor.module, isNonNull, fallbackType)
                    val hasDefaultValue = arg.defaultValue != null
                    ValueParameterDescriptorImpl(method, null, index++, Annotations.EMPTY, pName, pType,
                                                 hasDefaultValue, false, false, null,
                                                 SourceElement.NO_SOURCE)
                  }
                  .toList()
              }

              directionsClassDescriptor.createMethod(
                name = action.id.toCamelCase(),
                returnType = navDirectionType,
                dispatchReceiver = companionObject,
                valueParametersProvider = valueParametersProvider
              )
            }
            .toList()
        }

        override fun getContributedDescriptors(kindFilter: DescriptorKindFilter,
                                               nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
          return companionMethods().filter { kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name) }
        }

        override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
          return companionMethods().filter { it.name == name }
        }

        override fun printScopeStructure(p: Printer) {
          p.println(this::class.java.simpleName)
        }
      }
    }
  }
}