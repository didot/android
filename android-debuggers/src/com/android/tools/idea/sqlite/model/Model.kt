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
data class SqliteTable(val name: String, val columns: List<SqliteColumn>, val isView: Boolean)

/** Representation of the Sqlite table row */
data class SqliteRow(val values: List<SqliteColumnValue>)

/** Representation of a Sqlite table column value */
data class SqliteColumnValue(val column: SqliteColumn, val value: Any?)

/** Representation of a Sqlite table column */
data class SqliteColumn(val name: String, val type: JDBCType)

/**
 *  Representation of a SQLite statement that may contain positional parameters.
 *
 *  If the statement doesn't contain parameters, [parametersValues] is an empty list.
 *  If it does contain parameters, [parametersValues] contains their values, assigned by order.
 */
data class SqliteStatement(val sqliteStatementText: String, val parametersValues: List<Any>) {
  constructor(sqliteStatement: String) : this(sqliteStatement, emptyList<Any>())

  override fun toString(): String {
    var renderedStatement = sqliteStatementText
    parametersValues.forEach {
      // TODO(b/143946270) doesn't handle statements like: `SELECT * FROM comments WHERE text LIKE "?" AND id > ?`
      renderedStatement = renderedStatement.replaceFirst("?", it.toString())
    }

    return  renderedStatement
  }
}