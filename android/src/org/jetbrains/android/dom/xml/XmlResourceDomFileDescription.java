/*
 * Copyright (C) 2019 The Android Open Source Project
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

package org.jetbrains.android.dom.xml;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlResourceDomFileDescription extends AndroidResourceDomFileDescription<XmlResourceElement> {
  public XmlResourceDomFileDescription() {
    super(XmlResourceElement.class, "PreferenceScreen", ResourceFolderType.XML);
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  public static boolean isXmlResourceFile(@NotNull final XmlFile file) {
    return ReadAction.compute(() -> new XmlResourceDomFileDescription().isMyFile(file, null));
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    if (!super.isMyFile(file, module)) {
      return false;
    }
    final XmlTag rootTag = file.getRootTag();

    if (rootTag == null || !rootTag.getNamespace().isEmpty()) {
      return false;
    }
    for (XmlAttribute attribute : rootTag.getAttributes()) {
      if (attribute.getName().equals("xmlns")) {
        return false;
      }
    }
    return true;
  }
}
