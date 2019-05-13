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
package com.android.tools.idea.lang.roomSql.resolution

import com.android.tools.idea.lang.roomSql.resolution.RoomTable.Type
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil

typealias PsiClassPointer = SmartPsiElementPointer<out PsiClass>
typealias PsiFieldPointer = SmartPsiElementPointer<out PsiField>

data class RoomDatabase(
  /** Annotated class. */
  val psiClass: PsiClassPointer,

  /** Classes mentioned in the `tables` annotation parameter. These may not actually be `@Entities` if the code is wrong.  */
  val entities: Set<PsiClassPointer>
)

data class RoomTable(
  /** Annotated class. */
  val psiClass: PsiClassPointer,

  /** [Type] of the table. */
  val type: Type,

  /** Name of the table: take from the class name or the annotation parameter. */
  override val name: String,

  /**
   * [PsiElement] that determines the table name and should be the destination of references from SQL.
   *
   * This can be either the class itself or the annotation element.
   */
  val nameElement: PsiElementPointer = psiClass,

  /** Columns present in the table representing this entity. */
  val columns: Set<SqlColumn> = emptySet()
) : SqlTable {
  override fun processColumns(processor: Processor<SqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>) = ContainerUtil.process(columns, processor)
  override val definingElement: PsiElement get() = psiClass.element!!
  override val resolveTo: PsiElement get() = nameElement.element!!
  override val isView = type == Type.VIEW

  enum class Type {
    /** Created from a class annotated with `@Entity`. */
    ENTITY,

    /** Created from a class annotated with `@DatabaseView`. */
    VIEW,
  }
}

data class RoomFieldColumn(
  /** Field that defines this column. */
  val psiField: PsiFieldPointer,

  /** Effective name of the column, either taken from the field or from `@ColumnInfo`. */
  override val name: String,

  /** The [PsiElement] that defines the column name. */
  val nameElement: PsiElementPointer = psiField,
  override val isPrimaryKey: Boolean = false,
  override val alternativeNames: Set<String> = emptySet()
) : SqlColumn {
  override val type: SqlType? get() = psiField.element?.type?.presentableText?.let(::JavaFieldSqlType)
  override val definingElement: PsiElement get() = psiField.element!!
  override val resolveTo: PsiElement get() = nameElement.element!!
}

/**
 * Represents column that equals table name in Fts table.
 */
data class RoomFtsTableColumn(
  override val definingElement: PsiElement,
  override val name: String
) : SqlColumn {
  override val type: SqlType = FtsSqlType
}

/**
 * @see https://sqlite.org/lang_createtable.html#rowid
 */
val PRIMARY_KEY_NAMES = setOf("rowid", "oid", "_rowid_")
val PRIMARY_KEY_NAMES_FOR_FTS = setOf("rowid", "oid", "_rowid_", "docid")

/**
 * Represents special SQLite column `rowid` which are not explicitly defined in Room entities
 *
 * @see https://sqlite.org/lang_createtable.html#rowid
 */
data class RoomRowidColumn(
  override val definingElement: PsiElement,
  override val alternativeNames: Set<String> = PRIMARY_KEY_NAMES
) : SqlColumn {
  override val type: SqlType = JavaFieldSqlType("int")
  override val isPrimaryKey: Boolean = true
  override val name: String? = null
}

/**
 * Represent PsiElement that is used for [RoomRowidColumn]
 *
 * There is no real field for [RoomRowidColumn] so we resolve it to [PsiElementForFakeColumn] that navigate to table column belongs to.
 */
data class PsiElementForFakeColumn(val tablePsiElement: PsiClass): PsiElement by tablePsiElement {
  override fun getNavigationElement(): PsiElement {
    return tablePsiElement
  }

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return another is PsiElementForFakeColumn && another.tablePsiElement == tablePsiElement
  }
}

data class Dao(val psiClass: PsiClassPointer)

/**
 * Schema defined using Room annotations in Java/Kotlin code.
 */
data class RoomSchema(
  val databases: Set<RoomDatabase>,
  val tables: Set<RoomTable>,
  val daos: Set<Dao>
) {
  fun findTable(psiClass: PsiClass) = tables.find { it.psiClass.element == psiClass }
}
