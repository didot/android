// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.android.synthetic.idea

import org.jetbrains.kotlin.android.synthetic.AndroidConst
import org.jetbrains.kotlin.android.synthetic.descriptors.PredefinedPackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.core.extension.KotlinIndicesHelperExtension
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class AndroidIndicesHelperExtension : KotlinIndicesHelperExtension {
    override fun appendExtensionCallables(
            consumer: MutableList<in CallableDescriptor>,
            moduleDescriptor: ModuleDescriptor,
            receiverTypes: Collection<KotlinType>,
            nameFilter: (String) -> Boolean,
            lookupLocation: LookupLocation,
    ) {
        for (packageFragment in moduleDescriptor.getPackage(FqName(AndroidConst.SYNTHETIC_PACKAGE)).fragments) {
            if (packageFragment !is PredefinedPackageFragmentDescriptor) continue

            fun handleScope(scope: MemberScope) {
                val descriptors = scope.getContributedDescriptors(DescriptorKindFilter.CALLABLES) { nameFilter(it.asString()) }
                for (descriptor in descriptors) {
                    val receiverType = (descriptor as CallableDescriptor).extensionReceiverParameter?.type ?: continue
                    if (receiverTypes.any { it.isSubtypeOf(receiverType) }) {
                        consumer += descriptor
                    }
                }
            }

            handleScope(packageFragment.getMemberScope())
            for (fragment in packageFragment.lazySubpackages) {
                if (fragment.isDeprecated) continue
                handleScope(fragment.descriptor().getMemberScope())
            }
        }
    }
}