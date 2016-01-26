/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Service;

import java.util.List;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;

public class AndroidModuleInfoTest extends AndroidGradleTestCase {
  public void testManifestOnly() throws Exception {
    loadProject("projects/moduleInfo/manifestOnly");
    assertNotNull(myAndroidFacet);
    assertEquals(7, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(18, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    //noinspection SpellCheckingInspection
    assertEquals("com.example.unittest", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testGradleOnly() throws Exception {
    loadProject("projects/moduleInfo/gradleOnly");
    assertNotNull(myAndroidFacet);
    assertEquals(9, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(17, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    assertEquals("from.gradle", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testBoth() throws Exception {
    loadProject("projects/moduleInfo/both");
    assertNotNull(myAndroidFacet);
    assertEquals(9, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(17, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    assertEquals("from.gradle", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testFlavors() throws Exception {
    loadProject("projects/moduleInfo/flavors");
    assertNotNull(myAndroidFacet);

    assertEquals(14, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(17, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    assertEquals("com.example.free.debug", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testMerge() throws Exception {
    loadProject("projects/moduleInfo/merge");
    assertNotNull(myAndroidFacet);

    List<Activity> mainActivities = ManifestInfo.get(myAndroidFacet.getModule(), false).getActivities();
    assertEquals(1, mainActivities.size());
    assertEquals(".Main", mainActivities.get(0).getActivityClass().getRawText());

    List<Activity> mergedActivities = ManifestInfo.get(myAndroidFacet.getModule(), true).getActivities();
    assertEquals(2, mergedActivities.size());
    Set<String> activities = Sets.newHashSet(mergedActivities.get(0).getActivityClass().getRawText(),
                                             mergedActivities.get(1).getActivityClass().getRawText());
    assertTrue(activities.contains(".Main"));
    assertTrue(activities.contains(".Debug"));

    List<Service> services = ManifestInfo.get(myAndroidFacet.getModule(), true).getServices();
    assertEquals(1, services.size());

    assertEquals("@style/AppTheme", ManifestInfo.get(myAndroidFacet.getModule(), false).getManifestTheme());
  }

  public void testManifestPlaceholderCompletion() throws Exception {
    loadProject("projects/moduleInfo/merge");
    assertNotNull(myAndroidFacet);
    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("src/main/AndroidManifest.xml");
    assertNotNull(file);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);

    myFixture.configureFromExistingVirtualFile(file);
    List<HighlightInfo> highlights = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertTrue(highlights.isEmpty());
    myFixture.testHighlighting(false, false, false, file);

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(psiFile);
    assertNotNull(document);

    final String defaultPlaceholder = "${defaultTheme}";

    // Check placeholder completion
    new WriteCommandAction.Simple(getProject(), psiFile) {
      @Override
      protected void run() throws Throwable {
        int offset = document.getText().indexOf(defaultPlaceholder);
        document.replaceString(offset, offset + defaultPlaceholder.length(), "${<caret>");
        manager.commitAllDocuments();
      }
    }.execute();
    FileDocumentManager.getInstance().saveAllDocuments();
    myFixture.configureFromExistingVirtualFile(file);

    myFixture.complete(CompletionType.BASIC);
    assertThat(myFixture.getLookupElementStrings()).containsOnly("defaultTheme");
    // Check that there are no errors
    myFixture.testHighlighting();
  }
}
