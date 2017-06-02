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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.editors.strings.StringResource;
import com.android.tools.idea.editors.strings.StringResourceKey;
import com.android.tools.idea.rendering.Locale;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import javax.swing.RowFilter.Entry;
import java.util.Collections;

public final class NeedsTranslationForLocaleRowFilterTest extends AndroidTestCase {
  public void testInclude() {
    assertFalse(new NeedsTranslationForLocaleRowFilter(Locale.create("ar")).include(mockEntry()));
  }

  @NotNull
  private Entry<StringResourceTableModel, Integer> mockEntry() {
    StringResource resource = new StringResource(new StringResourceKey("key", null), Collections.emptyList(), myFacet);
    resource.setTranslatable(false);

    StringResourceTableModel model = Mockito.mock(StringResourceTableModel.class);
    Mockito.when(model.getStringResourceAt(0)).thenReturn(resource);

    @SuppressWarnings("unchecked")
    Entry<StringResourceTableModel, Integer> entry = (Entry<StringResourceTableModel, Integer>)Mockito.mock(Entry.class);

    Mockito.when(entry.getModel()).thenReturn(model);
    Mockito.when(entry.getIdentifier()).thenReturn(0);

    return entry;
  }
}
