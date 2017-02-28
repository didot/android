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
package com.android.tools.idea.editors.strings;

import com.android.SdkConstants;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class StringResourceData {
  private final AndroidFacet myFacet;
  private final Map<StringResourceKey, StringResource> myKeyToResourceMap;

  public StringResourceData(@NotNull AndroidFacet facet,
                            @NotNull Map<StringResourceKey, StringResource> keyToResourceMap) {
    myFacet = facet;
    myKeyToResourceMap = keyToResourceMap;
  }

  public void changeKeyName(@NotNull StringResourceKey key, @NotNull String newName) {
    ResourceItem res = getStringResource(key).getDefaultValueAsResourceItem();
    if (res == null) return; // String does not exist in the default locale.
    XmlTag tag = LocalResourceRepository.getItemTag(myFacet.getModule().getProject(), res);
    assert tag != null;
    XmlAttribute name = tag.getAttribute(SdkConstants.ATTR_NAME);
    assert name != null;
    XmlAttributeValue nameValue = name.getValueElement();
    assert nameValue != null;
    BaseRefactoringProcessor rename = new RenameProcessor(myFacet.getModule().getProject(), nameValue, newName, false, false);
    ApplicationManager.getApplication().invokeLater(rename);
  }

  public boolean setTranslatable(@NotNull StringResourceKey key, boolean translatable) {
    StringResource stringResource = getStringResource(key);
    ResourceItem item = stringResource.getDefaultValueAsResourceItem();
    if (item != null) {
      String translatableAsString;
      if (translatable) {
        translatableAsString = null;
        stringResource.setTranslatable(true);
      }
      else {
        translatableAsString = SdkConstants.VALUE_FALSE;
        stringResource.setTranslatable(false);
      }

      return StringsWriteUtils.setAttributeForItems(myFacet.getModule().getProject(), SdkConstants.ATTR_TRANSLATABLE, translatableAsString,
                                                    Collections.singletonList(item));
    }
    return false;
  }

  public boolean setDefaultValue(@NotNull StringResourceKey key, @NotNull String value) {
    StringResource resource = getStringResource(key);

    if (resource.getDefaultValueAsResourceItem() == null) {
      ResourceItem item = createItem(key, null, value);

      if (item == null) {
        return false;
      }

      resource.setDefaultValue(item, value);
      return true;
    }

    return setDefaultValueItemText(key, value);
  }

  private boolean setDefaultValueItemText(@NotNull StringResourceKey key, @NotNull String value) {
    StringResource resource = getStringResource(key);

    if (resource.getDefaultValueAsString().equals(value)) {
      return false;
    }

    ResourceItem item = resource.getDefaultValueAsResourceItem();
    assert item != null;

    boolean changed = StringsWriteUtils.setItemText(myFacet.getModule().getProject(), item, value);

    if (!changed) {
      return false;
    }

    if (value.isEmpty()) {
      resource.removeDefaultValue();
    }
    else {
      resource.setDefaultValue(item, value);
    }

    return true;
  }

  public boolean setTranslation(@NotNull StringResourceKey key, @NotNull Locale locale, @NotNull String value) {
    StringResource resource = getStringResource(key);

    if (resource.getTranslationAsResourceItem(locale) == null) {
      ResourceItem item = createItem(key, locale, value);

      if (item == null) {
        return false;
      }

      resource.putTranslation(locale, item, value);
      return true;
    }

    return setTranslationItemText(key, locale, value);
  }

  private boolean setTranslationItemText(@NotNull StringResourceKey key, @NotNull Locale locale, @NotNull String value) {
    StringResource resource = getStringResource(key);

    if (resource.getTranslationAsString(locale).equals(value)) {
      return false;
    }

    ResourceItem item = resource.getTranslationAsResourceItem(locale);
    assert item != null;

    boolean changed = StringsWriteUtils.setItemText(myFacet.getModule().getProject(), item, value);

    if (!changed) {
      return false;
    }

    if (value.isEmpty()) {
      resource.removeTranslation(locale);
    }
    else {
      resource.putTranslation(locale, item, value);
    }

    return true;
  }

  @Nullable
  private ResourceItem createItem(@NotNull StringResourceKey key, @Nullable Locale locale, @NotNull String value) {
    VirtualFile directory = key.getDirectory();

    if (directory == null) {
      return null;
    }

    return StringsWriteUtils.createItem(myFacet, directory, locale, key.getName(), value, getStringResource(key).isTranslatable());
  }

  @Nullable
  public String validateKey(@NotNull StringResourceKey key) {
    if (!myKeyToResourceMap.keySet().contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    StringResource stringResource = getStringResource(key);
    if (!stringResource.isTranslatable()) {
      Collection<Locale> localesWithTranslation = stringResource.getTranslatedLocales();
      if (!localesWithTranslation.isEmpty()) {
        return String.format("Key '%1$s' is marked as non translatable, but is translated in %2$s %3$s", key.getName(),
                             StringUtil.pluralize("locale", localesWithTranslation.size()), summarizeLocales(localesWithTranslation));
      }
    }
    else { // translatable key
      if (stringResource.getDefaultValueAsResourceItem() == null) {
        return "Key '" + key.getName() + "' missing default value";
      }

      Collection<Locale> missingTranslations = getMissingTranslations(key);
      if (!missingTranslations.isEmpty()) {
        return String.format("Key '%1$s' has translations missing for %2$s %3$s", key.getName(),
                             StringUtil.pluralize("locale", missingTranslations.size()), summarizeLocales(missingTranslations));
      }
    }
    return null;
  }

  @Nullable
  public String validateTranslation(@NotNull StringResourceKey key, @Nullable Locale locale) {
    if (!myKeyToResourceMap.keySet().contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    StringResource stringResource = getStringResource(key);

    if (locale == null) {
      ResourceItem item = stringResource.getDefaultValueAsResourceItem();
      return (item == null) ? String.format("Key '%1$s' is missing the default value", key.getName()) : null;
    }

    final boolean translationMissing = stringResource.isTranslationMissing(locale);
    final boolean doNotTranslate = !stringResource.isTranslatable();
    if (translationMissing && !doNotTranslate) {
      return String.format("Key '%1$s' is missing %2$s translation", key.getName(), getLabel(locale));
    }
    else if (doNotTranslate && !translationMissing) {
      return "Key '" + key.getName() + "' is marked as untranslatable and should not be translated to " + getLabel(locale);
    }
    return null;
  }

  @NotNull
  @VisibleForTesting
  Collection<Locale> getMissingTranslations(@NotNull StringResourceKey key) {
    Set<Locale> missingTranslations = Sets.newHashSet();
    for (Locale locale : getLocales()) {
      StringResource stringResource = getStringResource(key);
      if (stringResource.isTranslationMissing(locale)) {
        missingTranslations.add(locale);
      }
    }

    return missingTranslations;
  }

  @VisibleForTesting
  @NotNull
  static String summarizeLocales(@NotNull Collection<Locale> locales) {
    if (locales.isEmpty()) {
      return "";
    }

    final int size = locales.size();

    if (size == 1) {
      return getLabel(Iterables.getFirst(locales, null));
    }

    final int max = 3;
    List<Locale> sorted = getLowest(locales, max);
    if (size <= max) {
      return getLabels(sorted.subList(0, size - 1)) + " and " + getLabel(sorted.get(size - 1));
    }
    else {
      return String.format("%1$s and %2$d more", getLabels(sorted), size - max);
    }
  }

  private static List<Locale> getLowest(Collection<Locale> locales, int n) {
    return locales.stream()
      .limit(n)
      .sorted(Comparator.comparing(StringResourceData::getLabel))
      .collect(Collectors.toList());
  }

  private static String getLabels(Collection<Locale> locales) {
    return locales.stream()
      .map(StringResourceData::getLabel)
      .collect(Collectors.joining(", "));
  }

  private static String getLabel(@Nullable Locale locale) {
    return locale == null ? "" : LocaleMenuAction.getLocaleLabel(locale, false);
  }

  boolean containsKey(@NotNull StringResourceKey key) {
    return myKeyToResourceMap.containsKey(key);
  }

  @NotNull
  public StringResource getStringResource(@NotNull StringResourceKey key) {
    StringResource resource = myKeyToResourceMap.get(key);

    if (resource == null) {
      throw new IllegalArgumentException(key.toString());
    }

    return resource;
  }

  @NotNull
  public Collection<StringResource> getResources() {
    return myKeyToResourceMap.values();
  }

  @NotNull
  public List<StringResourceKey> getKeys() {
    return new ArrayList<>(myKeyToResourceMap.keySet());
  }

  @NotNull
  public List<Locale> getLocales() {
    Set<Locale> locales = new TreeSet<>(Locale.LANGUAGE_CODE_COMPARATOR);
    for (StringResource stringResource : myKeyToResourceMap.values()) {
      locales.addAll(stringResource.getTranslatedLocales());
    }
    return new ArrayList<>(locales);
  }
}
