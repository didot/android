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
package com.android.tools.idea.res.aar;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.google.common.collect.ListMultimap;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.List;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;

/**
 * Tests for {@link AarSourceResourceRepository}.
 */
public class AarSourceResourceRepositoryTest extends TestCase {

  public void testGetAllDeclaredIds() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    assertThat(repository.getAllDeclaredIds()).containsExactly(
      "id1", 0x7f0b0000,
      "id2", 0x7f0b0001,
      "id3", 0x7f0b0002);
  }

  public void testMultipleValues() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    List<ResourceItem> items = repository.getResourceItems(RES_AUTO, ResourceType.STRING, "hello");
    assertNotNull(items);
    List<String> helloVariants = ContainerUtil.map(
      items,
      resourceItem -> {
        ResourceValue value = resourceItem.getResourceValue();
        assertNotNull(value);
        return value.getValue();
      });
    assertSameElements(helloVariants, "bonjour", "hello", "hola");
  }

  public void testLibraryNameIsMaintained() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    assertThat(repository.getLibraryName()).isEqualTo(ResourcesTestsUtil.AAR_LIBRARY_NAME);
    for (ListMultimap<String, ResourceItem> multimap : repository.getItems().values()) {
      for (ResourceItem item : multimap.values()) {
        assertThat(item.getLibraryName()).isEqualTo(ResourcesTestsUtil.AAR_LIBRARY_NAME);
      }
    }
  }

  public void testPackageName() {
    AarSourceResourceRepository repository = ResourcesTestsUtil.getTestAarRepository();
    assertThat(repository.getPackageName()).isEqualTo(ResourcesTestsUtil.AAR_PACKAGE_NAME);
  }
}
