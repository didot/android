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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator

import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * Abstraction over the UI component used to evaluate user-defined SQL statements.
 *
 * This is used by [com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController] to avoid direct dependency on the
 * UI implementation.
 *
 * @see [SqliteEvaluatorView.Listener] for the listener interface.
 */
interface SqliteEvaluatorView {
  val project: Project
  /**
   * The JComponent containing the view's UI.
   */
  val component: JComponent
  val tableView: TableView
  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)
  fun showSqliteStatement(sqliteStatement: String)

  /**
   * Adds a new [SqliteDatabase] at a specific position among other databases.
   * @param database The database to add.
   * @param index The index at which the database should be added.
   */
  fun addDatabase(database: SqliteDatabase, index: Int)

  /**
   * Selects the database to run statements on and to use for auto completion.
   */
  fun selectDatabase(database: SqliteDatabase)
  fun removeDatabase(index: Int)

  /**
   * Returns the [SqliteDatabase] currently selected in the UI.
   */
  fun getActiveDatabase(): SqliteDatabase

  /**
   * Returns the string corresponding to the SQLite statement currently visible in the UI.
   */
  fun getSqliteStatement(): String

  /**
   * Notifies the view that the schema associated with [database] has changed.
   */
  fun schemaChanged(database: SqliteDatabase)

  /**
   * Toggles on and off the ability to run sqlite statements
   */
  fun setRunSqliteStatementEnabled(enabled: Boolean)

  interface Listener {
    /**
     * Method invoked when an sql statement needs to be evaluated.
     */
    fun evaluateSqliteStatementActionInvoked(database: SqliteDatabase, sqliteStatement: String)

    /**
     * Called when the sqlite statement changes
     */
    fun sqliteStatementTextChangedInvoked(newSqliteStatement: String)
  }
}