/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Abstraction over [DialogWrapper] that is compatible with unit testing.
 *
 * Instead of deriving from [DialogWrapper], consumers should create an instance of [AbstractDialogWrapper]
 * via the [factory] property, which can be customized for unit tests. That is, consumers should
 * use composition instead of inheritance.
 *
 * The API surface area is by design much smaller than [DialogWrapper] so that it is compatible with
 * unit testing.
 */
abstract class AbstractDialogWrapper {
  /**
   * Function that creates the center panel of the dialog. This property should be set by the consumer
   * before calling [init]
   */
  var centerPanelProvider: () -> JComponent = { JPanel() }

  /**
   * The title of the dialog
   */
  abstract var title: String

  /**
   * `true` if the dialog should be modal
   */
  abstract var isModal: Boolean

  /**
   * The text of the `OK` button (default is "ok")
   */
  abstract var okButtonText: String?

  abstract fun show()
  abstract fun init()

  companion object {
    private var defaultFactory = object : DialogWrapperFactory {
      override fun createDialogWrapper(project: Project,
                                       canBeParent: Boolean,
                                       ideModalityType: DialogWrapper.IdeModalityType): AbstractDialogWrapper {
        return DefaultDialogWrapper(project, canBeParent, ideModalityType)
      }
    }
    var factory = defaultFactory
  }
}