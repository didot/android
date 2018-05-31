// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor

import com.android.tools.idea.naveditor.editor.AddDestinationMenu
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureTemplateParametersWizardFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.finder.WindowFinder
import org.fest.swing.fixture.JListFixture
import javax.swing.JDialog
import javax.swing.JPanel

class AddDestinationMenuFixture(private val robot: Robot, private val menu: AddDestinationMenu) :
    ComponentFixture<AddDestinationMenuFixture, JPanel>(AddDestinationMenuFixture::class.java, robot, menu.mainPanel) {

  fun selectDestination(label: String) {
    val index = ProgressManager.getInstance().runProcess(Computable {
      ApplicationManager.getApplication().runReadAction(Computable {
        (0 until menu.destinationsList.itemsCount).first { menu.destinationsList.model.getElementAt(it).label == label }
      })
    }, EmptyProgressIndicator())
    JListFixture(robot, menu.destinationsList).clickItem(index)
  }

  fun visibleItemCount(): Int {
    return menu.destinationsList.itemsCount
  }

  fun clickCreateBlank(): ConfigureTemplateParametersWizardFixture {
    ActionButtonFixture(robot, menu.blankDestinationButton).click()

    val dialog = WindowFinder.findDialog(object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
      override fun isMatching(dialog: JDialog) = dialog.title == "New Android Component" && dialog.isShowing
    }).withTimeout(500).using(robot)

    return ConfigureTemplateParametersWizardFixture(robot, dialog.target() as JDialog)
  }
}
