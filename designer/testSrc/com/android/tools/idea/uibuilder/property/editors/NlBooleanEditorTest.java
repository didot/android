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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.android.tools.idea.uibuilder.property.fixtures.NlBooleanEditorFixture;

import static com.android.SdkConstants.ATTR_CHECKED;

public class NlBooleanEditorTest extends PropertyTestCase {
  private NlBooleanEditorFixture myEditor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myEditor = NlBooleanEditorFixture.createForInspector();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myEditor.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCheckThreeStates() {
    myEditor
      .setProperty(getProperty(myCheckBox1, ATTR_CHECKED))
      .expectValue(null)
      .expectCheckboxTipText(NlBooleanEditor.TIP_TEXT_DONT_CARE)
      .click()
      .expectValue(Boolean.TRUE)
      .expectCheckboxTipText(NlBooleanEditor.TIP_TEXT_SELECTED)
      .click()
      .expectValue(Boolean.FALSE)
      .expectCheckboxTipText(NlBooleanEditor.TIP_TEXT_NOT_SELECTED)
      .click()
      .expectValue(null)
      .expectCheckboxTipText(NlBooleanEditor.TIP_TEXT_DONT_CARE);
  }
}
