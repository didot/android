/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.ui.Icons;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class AddLocaleAction extends AnAction {
  private final StringResourceTable myTable;
  private final AndroidFacet myFacet;

  AddLocaleAction(@NotNull StringResourceTable table, @NotNull AndroidFacet facet) {
    super("Add Locale", null, Icons.newLayeredIcon(AndroidIcons.Globe, (ScalableIcon)AllIcons.ToolbarDecorator.Add));

    myTable = table;
    myFacet = facet;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    long count = myTable.getModel().getKeys().stream()
      .filter(key -> key.getDirectory() != null)
      .count();

    event.getPresentation().setEnabled(count != 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    StringResourceData data = myTable.getData();
    assert data != null;

    JList list = new LocaleList(getLocales(data.getLocaleSet()));

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setItemChoosenCallback(() -> createItem((Locale)list.getSelectedValue()))
      .createPopup();

    popup.showUnderneathOf(event.getInputEvent().getComponent());
  }

  @NotNull
  @VisibleForTesting
  static Collection<Locale> getLocales(@NotNull Collection<Locale> localesToRemove) {
    return LocaleManager.getLanguageCodes(true).stream()
      .flatMap(AddLocaleAction::getLocales)
      .filter(locale -> !localesToRemove.contains(locale))
      .sorted(Locale.LANGUAGE_NAME_COMPARATOR)
      .collect(Collectors.toList());
  }

  @NotNull
  private static Stream<Locale> getLocales(@NotNull String language) {
    return LocaleManager.getRelevantRegions(language).stream()
      .map(region -> Locale.create(new LocaleQualifier(null, language, region, null)));
  }

  private void createItem(@NotNull Locale locale) {
    StringResource resource = findResource();
    StringResourceKey key = resource.getKey();

    VirtualFile directory = key.getDirectory();
    assert directory != null;

    StringsWriteUtils.createItem(myFacet, directory, locale, key.getName(), resource.getDefaultValueAsString(), true);
  }

  @NotNull
  private StringResource findResource() {
    StringResourceData data = myTable.getData();
    assert data != null;

    StringResourceKey key = new StringResourceKey("app_name", myFacet.getAllResourceDirectories().get(0));

    if (data.containsKey(key)) {
      return data.getStringResource(key);
    }

    Optional<StringResource> optionalResource = data.getResources().stream()
      .filter(resource -> resource.getKey().getDirectory() != null)
      .findFirst();

    return optionalResource.orElseThrow(IllegalStateException::new);
  }
}
