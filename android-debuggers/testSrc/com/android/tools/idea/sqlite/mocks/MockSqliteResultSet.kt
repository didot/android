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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.sql.JDBCType

class MockSqliteResultSet(size: Int = 100) : SqliteResultSet {
  val _columns = listOf(SqliteColumn("id", JDBCType.INTEGER, false))
  private val rows = mutableListOf<SqliteRow>()

  val invocations = mutableListOf<List<SqliteRow>>()

  init {
    for (i in 0 until size) {
      rows.add(SqliteRow(listOf(SqliteColumnValue(_columns[0], i))))
    }
  }

  override val columns: ListenableFuture<List<SqliteColumn>> get() = Futures.immediateFuture(_columns)

  override val rowCount: ListenableFuture<Int> get() = Futures.immediateFuture(rows.size)

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    assert(rowOffset >= 0)
    assert(rowBatchSize > 0)

    val toIndex = if (rowOffset + rowBatchSize > rows.size) rows.size else rowOffset + rowBatchSize

    val rows = rows.subList(rowOffset, toIndex).toList()
    invocations.add(rows)
    return Futures.immediateFuture(rows)
  }

  override fun dispose() {
  }
}