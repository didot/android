/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth

class AndroidXmlCompletionContributorTest : AndroidTestCase() {
  fun testNamespaceCodeCompletion() {
    myFixture.configureFromExistingVirtualFile(
      myFixture.addFileToProject(
        "res/values/values.xml",
        """
          <resources xmln$caret>
          </resources>
          """.trimIndent()
      ).virtualFile
    )

    myFixture.completeBasic()

    Truth.assertThat(myFixture.lookupElementStrings).containsExactly(
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "xmlns:app=\"http://schemas.android.com/apk/res-auto\"",
      "xmlns:tools=\"http://schemas.android.com/tools\"",
    )
  }

  fun testNamespaceCodeCompletionXMLNStyped() {
    myFixture.configureFromExistingVirtualFile(
      myFixture.addFileToProject(
        "res/values/values.xml",
        """
          <resources xmlns:$caret>
          </resources>
          """.trimIndent()
      ).virtualFile
    )

    myFixture.completeBasic()

    Truth.assertThat(myFixture.lookupElementStrings).containsExactly(
      "android=\"http://schemas.android.com/apk/res/android\"",
      "app=\"http://schemas.android.com/apk/res-auto\"",
      "tools=\"http://schemas.android.com/tools\"",
    )
  }
}