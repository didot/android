/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.model

import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.intellij.openapi.vfs.VirtualFile
import java.sql.JDBCType

/**
 * Representation of a database instance.
 */
sealed class SqliteDatabase {
  /**
   * Human readable name of the database.
   */
  abstract val name: String

  /**
   * A connection to the database.
   */
  abstract val databaseConnection: DatabaseConnection
}

/**
 * [SqliteDatabase] accessed through live connection.
 */
data class LiveSqliteDatabase(override val name: String, override val databaseConnection: DatabaseConnection) : SqliteDatabase()

/**
 * File based-[SqliteDatabase]. This database is accessed through a [VirtualFile].
 * The [DatabaseConnection] gets closed when the file is deleted.
 */
data class FileSqliteDatabase(
  override val name: String,
  override val databaseConnection: DatabaseConnection,
  val virtualFile: VirtualFile
) : SqliteDatabase()

/** Representation of the Sqlite database schema */
data class SqliteSchema(val tables: List<SqliteTable>)

/** Representation of the Sqlite database table
 *
 * @see [https://www.sqlite.org/lang_createview.html] for isView
 **/
data class SqliteTable(val name: String, val columns: List<SqliteColumn>, val rowIdName: RowIdName?, val isView: Boolean)

/** Representation of the Sqlite table row */
data class SqliteRow(val values: List<SqliteColumnValue>)

/** Representation of a Sqlite table column value */
data class SqliteColumnValue(val columnName: String, val value: SqliteValue)

/** Representation of a Sqlite table column */
data class SqliteColumn(val name: String, val affinity: SqliteAffinity, val isNullable: Boolean, val inPrimaryKey: Boolean)

/**
 *  A column obtained from a result set.
 *  We cannot use [SqliteColumn] because the on-device database inspector is not capable of providing the optional properties of this class,
 *  while JDBC is.
 */
data class ResultSetSqliteColumn(
  val name: String,
  val affinity: SqliteAffinity? = null,
  val isNullable: Boolean? = null,
  val inPrimaryKey: Boolean? = null
)

/**
 * Representation of a SQLite statement that may contain positional parameters.
 *
 * @param sqliteStatementText The text of the SQLite statement.
 * It can be a complete statement (eg: SELECT * FROM tab WHERE id = 1), or it can be
 * a statement with positional templates (eg: SELECT * FROM tab WHERE id = ?).
 * If it contains positional templates, the values of the templates are stored in [parametersValues].
 * @param parametersValues If [sqliteStatementText] doesn't contain parameters, [parametersValues] is an empty list.
 * If it does contain parameters, [parametersValues] contains their values.
 * Each value is matched with each question mark in the order they appear in [sqliteStatementText], from left to right.
 * @param sqliteStatementWithInlineParameters The same string as [sqliteStatementText],
 * but positional templates have been replaced with the corresponding value in [parametersValues].
 */
data class SqliteStatement(
  val sqliteStatementText: String,
  val parametersValues: List<SqliteValue>,
  val sqliteStatementWithInlineParameters: String
) {
  constructor(sqliteStatement: String) : this(sqliteStatement, emptyList<SqliteValue>(), sqliteStatement)
}

enum class RowIdName(val stringName: String) {
  ROWID("rowid"), OID("oid"), _ROWID_("_rowid_")
}

/**
 * See [SQLite documentation](https://www.sqlite.org/datatype3.html) for how affinity is determined.
 */
enum class SqliteAffinity {
  TEXT, NUMERIC, INTEGER, REAL, BLOB;

  companion object {
    /**
     * See [SQLite doc](https://www.sqlite.org/datatype3.html#affinity_name_examples) for examples.
     */
    fun fromTypename(typename: String): SqliteAffinity {
      return when {
        typename.contains("int", true) -> INTEGER
        typename.contains("char", true) ||
        typename.contains("clob", true) ||
        typename.contains("text", true) -> TEXT
        typename.contains("blob", true) ||
        typename.isEmpty() -> BLOB
        typename.contains("real", true) ||
        typename.contains("floa", true) ||
        typename.contains("doub", true) -> REAL
        else -> NUMERIC
      }
    }

    fun fromJDBCType(jdbcType: JDBCType): SqliteAffinity {
      return fromTypename(jdbcType.name)
    }
  }
}

/**
 * Abstraction representing a value from a Sqlite database.
 *
 * We currently treat everything as String.
 * This is fine on the studio side, because these values are only used to be shown in the UI, as strings.
 * On the device side, we can send everything as string and SQLite will do its best to store data in the correct data format, based on the
 * affinity of the column. See [SQLite data types](https://www.sqlite.org/datatype3.html)
 */
sealed class SqliteValue {
  companion object {
    fun fromAny(value: Any?): SqliteValue {
      return when (value) {
        null -> NullValue
        else -> StringValue(value.toString())
      }
    }
  }

  data class StringValue(val value: String) : SqliteValue()
  object NullValue : SqliteValue()
}