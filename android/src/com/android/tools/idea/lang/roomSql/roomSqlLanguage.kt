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
package com.android.tools.idea.lang.roomSql

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.LanguageFileType
import icons.AndroidIcons
import javax.swing.Icon


object ROOM_SQL_LANGUAGE : Language("Room SQL")

object ROOM_SQL_FILE_TYPE : LanguageFileType(ROOM_SQL_LANGUAGE) {
  override fun getName(): String = "Room SQL"
  override fun getDescription(): String = "SQL dialect used by Room"
  override fun getDefaultExtension(): String = "rsql"
  override fun getIcon(): Icon = ROOM_SQL_ICON
}

/**
 * Icon used for all things related to Room.
 *
 * TODO: Figure out if we want to re-use the DB icon or create a new one.
 */
val ROOM_SQL_ICON: Icon = AndroidIcons.DeviceExplorer.DatabaseFolder

class RoomSqlFileTypeFactory : FileTypeFactory() {
  override fun createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(ROOM_SQL_FILE_TYPE)
  }
}