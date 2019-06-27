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

import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactory
import com.android.tools.idea.sqlite.ui.tableView.TableView
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import javax.swing.JComponent

open class MockSqliteEditorViewFactory : SqliteEditorViewFactory {

  private val sqliteEvaluatorView = MockSqliteEvaluatorView()
  private val tableView: TableView = mock(TableView::class.java)

  init {
    `when`(tableView.component).thenReturn(mock(JComponent::class.java))
  }

  override fun createEvaluatorDialog() = sqliteEvaluatorView

  override fun createTableView(): TableView = tableView
}