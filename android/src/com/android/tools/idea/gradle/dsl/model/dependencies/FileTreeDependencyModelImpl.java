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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FileTreeDependencyModelImpl extends DependencyModelImpl implements FileTreeDependencyModel {
  private static final Logger LOG = Logger.getInstance(FileTreeDependencyModelImpl.class);

  @NonNls public static final String FILE_TREE = "fileTree";
  @NonNls private static final String DIR = "dir";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String EXCLUDE = "exclude";

  @NotNull private String myConfigurationName;
  @NotNull private final GradleDslMethodCall myDslElement;
  @NotNull private final GradleDslSimpleExpression myDir;

  @Nullable private final GradleDslElement myIncludeElement;
  @Nullable private final GradleDslElement myExcludeElement;

  static Collection<FileTreeDependencyModel> create(@NotNull String configurationName, @NotNull GradleDslMethodCall methodCall) {
    List<FileTreeDependencyModel> result = Lists.newArrayList();
    if (FILE_TREE.equals(methodCall.getMethodName())) {
      List<GradleDslExpression> arguments = methodCall.getArguments();
      for (GradleDslExpression argument : arguments) {
        if (argument instanceof GradleDslSimpleExpression) {
          result.add(new FileTreeDependencyModelImpl(configurationName, methodCall, (GradleDslSimpleExpression)argument, null, null));
        }
        else if (argument instanceof GradleDslExpressionMap) {
          GradleDslExpressionMap dslMap = (GradleDslExpressionMap)argument;
          GradleDslSimpleExpression dirElement = dslMap.getPropertyElement(DIR, GradleDslSimpleExpression.class);
          if (dirElement == null) {
            assert methodCall.getPsiElement() != null;
            String msg = String.format("'%1$s' is not a valid file tree dependency", methodCall.getPsiElement().getText());
            LOG.warn(msg);
            continue;
          }
          GradleDslElement includeElement = dslMap.getPropertyElement(INCLUDE);
          GradleDslElement excludeElement = dslMap.getPropertyElement(EXCLUDE);
          result.add(new FileTreeDependencyModelImpl(configurationName, methodCall, dirElement, includeElement, excludeElement));
        }
      }
    }
    return result;
  }

  static void create(@NotNull GradlePropertiesDslElement parent,
                     @NotNull String configurationName,
                     @NotNull String dir,
                     @Nullable List<String> includes,
                     @Nullable List<String> excludes) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parent, name, FILE_TREE);
    if ((includes == null || includes.isEmpty()) && (excludes == null || excludes.isEmpty())) {
      GradleDslLiteral directory = new GradleDslLiteral(methodCall, name);
      directory.setValue(dir);
      methodCall.addNewArgument(directory);
    }
    else {
      GradleDslExpressionMap mapArguments = new GradleDslExpressionMap(methodCall, name);
      mapArguments.setNewLiteral(DIR, dir);
      if (includes != null && !includes.isEmpty()) {
        if (includes.size() == 1) {
          mapArguments.setNewLiteral(INCLUDE, includes.get(0));
        }
        else {
          for (String include : includes) {
            mapArguments.addToNewLiteralList(INCLUDE, include);
          }
        }
      }
      if (excludes != null && !excludes.isEmpty()) {
        if (excludes.size() == 1) {
          mapArguments.setNewLiteral(EXCLUDE, excludes.get(0));
        }
        else {
          for (String exclude : excludes) {
            mapArguments.addToNewLiteralList(EXCLUDE, exclude);
          }
        }
      }
      methodCall.addNewArgument(mapArguments);
    }
    parent.setNewElement(methodCall);
  }

  private FileTreeDependencyModelImpl(@NotNull String configurationName,
                                      @NotNull GradleDslMethodCall dslElement,
                                      @NotNull GradleDslSimpleExpression dir,
                                      @Nullable GradleDslElement includeElement,
                                      @Nullable GradleDslElement excludeElement) {
    myConfigurationName = configurationName;
    myDslElement = dslElement;
    myDir = dir;
    myIncludeElement = includeElement;
    myExcludeElement = excludeElement;
  }

  @Override
  @NotNull
  protected GradleDslElement getDslElement() {
    return myDslElement;
  }

  @Override
  @NotNull
  public String configurationName() {
    return myConfigurationName;
  }

  @Override
  @NotNull
  public GradleNotNullValue<String> dir() {
    String dir = myDir.getValue(String.class);
    assert dir != null;
    return new GradleNotNullValueImpl<>(myDir, dir);
  }

  @Override
  public void setDir(@NotNull String dir) {
    myDir.setValue(dir);
  }

  @Override
  @NotNull
  public List<GradleNotNullValue<String>> includes() {
    return getStringValues(myIncludeElement);
  }

  @Override
  @NotNull
  public List<GradleNotNullValue<String>> excludes() {
    return getStringValues(myExcludeElement);
  }

  @NotNull
  private static List<GradleNotNullValue<String>> getStringValues(@Nullable GradleDslElement expressionOrList) {
    if (expressionOrList instanceof GradleDslExpressionList) {
      return ((GradleDslExpressionList)expressionOrList).getValues(String.class);
    }

    if (expressionOrList instanceof GradleDslSimpleExpression) {
      String value = ((GradleDslSimpleExpression)expressionOrList).getValue(String.class);
      if (value != null) {
        return Collections.singletonList(new GradleNotNullValueImpl<>(expressionOrList, value));
      }
    }

    return Collections.emptyList();
  }
}
