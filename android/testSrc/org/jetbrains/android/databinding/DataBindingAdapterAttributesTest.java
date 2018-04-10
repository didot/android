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
package org.jetbrains.android.databinding;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;

import java.util.Arrays;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X;

public class DataBindingAdapterAttributesTest extends AndroidGradleTestCase {

  public void testCompletionAndInspections() throws Exception {
    loadProject(PROJECT_WITH_DATA_BINDING);
    assertCompletionAndInspections();
  }

  public void testCompletionAndInspectionsAndroidX() throws Exception {
    loadProject(PROJECT_WITH_DATA_BINDING_ANDROID_X);
    assertCompletionAndInspections();
  }

  /**
   * Checks {@code @BindingAdapter} annotation completion and uses {@link AndroidUnknownAttributeInspection} to check that the attribute
   * has been defined.
   */
  private void assertCompletionAndInspections() {
    myFixture.enableInspections(AndroidUnknownAttributeInspection.class);

    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    myFixture.openFileInEditor(file);
    Editor editor = myFixture.getEditor();
    Document document = editor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    int offset = document.getText().indexOf("name}\"");
    int line = document.getLineNumber(offset);

    assertFalse(document.getText().contains("my_binding_attribute"));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineEndOffset(line), " my_bind");
      documentManager.commitAllDocuments();
    });

    editor.getCaretModel().moveToOffset(document.getLineEndOffset(line));
    assertNull(myFixture.completeBasic()); // null because there is only one completion option
    assertTrue(document.getText().contains("my_binding_attribute"));

    // The attribute is defined by @BindingAdapter so it shouldn't be marked as unknown
    assertTrue(myFixture.doHighlighting(HighlightSeverity.WARNING).isEmpty());

    // The next attribute is not defined so it should be marked as a warning
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineEndOffset(line), " my_not_attribute=\"\"");
      documentManager.commitAllDocuments();
    });
    assertFalse(myFixture.doHighlighting(HighlightSeverity.WARNING).isEmpty());

    // Check that we do not return duplicate attributes (http://b/67408823)
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineEndOffset(line), " android:paddin");
      documentManager.commitAllDocuments();
    });
    editor.getCaretModel().moveToOffset(document.getLineEndOffset(line));
    long paddingCompletions = Arrays.stream(myFixture.completeBasic())
      .map(LookupElement::getLookupString)
      .filter("padding"::equals)
      .count();
    assertEquals(1, paddingCompletions);
  }
}
