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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.QueryResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.AsyncTestUtils
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LiveDatabaseConnectionTest : PlatformTestCase() {
  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)
  private lateinit var liveDatabaseConnection: LiveDatabaseConnection

  override fun tearDown() {
    super.tearDown()
    Disposer.dispose(liveDatabaseConnection)
  }

  fun testReadSchema() {
    // Prepare
    val column1 = SqliteInspectorProtocol.Column.newBuilder()
      .setName("column1")
      .setType("TEXT")
      .build()

    val column2 = SqliteInspectorProtocol.Column.newBuilder()
      .setName("column2")
      .setType("INTEGER")
      .build()

    val column3 = SqliteInspectorProtocol.Column.newBuilder()
      .setName("column3")
      .setType("FLOAT")
      .build()

    val column4 = SqliteInspectorProtocol.Column.newBuilder()
      .setName("column4")
      .setType("BLOB")
      .build()

    val table = SqliteInspectorProtocol.Table.newBuilder()
      .addColumns(column1)
      .addColumns(column2)
      .addColumns(column3)
      .addColumns(column4)
      .build()

    val schema = GetSchemaResponse.newBuilder()
      .addTables(table)
      .build()

    val schemaResponse = Response.newBuilder()
      .setGetSchema(schema)
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(schemaResponse.toByteArray()))

    liveDatabaseConnection = LiveDatabaseConnection(mockMessenger, 1, taskExecutor)

    // Act
    val sqliteSchema = pumpEventsAndWaitForFuture(liveDatabaseConnection.readSchema())

    // Assert
    assertSize(1, sqliteSchema.tables)
    assertSize(4, sqliteSchema.tables.first().columns)
    assertEquals(RowIdName._ROWID_, sqliteSchema.tables.first().rowIdName)
    assertEquals("column1", sqliteSchema.tables.first().columns[0].name)
    assertEquals("column2", sqliteSchema.tables.first().columns[1].name)
    assertEquals("column3", sqliteSchema.tables.first().columns[2].name)
    assertEquals("column4", sqliteSchema.tables.first().columns[3].name)
    assertEquals(SqliteAffinity.TEXT, sqliteSchema.tables.first().columns[0].affinity)
    assertEquals(SqliteAffinity.INTEGER, sqliteSchema.tables.first().columns[1].affinity)
    assertEquals(SqliteAffinity.REAL, sqliteSchema.tables.first().columns[2].affinity)
    assertEquals(SqliteAffinity.BLOB, sqliteSchema.tables.first().columns[3].affinity)
  }

  fun testExecuteQuery() {
    // Prepare
    val cellValueString = SqliteInspectorProtocol.CellValue.newBuilder().setStringValue("a string").build()

    val cellValueFloat = SqliteInspectorProtocol.CellValue.newBuilder().setFloatValue(1f).build()

    val cellValueBlob = SqliteInspectorProtocol.CellValue.newBuilder()
      .setBlobValue(ByteString.copyFrom("a blob".toByteArray()))
      .build()

    val cellValueInt = SqliteInspectorProtocol.CellValue.newBuilder().setIntValue(1)
      .build()

    val cellValueNull = SqliteInspectorProtocol.CellValue.newBuilder().build()

    val columnNames = listOf("column1", "column2", "column3", "column4", "column5")

    val rows = SqliteInspectorProtocol.Row.newBuilder()
      .addValues(cellValueString)
      .addValues(cellValueFloat)
      .addValues(cellValueBlob)
      .addValues(cellValueInt)
      .addValues(cellValueNull)
      .build()

    val cursor = Response.newBuilder()
      .setQuery(
        QueryResponse.newBuilder()
          .addAllColumnNames(columnNames)
          .addRows(rows)
      )
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    liveDatabaseConnection = LiveDatabaseConnection(mockMessenger, 1, taskExecutor)

    // Act
    val resultSet = pumpEventsAndWaitForFuture(liveDatabaseConnection.execute(SqliteStatement("fake query")))!!

    // Assert
    val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
    val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1))

    assertSize(1, sqliteRows)
    assertSize(5, sqliteColumns)

    assertEquals("column1", sqliteColumns[0].name)
    assertEquals("column2", sqliteColumns[1].name)
    assertEquals("column3", sqliteColumns[2].name)
    assertEquals("column4", sqliteColumns[3].name)
    assertEquals("column5", sqliteColumns[4].name)

    assertEquals(SqliteAffinity.TEXT, sqliteColumns[0].affinity)
    assertEquals(SqliteAffinity.TEXT, sqliteColumns[1].affinity)
    assertEquals(SqliteAffinity.TEXT, sqliteColumns[2].affinity)
    assertEquals(SqliteAffinity.TEXT, sqliteColumns[3].affinity)
    assertEquals(SqliteAffinity.TEXT, sqliteColumns[4].affinity)

    assertEquals(sqliteRows[0].values[0].value, SqliteValue.StringValue("a string"))
    assertEquals(sqliteRows[0].values[1].value, SqliteValue.StringValue(1f.toString()))
    // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
    assertEquals(sqliteRows[0].values[2].value, SqliteValue.StringValue("6120626C6F62"))
    assertEquals(sqliteRows[0].values[3].value, SqliteValue.StringValue(1.toString()))
    assertEquals(sqliteRows[0].values[4].value, SqliteValue.NullValue)
  }

  fun testExecuteStatementWithParameters() {
    // Prepare
    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    val sqliteStatement = SqliteStatement("fake query", listOf(SqliteValue.StringValue("1"), SqliteValue.NullValue), "fakeQuery")

    val cursor = Response.newBuilder()
      .setQuery(QueryResponse.newBuilder())
      .build()

    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    liveDatabaseConnection = LiveDatabaseConnection(mockMessenger, 1, taskExecutor)

    // Act
    pumpEventsAndWaitForFuture(liveDatabaseConnection.execute(sqliteStatement))!!

    // Assert
    val param1 = SqliteInspectorProtocol.QueryParameterValue.newBuilder().setStringValue("1").build()
    val paramNull = SqliteInspectorProtocol.QueryParameterValue.newBuilder().build()

    val queryBuilder = SqliteInspectorProtocol.QueryCommand.newBuilder()
      .setQuery(sqliteStatement.sqliteStatementText)
      .addAllQueryParameterValues(listOf(param1, paramNull))
      .setDatabaseId(1)

    val queryCommand = SqliteInspectorProtocol.Command.newBuilder().setQuery(queryBuilder).build()
    
    verify(mockMessenger).sendRawCommand(queryCommand.toByteArray())
  }

  fun testReturnsEmptyResultSetForEmptyResponse() {
    // Prepare
    val cursor = Response.newBuilder().build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    liveDatabaseConnection = LiveDatabaseConnection(mockMessenger, 1, taskExecutor)

    // Act
    val resultSet = pumpEventsAndWaitForFuture(liveDatabaseConnection.execute(SqliteStatement("fake query")))!!

    // Assert
    val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
    val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1))

    assertSize(0, sqliteRows)
    assertSize(0, sqliteColumns)
  }

  fun testThrowsErrorOnErrorOccurredResponse() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setIsRecoverable(true)
        .setStackTrace("stackTrace")
        .build()
    ).build()

    val cursor = Response.newBuilder()
      .setErrorOccurred(errorOccurredEvent)
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    liveDatabaseConnection = LiveDatabaseConnection(mockMessenger, 1, taskExecutor)

    // Act / Assert
    AsyncTestUtils.pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
    AsyncTestUtils.pumpEventsAndWaitForFutureException(liveDatabaseConnection.execute(SqliteStatement("fake query")))
  }
}