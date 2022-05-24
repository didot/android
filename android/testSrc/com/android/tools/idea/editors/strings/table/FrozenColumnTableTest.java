/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.testing.AndroidProjectRule;
import java.awt.Component;
import java.awt.Dimension;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

public final class FrozenColumnTableTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void getRowHeight() {
    FrozenColumnTable frozenColumnTable = new FrozenColumnTable(new DefaultTableModel(1, 4), 2);

    JTable frozenTable = frozenColumnTable.getFrozenTable();
    frozenTable.setRowHeight(26);
    frozenTable.getRowHeight();

    JTable scrollableTable = frozenColumnTable.getScrollableTable();
    scrollableTable.setRowHeight(29);
    scrollableTable.getRowHeight();

    assertEquals(29, frozenColumnTable.getRowHeight());
  }

  @Test
  public void leIsScrollableEvenWithoutLocales() {
    Object[][] data = new Object[][] {
      new Object[]{"east", "app/src/main/res", false, "east"},
      new Object[]{"west", "app/src/main/res", false, "west"},
      new Object[]{"north", "app/src/main/res", false, "north"}
    };
    Object[] columns = new Object[]{"Key", "Resource Folder", "Untranslatable", "Default Value"};
    DefaultTableModel model = new DefaultTableModel(data, columns);
    FrozenColumnTable frozenColumnTable = new FrozenColumnTable(model, 4);
    frozenColumnTable.getFrozenTable().createDefaultColumnsFromModel();
    frozenColumnTable.getScrollableTable().createDefaultColumnsFromModel();
    JScrollPane pane = (JScrollPane)frozenColumnTable.getScrollPane();
    pane.setBounds(0, 0, 800, 20);
    new FakeUi(pane, 1.0, true);
    pane.doLayout();
    assertThat(pane.getVerticalScrollBar().isVisible()).isTrue();
  }
}
