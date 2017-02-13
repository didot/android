/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.editors.strings.StringResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.android.tools.idea.ui.TableUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.Collections;

public final class StringsCellEditorTest extends AndroidTestCase {
  public void testIsCellEditable() {
    Collection<VirtualFile> directories = Collections.singletonList(myFixture.copyDirectoryToProject("stringsEditor/base/res", "res"));
    MultiResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, directories);

    JTable table = new JBTable(new StringResourceTableModel(new StringResourceRepository(repository).getData(myFacet)));
    TableUtils.selectCellAt(table, 0, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

    assertTrue(new StringsCellEditor().isCellEditable(new ActionEvent(table, 0, null)));
  }
}
