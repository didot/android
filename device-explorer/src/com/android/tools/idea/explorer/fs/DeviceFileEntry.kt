/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.fs

import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.google.common.util.concurrent.ListenableFuture
import java.nio.file.Path

/**
 * An file or directory entry in a [DeviceFileSystem]
 */
interface DeviceFileEntry {
  /**
   * The [DeviceFileSystem] this entry belongs to.
   */
  val fileSystem: DeviceFileSystem

  /**
   * The parent [DeviceFileEntry] or `null` if this is the root directory.
   */
  val parent: DeviceFileEntry?

  /**
   * The name of this entry in its parent directory.
   */
  val name: String

  /**
   * The full path of the entry in the device file system.
   */
  val fullPath: String

  /**
   * The list of entries contained in this directory.
   */
  val entries: ListenableFuture<List<DeviceFileEntry?>?>

  /**
   * Deletes the entry from the device file system.
   */
  fun delete(): ListenableFuture<Unit>

  /**
   * Creates a new file "`fileName`" in this directory, and returns a future that
   * completes when the file is created. If there is any error creating the file (including the path
   * already exists), the future completes with an exception.
   */
  fun createNewFile(fileName: String): ListenableFuture<Unit>

  /**
   * Creates a new directory "`directoryName`" in this directory, and returns a future that
   * completes when the directory is created. If there is any error creating the directory
   * (including the path already exists), the future completes with an exception.
   */
  fun createNewDirectory(directoryName: String): ListenableFuture<Unit>

  /**
   * Returns `true` if the entry is a symbolic link that points to a directory.
   *
   * @see com.android.tools.idea.explorer.adbimpl.AdbFileListing.isDirectoryLink
   */
  val isSymbolicLinkToDirectory: ListenableFuture<Boolean?>

  /**
   * Downloads the contents of the [DeviceFileEntry] to a local file.
   */
  fun downloadFile(
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit>

  /**
   * Uploads the contents of a local file to a remote [DeviceFileEntry] directory.
   */
  fun uploadFile(
    localPath: Path,
    progress: FileTransferProgress
  ): ListenableFuture<Unit> {
    return uploadFile(localPath, localPath.fileName.toString(), progress)
  }

  /**
   * Uploads the contents of a local file to a remote [DeviceFileEntry] directory.
   */
  fun uploadFile(
    localPath: Path,
    fileName: String,
    progress: FileTransferProgress
  ): ListenableFuture<Unit>

  /**
   * The permissions associated to this entry, similar to unix permissions.
   */
  val permissions: Permissions

  /**
   * The last modification date & time of this entry
   */
  val lastModifiedDate: DateTime

  /**
   * The size (in bytes) of this entry, or `-1` if the size is unknown.
   */
  val size: Long

  /**
   * `true` if the entry is a directory, i.e. it contains entries.
   */
  val isDirectory: Boolean

  /**
   * `true` if the entry is a file, i.e. it has content and does not contain entries.
   */
  val isFile: Boolean

  /**
   * `true` if the entry is a symbolic link.
   */
  val isSymbolicLink: Boolean

  /**
   * The link target of the entry if [.isSymbolicLink] is `true`, `null` otherwise.
   */
  val symbolicLinkTarget: String?

  /**
   * Permissions associated to a [DeviceFileEntry].
   */
  interface Permissions {
    val text: String
  }

  /**
   * Date & time associated to a [DeviceFileEntry].
   */
  interface DateTime {
    val text: String
  }
}