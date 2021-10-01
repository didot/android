// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.android.parcel.quickfixes

import org.jetbrains.kotlin.android.synthetic.diagnostic.ErrorsAndroid
import org.jetbrains.kotlin.idea.quickfix.QuickFixContributor
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix
import org.jetbrains.kotlin.lexer.KtTokens

class ParcelableQuickFixContributor : QuickFixContributor {
    override fun registerQuickFixes(quickFixes: QuickFixes) {
        quickFixes.register(ErrorsAndroid.PARCELABLE_CANT_BE_INNER_CLASS,
                            RemoveModifierFix.createRemoveModifierFromListOwnerFactory(KtTokens.INNER_KEYWORD, false))

        quickFixes.register(ErrorsAndroid.NO_PARCELABLE_SUPERTYPE, ParcelableAddSupertypeQuickfix.Factory)
        quickFixes.register(ErrorsAndroid.PARCELABLE_SHOULD_HAVE_PRIMARY_CONSTRUCTOR, ParcelableAddPrimaryConstructorQuickfix.Factory)
        quickFixes.register(ErrorsAndroid.PROPERTY_WONT_BE_SERIALIZED, ParcelableAddIgnoreOnParcelAnnotationQuickfix.Factory)

        quickFixes.register(ErrorsAndroid.OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED, ParcelMigrateToParcelizeQuickFix.FactoryForWrite)
        quickFixes.register(ErrorsAndroid.OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED, ParcelRemoveCustomWriteToParcel.Factory)

        quickFixes.register(ErrorsAndroid.CREATOR_DEFINITION_IS_NOT_ALLOWED, ParcelMigrateToParcelizeQuickFix.FactoryForCREATOR)
        quickFixes.register(ErrorsAndroid.CREATOR_DEFINITION_IS_NOT_ALLOWED, ParcelRemoveCustomCreatorProperty.Factory)

        quickFixes.register(ErrorsAndroid.REDUNDANT_TYPE_PARCELER, ParcelableRemoveDuplicatingTypeParcelerAnnotationQuickFix.Factory)
        quickFixes.register(ErrorsAndroid.CLASS_SHOULD_BE_PARCELIZE, AnnotateWithParcelizeQuickFix.Factory)
    }
}