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

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

/**
 * Implementation of [AbstractDialogWrapper] that uses the [DialogWrapper] class from the platform.
 */
class DefaultDialogWrapper(private val options: DialogWrapperOptions) : AbstractDialogWrapper() {
  private val innerDialogWrapper: DialogWrapperInner by lazy {
    DialogWrapperInner()
  }

  override val disposable = Disposer.newDisposable()

  override var title: String
    get() = innerDialogWrapper.title
    set(value) { innerDialogWrapper.title = value }

  override var cancelButtonText: String
    get() = innerDialogWrapper.cancelAction.getValue(Action.NAME)?.toString() ?: ""
    set(value) { innerDialogWrapper.cancelAction.putValue(Action.NAME, value) }

  override var cancelButtonVisible: Boolean
    get() = innerDialogWrapper.cancelButton?.isVisible ?: false
    set(value) { innerDialogWrapper.cancelButton?.isVisible = value }

  override var cancelButtonEnabled: Boolean
    get() = innerDialogWrapper.cancelButton?.isEnabled ?: false
    set(value) { innerDialogWrapper.cancelButton?.isEnabled = value }

  override var okButtonText: String
    get() = innerDialogWrapper.okAction.getValue(Action.NAME)?.toString() ?: ""
    set(value) { innerDialogWrapper.okAction.putValue(Action.NAME, value) }

  override var okButtonVisible: Boolean
    get() = innerDialogWrapper.okButton?.isVisible ?: false
    set(value) { innerDialogWrapper.okButton?.isVisible = value }

  override var okButtonEnabled: Boolean
    get() = innerDialogWrapper.okButton?.isEnabled ?: false
    set(value) { innerDialogWrapper.okButton?.isEnabled = value }

  override fun init() {
    // Ensure we are disposed if our inner dialog is disposed (e.g. "Close" or "Cancel" button)
    Disposer.register(innerDialogWrapper.disposable, disposable)
    innerDialogWrapper.init()
  }

  override fun show() {
    innerDialogWrapper.show()
  }

  private inner class DialogWrapperInner
    : DialogWrapper(options.project, options.canBeParent, options.ideModalityType) {

    public override fun init() {
      options.preferredFocusProvider()?.let { super.myPreferredFocusedComponent = it }
      options.cancelButtonText?.let { setCancelButtonText(it) }
      options.okButtonText?.let { setOKButtonText(it) }
      if (!options.hasOkButton) {
        cancelAction.putValue(DEFAULT_ACTION, true)
      }
      isModal = options.isModal
      title = options.title
      super.init()
    }

    /** Make [DialogWrapper.getOKAction] publicly accessible */
    val okAction = super.getOKAction()

    /** Make [DialogWrapper.getCancelAction] publicly accessible */
    @get:JvmName("getCancelAction_")
    val cancelAction = super.getCancelAction()

    val okButton: JButton?
      get() {
        return getButton(okAction)
      }

    val cancelButton: JButton?
      get() {
        return getButton(cancelAction)
      }

    override fun createCenterPanel(): JComponent {
      return options.centerPanelProvider()
    }

    override fun createActions(): Array<Action> {
      val helpAction = helpAction
      if (!options.hasOkButton) {
        return if (helpAction === myHelpAction && helpId == null) arrayOf(cancelAction)
        else arrayOf(cancelAction, helpAction)
      }
      else {
        return if (helpAction === myHelpAction && helpId == null) arrayOf(okAction, cancelAction)
        else arrayOf(okAction, cancelAction, helpAction)
      }
    }

    override fun doOKAction() {
      val handled = options.okActionHandler()
      if (!handled) {
        super.doOKAction()
      }
    }

    override fun doValidate(): ValidationInfo? {
      return options.validationHandler()
    }
  }
}