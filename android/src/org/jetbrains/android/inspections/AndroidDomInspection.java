/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.AndroidXmlExtension;
import org.jetbrains.android.dom.converters.AndroidPackageConverter;
import org.jetbrains.android.dom.converters.AndroidPermissionConverter;
import org.jetbrains.android.dom.converters.ConstantFieldConverter;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.dom.resources.DeclareStyleableNameConverter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidDomInspection extends BasicDomElementsInspection<AndroidDomElement> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.AndroidDomInspection");

  public AndroidDomInspection() {
    super(AndroidDomElement.class);
  }

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.dom.name");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "AndroidDomInspection";
  }

  @Override
  protected boolean shouldCheckResolveProblems(GenericDomValue value) {
    final Converter realConverter = WrappingConverter.getDeepestConverter(value.getConverter(), value);
    return !(realConverter instanceof AndroidPackageConverter) &&
           !(realConverter instanceof DeclareStyleableNameConverter) &&
           !(realConverter instanceof OnClickConverter) &&
           !(realConverter instanceof ConstantFieldConverter) &&
           !(realConverter instanceof AndroidPermissionConverter);
  }

  @Override
  protected void checkChildren(final DomElement element, Consumer<DomElement> visitor) {
    // The following code is similar to contents of the overridden method,
    // but adds support for "aapr:attr" attributes.
    final XmlElement xmlElement = element.getXmlElement();
    if (xmlElement instanceof XmlTag) {
      for (final DomElement child : DomUtil.getDefinedChildren(element, true, true)) {
        final XmlElement element1 = child.getXmlElement();
        if (element1 == null) {
          LOG.error("No XML element for DomElement " + child + " of class " + child.getClass().getName() +
                    "; parent=" + element);
        }
        else if (element1.isPhysical()) {
          visitor.consume(child);
        }
      }

      for (final AbstractDomChildrenDescription description : element.getGenericInfo().getChildrenDescriptions()) {
        if (description.getAnnotation(Required.class) != null) {
          for (final DomElement child : description.getValues(element)) {
            if (!child.exists()) {
              String name = child.getXmlElementName();
              String namespaceKey = child.getXmlElementNamespaceKey();
              if (namespaceKey != null) {
                name = namespaceKey + ':' + name;
              }
              if (!AndroidXmlExtension.isAaptAttributeDefined((XmlTag)xmlElement, name)) {
                visitor.consume(child);
              }
            }
          }
        }
      }
    }
  }
}