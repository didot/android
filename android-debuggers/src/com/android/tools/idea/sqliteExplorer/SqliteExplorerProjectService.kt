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
package com.android.tools.idea.sqliteExplorer

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.sqlite.SqliteServiceFactoryImpl
import com.android.tools.idea.sqlite.controllers.SqliteController
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactoryImpl
import com.android.tools.idea.sqlite.ui.mainView.SqliteView
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import javax.swing.JComponent

/**
 * Intellij Project Service that holds the reference to the [SqliteController]
 * and is the entry point for opening a Sqlite database in the Sqlite Explorer tool window.
 */
class SqliteExplorerProjectService(
  private val project: Project,
  private val toolWindowManager: ToolWindowManager
) {
  companion object {
    @JvmStatic fun getInstance(project: Project): SqliteExplorerProjectService {
      return ServiceManager.getService(project, SqliteExplorerProjectService::class.java)
    }
  }

  private val controller: SqliteController

  /**
   * Returns the [JComponent] that contains the view of the Sqlite Explorer
   */
  val component get() = controller.sqliteView.component

  init {
    val sqliteView: SqliteView = invokeAndWaitIfNeeded { SqliteViewImpl(project, project) }

    controller = SqliteController(
      project,
      SqliteServiceFactoryImpl(),
      SqliteEditorViewFactoryImpl.getInstance(),
      sqliteView,
      EdtExecutorService.getInstance(),
      PooledThreadExecutor.INSTANCE
    )
    controller.setUp()
  }

  @AnyThread
  fun openSqliteDatabase(file: VirtualFile) {
    toolWindowManager.getToolWindow(SqliteExplorerToolWindowFactory.TOOL_WINDOW_ID).show { controller.openSqliteDatabase(file) }
  }
}