/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.ResourceResolverCache;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;

@SuppressWarnings("SpellCheckingInspection")
public class IncludeReferenceTest extends AndroidTestCase {
  public void testBasic() {
    VirtualFile file1 = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout/designtime.xml");
    assertNotNull(file1);
    VirtualFile file2 = myFixture.copyFileToProject("xmlpull/visible_child.xml", "res/layout/visible_child.xml");
    assertNotNull(file2);
    VirtualFile file3 = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout-land/designtime.xml");
    assertNotNull(file1);

    IncludeReference reference = IncludeReference.create(myModule, file1, file2);
    assertEquals("designtime.xml", reference.getFromDisplayName());
    assertEquals("designtime", reference.getFromResourceName());
    assertEquals("@layout/designtime", reference.getFromResourceUrl());
    assertSame(file1, reference.getFromFile());
    assertSame(file2, reference.getToFile());
    assertSame(file1, LocalFileSystem.getInstance().findFileByIoFile(reference.getFromPath()));
    //noinspection ConstantConditions
    assertSame(file2, LocalFileSystem.getInstance().findFileByIoFile(reference.getToPath()));
    assertSame(myModule, reference.getModule());

    reference = IncludeReference.create(myModule, file3, file2);
    assertEquals("designtime", reference.getFromResourceName());
    assertEquals("@layout/designtime", reference.getFromResourceUrl());
    assertEquals("layout-land/designtime.xml", reference.getFromDisplayName());
  }

  public void testGetSet() {
    VirtualFile included = myFixture.copyFileToProject("designer/included.xml", "res/layout/included.xml");
    assertNotNull(included);
    VirtualFile includer = myFixture.copyFileToProject("designer/included.xml", "res/layout/includer.xml");
    assertNotNull(includer);
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(included);
    assertNotNull(psiFile);

    assertEquals("@layout/includer", IncludeReference.getIncludingLayout(psiFile));

    ConfigurationManager manager = myFacet.getConfigurationManager();
    Configuration configuration = manager.getConfiguration(included);
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    assertNotNull(resourceResolver);
    IncludeReference reference = IncludeReference.get(myModule, psiFile, resourceResolver);
    assertEquals("includer.xml", reference.getFromDisplayName());
    assertEquals("includer", reference.getFromResourceName());
    assertEquals("@layout/includer", reference.getFromResourceUrl());

    assertSame(reference.getFromFile(), includer);
    assertSame(reference.getToFile(), included);

    IncludeReference.setIncludingLayout(getProject(), psiFile, null);
    assertSame(IncludeReference.NONE, IncludeReference.get(myModule, psiFile, resourceResolver));

    VirtualFile other = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout-land/designtime.xml");
    assertNotNull(other);
    IncludeReference.setIncludingLayout(getProject(), psiFile, "@layout/designtime");
    assertEquals("@layout/designtime", IncludeReference.getIncludingLayout(psiFile));
  }
}