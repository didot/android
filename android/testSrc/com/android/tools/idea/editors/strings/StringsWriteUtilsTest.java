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
package com.android.tools.idea.editors.strings;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;

import com.android.SdkConstants;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.AndroidTestCase;

public final class StringsWriteUtilsTest extends AndroidTestCase {
  private Project myProject;
  private VirtualFile myResourceDirectory;
  private ResourceRepository myResourceRepository;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myProject = myFacet.getModule().getProject();
    myResourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    myResourceRepository = ResourcesTestsUtil.createTestModuleRepository(myFacet, Collections.singletonList(myResourceDirectory));
  }

  @Override
  public void tearDown() throws Exception {
    myProject = null;
    myResourceRepository = null;
    super.tearDown();
  }

  public void testSetItemText() {
    StringsWriteUtils.setItemText(myProject, getResourceItem("key2", Locale.create("fr")), "L'Étranger");
    assertEquals("L\\'Étranger", getText("values-fr/strings.xml", "key2"));
  }

  public void testSetItemTextCdata() {
    StringsWriteUtils.setItemText(myProject, getResourceItem("key2", Locale.create("fr")), "<![CDATA[L'Étranger]]>");
    assertEquals("<![CDATA[L'Étranger]]>", getText("values-fr/strings.xml", "key2"));
  }

  public void testSetItemTextXliff() {
    StringsWriteUtils.setItemText(myProject, getResourceItem("key2", Locale.create("fr")), "<xliff:g>L'Étranger</xliff:g>");
    assertEquals("<xliff:g>L\\'Étranger</xliff:g>", getText("values-fr/strings.xml", "key2"));
  }

  private ResourceItem getResourceItem(String name, Locale locale) {
    Iterable<ResourceItem> items = myResourceRepository.getResources(RES_AUTO, ResourceType.STRING, name);

    for (ResourceItem item : items) {
      if (locale.qualifier.equals(item.getConfiguration().getLocaleQualifier())) {
        return item;
      }
    }

    throw new AssertionError();
  }

  private String getText(String path, String name) {
    VirtualFile virtualFile = myResourceDirectory.findFileByRelativePath(path);
    assert virtualFile != null;

    Object psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    assert psiFile != null;

    XmlTag resources = ((XmlFile)psiFile).getRootTag();
    assert resources != null;

    for (XmlTag string : resources.findSubTags("string")) {
      if (name.equals(string.getAttributeValue(SdkConstants.ATTR_NAME))) {
        return string.getValue().getText();
      }
    }

    throw new AssertionError();
  }
}
