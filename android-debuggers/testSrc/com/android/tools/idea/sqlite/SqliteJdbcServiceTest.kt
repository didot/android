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
package com.android.tools.idea.sqlite

import com.android.tools.idea.editors.sqlite.SqliteTestUtil
import com.android.tools.idea.editors.sqlite.SqliteViewer
import com.android.tools.idea.sqlite.Utils.pumpEventsAndWaitForFuture
import com.android.tools.idea.sqlite.Utils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.jdbc.SqliteJdbcService
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteTable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.ide.PooledThreadExecutor
import java.sql.JDBCType

class SqliteJdbcServiceTest : PlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private var previouslyEnabled: Boolean = false

  override fun setUp() {
    super.setUp()
    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()
    previouslyEnabled = SqliteViewer.enableFeature(true)
  }

  override fun tearDown() {
    try {
      sqliteUtil.tearDown()
      SqliteViewer.enableFeature(previouslyEnabled)
    }
    finally {
      super.tearDown()
    }
  }

  fun testReadSchemaFailsIfDatabaseNotOpened() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)

    // Act
    val error = pumpEventsAndWaitForFutureException(service.readSchema())

    // Assert
    assertThat(error).isInstanceOf(IllegalStateException::class.java)

    service.closeDatabase()
  }

  fun testReadSchemaReturnsTablesAndColumns() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    val schema = pumpEventsAndWaitForFuture(service.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(2)
    val authorTable = schema.tables.find { it.name == "Author" }
    assertThat(authorTable).isNotNull()
    assertThat(authorTable?.columns?.count()).isEqualTo(3)
    assertThat(authorTable?.hasColumn("author_id", JDBCType.INTEGER)).isTrue()
    assertThat(authorTable?.hasColumn("first_name", JDBCType.VARCHAR)).isTrue()
    assertThat(authorTable?.hasColumn("last_name", JDBCType.VARCHAR)).isTrue()

    val bookTable = schema.tables.find { it.name == "Book" }
    assertThat(bookTable).isNotNull()
    assertThat(bookTable?.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(bookTable?.hasColumn("title", JDBCType.VARCHAR)).isTrue()
    assertThat(bookTable?.hasColumn("isbn", JDBCType.VARCHAR)).isTrue()
    assertThat(bookTable?.hasColumn("author_id", JDBCType.INTEGER)).isTrue()

    service.closeDatabase()
  }

  fun testCloseUnlocksFile() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    pumpEventsAndWaitForFuture(service.closeDatabase())
    ApplicationManager.getApplication().runWriteAction {
      file.delete(this)
    }

    // Assert
    assertThat(file.exists()).isFalse()
  }

  fun testReadTableReturnsResultSet() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    val resultSet = pumpEventsAndWaitForFuture(service.readTable(SqliteTable("Book", listOf())))

    // Assert
    assertThat(resultSet.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", JDBCType.VARCHAR)).isTrue()
    assertThat(resultSet.hasColumn("isbn", JDBCType.VARCHAR)).isTrue()
    assertThat(resultSet.hasColumn("author_id", JDBCType.INTEGER)).isTrue()

    // Act
    resultSet.rowBatchSize = 3
    var rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(1)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(0)

    service.closeDatabase()
  }

  fun testExecuteQuerySelectAllReturnsResultSet() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    val resultSet = pumpEventsAndWaitForFuture(service.executeQuery("SELECT * FROM Book"))

    // Assert
    assertThat(resultSet.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", JDBCType.VARCHAR)).isTrue()
    assertThat(resultSet.hasColumn("isbn", JDBCType.VARCHAR)).isTrue()
    assertThat(resultSet.hasColumn("author_id", JDBCType.INTEGER)).isTrue()

    // Act
    resultSet.rowBatchSize = 3
    var rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(1)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(0)

    service.closeDatabase()
  }

  fun testExecuteQuerySelectColumnReturnsResultSet() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    val resultSet = pumpEventsAndWaitForFuture(service.executeQuery("SELECT book_id FROM Book"))

    // Assert
    assertThat(resultSet.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", JDBCType.VARCHAR)).isFalse()
    assertThat(resultSet.hasColumn("isbn", JDBCType.VARCHAR)).isFalse()
    assertThat(resultSet.hasColumn("author_id", JDBCType.INTEGER)).isFalse()

    // Act
    resultSet.rowBatchSize = 3
    var rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(1)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.nextRowBatch())

    // Assert
    assertThat(rows.count()).isEqualTo(0)

    service.closeDatabase()
  }

  fun testExecuteUpdateDropTable() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    pumpEventsAndWaitForFuture(service.executeUpdate("DROP TABLE Book"))
    val error = pumpEventsAndWaitForFutureException(service.readTable(SqliteTable("Book", listOf())))

    // Assert
    assertThat(error).isNotNull()

    service.closeDatabase()
  }

  fun testResultSetThrowsAfterDisposed() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    val resultSet = pumpEventsAndWaitForFuture(service.readTable(SqliteTable("Book", listOf())))
    Disposer.dispose(resultSet)
    val error = pumpEventsAndWaitForFutureException(resultSet.nextRowBatch())

    // Assert
    assertThat(error).isNotNull()

    service.closeDatabase()
  }

  fun testReadTableFailsWhenIncorrectTableName() {
    // Prepare
    val file = sqliteUtil.createTestSqliteDatabase()
    val service = SqliteJdbcService(file, testRootDisposable, PooledThreadExecutor.INSTANCE)
    pumpEventsAndWaitForFuture(service.openDatabase())

    // Act
    val error = pumpEventsAndWaitForFutureException(service.readTable(SqliteTable("IncorrectTableName", listOf())))

    // Assert
    assertThat(error).isNotNull()

    service.closeDatabase()
  }

  private fun SqliteResultSet.hasColumn(name: String, type: JDBCType) : Boolean {
    return pumpEventsAndWaitForFuture(this.columns()).find { it.name == name }?.type?.equals(type) ?: false
  }

  private fun SqliteTable.hasColumn(name: String, type: JDBCType) : Boolean {
    return this.columns.find { it.name == name }?.type?.equals(type) ?: false
  }

  companion object {
    const val TIMEOUT_MILLISECONDS: Long = 30000
  }
}
