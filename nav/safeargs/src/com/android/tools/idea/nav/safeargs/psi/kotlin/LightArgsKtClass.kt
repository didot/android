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
package com.android.tools.idea.nav.safeargs.psi.kotlin

import com.android.tools.idea.nav.safeargs.index.NavFragmentData
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
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
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.Printer

/**
 * Kt class descriptors for Args classes generated from navigation xml files.
 *
 * An "Arg" represents an argument which can get passed from one destination to another.
 *
 * For example, if you had the following "nav.xml":
 *
 * ```
 * <argument
 *    android:name="message"
 *    app:argType="string" />
 * ```
 *
 * This would generate a class like the following:
 *
 * ```
 *  data class FirstFragmentArgs( val message: String) : NavArgs {
 *       fun toBundle(): Bundle
 *
 *       companion object {
 *          fun fromBundle(bundle: Bundle): FirstFragmentArgs
 *       }
 *
 * ```
 */
class LightArgsKtClass(
  name: Name,
  private val fragment: NavFragmentData,
  superTypes: Collection<KotlinType>,
  sourceElement: SourceElement,
  containingDescriptor: DeclarationDescriptor,
  private val storageManager: StorageManager
) : ClassDescriptorImpl(containingDescriptor, name, Modality.FINAL, ClassKind.CLASS, superTypes, sourceElement, false, storageManager) {

  private val _primaryConstructor = storageManager.createLazyValue { computePrimaryConstructor() }
  private val _companionObject = storageManager.createLazyValue { computeCompanionObject() }
  private val scope = storageManager.createLazyValue { ArgsClassScope() }

  override fun getUnsubstitutedMemberScope(): MemberScope = scope()
  override fun getConstructors() = listOf(_primaryConstructor())
  override fun getUnsubstitutedPrimaryConstructor() = _primaryConstructor()
  override fun getCompanionObjectDescriptor() = _companionObject()

  private fun computePrimaryConstructor(): ClassConstructorDescriptor {
    val valueParametersProvider = { constructor: ClassConstructorDescriptor ->
      var index = 0
      fragment.arguments
        .asSequence()
        .map { arg ->
          val pName = Name.identifier(arg.name)
          val isNonNull = arg.nullable != "true"
          val fallbackType = this.builtIns.stringType
          val pType = this.builtIns.getKotlinType(arg.type, arg.defaultValue, containingDeclaration.module, isNonNull, fallbackType)
          val hasDefaultValue = arg.defaultValue != null
          ValueParameterDescriptorImpl(constructor, null, index++, Annotations.EMPTY, pName, pType, hasDefaultValue,
                                       false, false, null, SourceElement.NO_SOURCE)
        }
        .toList()
    }
    return this.createConstructor(valueParametersProvider)
  }

  private fun computeCompanionObject(): ClassDescriptor {
    val argsClassDescriptor = this@LightArgsKtClass
    return object : ClassDescriptorImpl(argsClassDescriptor, argsClassDescriptor.name, Modality.FINAL,
                                        ClassKind.OBJECT, emptyList(), argsClassDescriptor.source, false, storageManager) {

      private val companionObjectScope = storageManager.createLazyValue { CompanionScope() }
      private val companionObject = this
      override fun isCompanionObject() = true
      override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = null
      override fun getConstructors(): Collection<ClassConstructorDescriptor> = emptyList()
      override fun getUnsubstitutedMemberScope() = companionObjectScope()

      private inner class CompanionScope : MemberScopeImpl() {
        private val companionMethods = storageManager.createLazyValue {
          val valueParametersProvider = { method: SimpleFunctionDescriptorImpl ->
            val bundleType = argsClassDescriptor.builtIns.getKotlinType("android.os.Bundle", null, argsClassDescriptor.module)
            val valueParameter = ValueParameterDescriptorImpl(
              method, null, 0, Annotations.EMPTY, Name.identifier("bundle"), bundleType,
              false, false, false, null, SourceElement.NO_SOURCE
            )
            listOf(valueParameter)
          }

          val fromBundle = argsClassDescriptor.createMethod(
            name = "fromBundle",
            returnType = argsClassDescriptor.getDefaultType(),
            dispatchReceiver = companionObject,
            valueParametersProvider = valueParametersProvider
          )

          listOf(fromBundle)
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

  private inner class ArgsClassScope : MemberScopeImpl() {
    private val argsClassDescriptor = this@LightArgsKtClass
    private val methods = storageManager.createLazyValue {
      val bundleType = argsClassDescriptor.builtIns.getKotlinType("android.os.Bundle", null, argsClassDescriptor.module)
      val toBundle = argsClassDescriptor.createMethod(
        name = "toBundle",
        returnType = bundleType,
        dispatchReceiver = argsClassDescriptor
      )

      val copy = argsClassDescriptor.createMethod(
        name = "copy",
        returnType = argsClassDescriptor.getDefaultType(),
        dispatchReceiver = argsClassDescriptor,
        valueParametersProvider = { argsClassDescriptor.unsubstitutedPrimaryConstructor.valueParameters }
      )

      // TODO(b/157920723): Destructuring declaration doesn't work
      var index = 1
      val componentFunctions = argsClassDescriptor.unsubstitutedPrimaryConstructor.valueParameters
        .asSequence()
        .map { parameter ->
          argsClassDescriptor.createMethod(
            name = "component" + index++,
            returnType = parameter.type,
            dispatchReceiver = argsClassDescriptor
          )
        }
        .toList()

      listOf(toBundle, copy) + componentFunctions
    }

    private val properties = storageManager.createLazyValue {
      fragment.arguments
        .asSequence()
        .map { arg ->
          val pName = arg.name
          val isNonNull = arg.nullable != "true"
          val fallbackType = argsClassDescriptor.builtIns.stringType
          val pType = argsClassDescriptor.builtIns
            .getKotlinType(arg.type, arg.defaultValue, argsClassDescriptor.module, isNonNull, fallbackType)
          argsClassDescriptor.createProperty(pName, pType, argsClassDescriptor.source)
        }
        .toList()
    }

    override fun getContributedDescriptors(kindFilter: DescriptorKindFilter,
                                           nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
      return methods().filter { kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name) } +
             properties().filter { kindFilter.acceptsKinds(DescriptorKindFilter.VARIABLES_MASK) && nameFilter(it.name) }
    }

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
      return methods().filter { it.name == name }
    }

    override fun getContributedVariables(name: Name, location: LookupLocation): List<PropertyDescriptor> {
      return properties().filter { it.name == name }
    }

    override fun printScopeStructure(p: Printer) {
      p.println(this::class.java.simpleName)
    }
  }
}