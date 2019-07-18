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
package com.android.tools.idea.lang.androidSql

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

fun createStubRoomClasses(codeInsightTestFixture: JavaCodeInsightTestFixture) {
  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Dao {}
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Database { Class[] tables(); int version(); }
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Entity { String tableName() default ""; }
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Query { String value(); }
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface DatabaseView { String value() default ""; String viewName() default ""  }
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Ignore {}
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface ColumnInfo { String name() default ""; }
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Embedded { String prefix() default ""; }
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Fts3 {}
        """.trimIndent()
  )

  codeInsightTestFixture.addClass(
    """
        package androidx.room;
  
        public @interface Fts4 {}
        """.trimIndent()
  )
}

data class FieldDefinition(val name: String, val type: String, val columnName: String? = null)

infix fun String.ofType(type: String): FieldDefinition = FieldDefinition(this, type)

fun JavaCodeInsightTestFixture.classPointer(qualifiedClassName: String): SmartPsiElementPointer<PsiClass> {
  return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findClass(qualifiedClassName))
}

fun JavaCodeInsightTestFixture.findField(
  qualifiedClassName: String,
  fieldName: String,
  checkBases: Boolean = false
): PsiField {
  return findClass(qualifiedClassName).findFieldByName(fieldName, checkBases)!!
}

fun JavaCodeInsightTestFixture.fieldPointer(
  qualifiedClassName: String,
  fieldName: String,
  checkBases: Boolean = false
): SmartPsiElementPointer<PsiField> {
  return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findField(qualifiedClassName, fieldName, checkBases))
}

fun JavaCodeInsightTestFixture.addRoomEntity(
  qualifiedClassName: String,
  vararg fields: FieldDefinition
): PsiClass {
  return addRoomEntity(qualifiedClassName, tableNameOverride = null, fields = *fields)
}

val JavaCodeInsightTestFixture.referenceAtCaret get() = file.findReferenceAt(caretOffset)!!

fun JavaCodeInsightTestFixture.addRoomEntity(
  qualifiedClassName: String,
  tableNameOverride: String?,
  vararg fields: FieldDefinition
): PsiClass {
  val packageName = qualifiedClassName.substringBeforeLast('.', "")
  val className = qualifiedClassName.substringAfterLast('.')
  val packageLine = if (packageName.isEmpty()) "" else "package $packageName;"
  val annotationArguments = if (tableNameOverride == null) "" else "(tableName = \"$tableNameOverride\")"

  val fieldsSnippet = fields.joinToString(prefix = "\n", postfix = "\n", separator = "\n") { (name, type, columnName) ->
    val annotation = if (columnName == null) "" else "@ColumnInfo(name = \"$columnName\")"
    "$annotation $type $name;"
  }

  return addClass(
    """
      $packageLine

      import androidx.room.Entity;
      import androidx.room.ColumnInfo;

      @Entity$annotationArguments
      public class $className { $fieldsSnippet }
      """.trimIndent()
  )
}

abstract class RoomLightTestCase : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    createStubRoomClasses(myFixture)
  }
}
