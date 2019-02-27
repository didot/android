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

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.CLASS_PREFERENCE_ANDROIDX;

import com.android.SdkConstants;
import com.android.tools.idea.dom.xml.PathsDomFileDescription;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.AttributeProcessingUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.LayoutViewClassUtils;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AndroidXmlResourcesUtil {
  @NonNls public static final String SEARCHABLE_TAG_NAME = "searchable";
  @NonNls public static final String KEYBOARD_TAG_NAME = "Keyboard";
  @NonNls public static final String DEVICE_ADMIN_TAG_NAME = "device-admin";
  @NonNls public static final String ACCOUNT_AUTHENTICATOR_TAG_NAME = "account-authenticator";
  @NonNls public static final String PREFERENCE_HEADERS_TAG_NAME = "preference-headers";

  public static final ImmutableMap<String, String> SPECIAL_STYLEABLE_NAMES = ImmutableMap.<String, String>builder()
    .put(SdkConstants.TAG_APPWIDGET_PROVIDER, "AppWidgetProviderInfo")
    .put(SEARCHABLE_TAG_NAME, "Searchable")
    .put("actionkey", "SearchableActionKey")
    .put("intent", "Intent")
    .put(KEYBOARD_TAG_NAME, "Keyboard")
    .put("Row", "Keyboard_Row")
    .put("Key", "Keyboard_Key")
    .put(DEVICE_ADMIN_TAG_NAME, "DeviceAdmin")
    .put(ACCOUNT_AUTHENTICATOR_TAG_NAME, "AccountAuthenticator")
    .put("header", "PreferenceHeader")
    .build();

  private static final ImmutableSet<String> ROOT_TAGS = ImmutableSet.of(
    SdkConstants.TAG_APPWIDGET_PROVIDER, SEARCHABLE_TAG_NAME, KEYBOARD_TAG_NAME, DEVICE_ADMIN_TAG_NAME, ACCOUNT_AUTHENTICATOR_TAG_NAME,
    PREFERENCE_HEADERS_TAG_NAME, PathsDomFileDescription.TAG_NAME);

  private AndroidXmlResourcesUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    List<String> result = new ArrayList<>();

    String preferencesLibName = MigrateToAndroidxUtil.getNameInProject(CLASS_PREFERENCE_ANDROIDX, facet.getModule().getProject());
    boolean hasAndroidXClass = JavaPsiFacade.getInstance(facet.getModule().getProject())
                                 .findClass(preferencesLibName, facet.getModule().getModuleWithLibrariesScope()) != null;
    if (hasAndroidXClass) {
      result.addAll(AndroidDomUtil.removeUnambiguousNames(AttributeProcessingUtil.getAndroidXPreferencesClassMap(facet)));
    }
    else {
      result.addAll(AndroidDomUtil.removeUnambiguousNames(AttributeProcessingUtil.getFrameworkPreferencesClassMap(facet)));
    }
    result.addAll(ROOT_TAGS);

    return result;
  }

  public static boolean isSupportedRootTag(@NotNull AndroidFacet facet, @NotNull String rootTagName) {
    return ROOT_TAGS.contains(rootTagName) ||
           LayoutViewClassUtils.findClassByTagName(facet, rootTagName, SdkConstants.CLASS_PREFERENCE) != null;
  }

  public static boolean isAndroidXPreferenceFile(@NotNull XmlTag tag, @NotNull AndroidFacet facet) {
    XmlTag rootTag = ((XmlFile)tag.getContainingFile()).getRootTag();
    if (rootTag == null) {
      return false;
    }
    String rootTagName = rootTag.getName();
    if (rootTagName.startsWith(ANDROIDX_PKG_PREFIX)) {
      return true;
    }
    else if (rootTagName.startsWith(ANDROID_PKG_PREFIX)) {
      return false;
    }
    else if (rootTagName.startsWith("android.support.v") && StringUtil.getPackageName(rootTagName).endsWith("preference")) {
      return true;
    }
    Project project = facet.getModule().getProject();
    String preferencesLibName = MigrateToAndroidxUtil.getNameInProject(CLASS_PREFERENCE_ANDROIDX, project);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    if (psiFacade.findClass(preferencesLibName, rootTag.getResolveScope()) == null) {
      return false;
    }
    PsiJavaParserFacade parser = psiFacade.getParserFacade();
    try {
      PsiType type = parser.createTypeFromText(rootTagName, null);
      if (type instanceof PsiClassType && ((PsiClassType)type).resolve() != null) {
        if (InheritanceUtil.isInheritor(type, preferencesLibName)) {
          return true;
        }
        return false;
      }
    } catch (IncorrectOperationException e) {
      // When the root tag does not specify a valid type but is in a AndroidX project. eg. <preference-headers>, since AndroidX is being
      // used we assume this is an AndroidX file.
      return true;
    }
    // The root tag is an unqualified name (eg. PreferenceScreen), if AndroidX is being used then we assume that AndroidX classes
    // are being used.
    return true;
  }
}
