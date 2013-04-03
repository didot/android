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

package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.converters.ParentStyleConverter;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 5, 2009
 * Time: 6:00:43 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Style extends ResourceElement {
  List<StyleItem> getItems();

  StyleItem addItem();

  @Attribute("parent")
  @Convert(ParentStyleConverter.class)
  GenericAttributeValue<ResourceValue> getParentStyle();

  @Override
  @Required
  @Convert(ResourceNameConverter.class)
  GenericAttributeValue<String> getName();
}
