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
package com.android.tools.idea.gradle.dsl.model.repositories;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.repositories.FlatDirRepositoryModel.FLAT_DIR_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.*;
import static com.android.tools.idea.gradle.dsl.model.repositories.JCenterDefaultRepositoryModel.JCENTER_METHOD_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.MavenCentralRepositoryModel.MAVEN_CENTRAL_METHOD_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.JCENTER_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.MAVEN_BLOCK_NAME;

public class RepositoriesModelImpl extends GradleDslBlockModel implements RepositoriesModel {
  public RepositoriesModelImpl(@NotNull RepositoriesDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  @Override
  public List<RepositoryModel> repositories() {
    List<RepositoryModel> result = Lists.newArrayList();
    for (GradleDslElement element : myDslElement.getAllPropertyElements()) {
      if (element instanceof GradleDslMethodCall) {
        String methodName = ((GradleDslMethodCall)element).getMethodName();
        if (MAVEN_CENTRAL_METHOD_NAME.equals(methodName)) {
          result.add(new MavenCentralRepositoryModel(myDslElement, element));
        }
        else if (JCENTER_METHOD_NAME.equals(methodName)) {
          result.add(new JCenterDefaultRepositoryModel(myDslElement, element));
        }
        else if (GOOGLE_METHOD_NAME.equals(methodName)) {
          result.add(new GoogleDefaultRepositoryModelImpl(myDslElement, element));
        }
      }
      else if (element instanceof MavenRepositoryDslElement) {
        if (MAVEN_BLOCK_NAME.equals(element.getName())) {
          result.add(new MavenRepositoryModelImpl(myDslElement, (MavenRepositoryDslElement)element));
        }
        else if (JCENTER_BLOCK_NAME.equals(element.getName())) {
          result.add(new JCenterRepositoryModel(myDslElement, (MavenRepositoryDslElement)element));
        }
      }
      else if (element instanceof FlatDirRepositoryDslElement) {
        result.add(new FlatDirRepositoryModel(myDslElement, (FlatDirRepositoryDslElement)element));
      }
      else if (element instanceof GradleDslExpressionMap) {
        if (MAVEN_CENTRAL_METHOD_NAME.equals(element.getName())) {
          result.add(new MavenCentralRepositoryModel(myDslElement, element));
        }
        else if (FLAT_DIR_ATTRIBUTE_NAME.equals(element.getName())) {
          result.add(new FlatDirRepositoryModel(myDslElement ,(GradlePropertiesDslElement)element));
        }
      }
    }
    return result;
  }

  /**
   * Adds a repository by method name if it is not already in the list of repositories.
   *
   * @param methodName Name of method to call.
   */
  @Override
  public void addRepositoryByMethodName(@NotNull String methodName) {
    // Check if it is already there
    if (containsMethodCall(methodName)) {
      return;
    }
    myDslElement.setNewElement(new GradleDslMethodCall(myDslElement, GradleNameElement.empty(), methodName));
  }

  /**
   * Adds a flat directory repository if it is not already in the list of repositories.
   *
   * @param dirName Directory to add
   */
  @Override
  public void addFlatDirRepository(@NotNull String dirName) {
    List<FlatDirRepositoryDslElement> flatDirElements = myDslElement.getPropertyElements(FlatDirRepositoryDslElement.class);
    if (!flatDirElements.isEmpty()) {
      // A repository already exists
      new FlatDirRepositoryModel(myDslElement, flatDirElements.get(0)).dirs().addListValue().setValue(dirName);
    }
    else {
      // We need to create one
      GradlePropertiesDslElement gradleDslElement = new FlatDirRepositoryDslElement(myDslElement);
      myDslElement.setNewElement(gradleDslElement);
      new FlatDirRepositoryModel(myDslElement, gradleDslElement).dirs().addListValue().setValue(dirName);
    }
  }

  /**
   * Looks for a repository by method name.
   *
   * @param methodName Method name of the repository
   * @return {@code true} if there is a call to {@code methodName}, {@code false} other wise.
   */
  @Override
  public boolean containsMethodCall(@NotNull String methodName) {
    List<GradleDslMethodCall> elements = myDslElement.getPropertyElements(GradleDslMethodCall.class);
    for (GradleDslMethodCall element : elements) {
      if (methodName.equals(element.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds a repository by url if it is not already in the list of repositories.
   *
   * @param url address to use.
   */
  @Override
  public void addMavenRepositoryByUrl(@NotNull String url, @NotNull String name) {
    // Check if it is already there
    if (containsMavenRepositoryByUrl(url)) {
      return;
    }
    GradleNameElement nameElement = GradleNameElement.create(MAVEN_BLOCK_NAME);
    MavenRepositoryDslElement newElement = new MavenRepositoryDslElement(myDslElement, nameElement);
    newElement.setNewLiteral("url", url);
    // name is an optional property, it can be nullable but at this point only non null values are used.
    newElement.setNewLiteral("name", name);
    myDslElement.setNewElement(newElement);
  }

  /**
   * Looks for a repository by URL.
   *
   * @param repositoryUrl the URL of the repository to find.
   * @return {@code true} if there is a repository using {@code repositoryUrl} as URL, {@code false} otherwise.
   */
  @Override
  public boolean containsMavenRepositoryByUrl(@NotNull String repositoryUrl) {
    List<MavenRepositoryDslElement> elements = myDslElement.getPropertyElements(MavenRepositoryDslElement.class);
    for (MavenRepositoryDslElement element : elements) {
      String urlElement = element.getLiteralProperty("url", String.class).value();
      if (repositoryUrl.equals(urlElement)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Look for Google Maven repository. If Gradle version is 4 or newer, look for it by method call and url.
   * If it is lower than 4, look only by url.
   *
   * @return {@code true} if Google Maven repository can be found in {@code repositoriesModel}, {@code false} otherwise.
   */
  @Override
  public boolean hasGoogleMavenRepository() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) {
      // No psiElement means that there is no repository block
      return false;
    }
    Project project = getPsiElement().getProject();
    if (GradleVersions.getInstance().isGradle4OrNewer(project) && containsMethodCall(GOOGLE_METHOD_NAME)) {
      // google repository by method can only be used in gradle 4.0+
      return true;
    }
    return containsMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL);
  }

  @Override
  public void addGoogleMavenRepository(@NotNull Project project) {
    if (GradleVersions.getInstance().isGradle4OrNewer(project)) {
      addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    }
    else {
      addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    }
  }

  @Override
  public void addGoogleMavenRepository(@NotNull GradleVersion gradleVersion) {
    if (gradleVersion.compareIgnoringQualifiers("4.0") >= 0) {
      addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    }
    else {
      addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    }
  }
}
