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

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.res.StringResourceWriter;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import icons.StudioIcons;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JList;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

final class AddLocaleAction extends AnAction {
  private final StringResourceViewPanel myPanel;

  AddLocaleAction(@NotNull StringResourceViewPanel panel) {
    super("Add Locale", null, StudioIcons.LayoutEditor.Toolbar.ADD_LOCALE);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    long count = myPanel.getTable().getModel().getKeys().stream()
      .filter(key -> key.getDirectory() != null)
      .count();

    event.getPresentation().setEnabled(count != 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    StringResourceData data = myPanel.getTable().getData();
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
    Stream<Locale> regionStream = LocaleManager.getRelevantRegions(language).stream()
      .map(region -> Locale.create(new LocaleQualifier(null, language, region, null)));

    return Stream.concat(Stream.of(createLocale(language)), regionStream);
  }

  @NotNull
  private static Locale createLocale(@NotNull String language) {
    String full = language.length() == 2 ? language : LocaleQualifier.BCP_47_PREFIX + language;
    return Locale.create(new LocaleQualifier(full, language, null, null));
  }

  @VisibleForTesting
  void createItem(@NotNull Locale locale) {
    Project project = myPanel.getFacet().getModule().getProject();
    StringResourceData data = myPanel.getTable().getData();
    assert data != null;
    StringResourceKey key = findResourceKey(data);

    VirtualFile directory = key.getDirectory();
    if (directory == null) {
      return;
    }
    XmlFile file = StringResourceWriter.INSTANCE.getStringResourceFile(project, directory, locale);

    if (file == null) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(project, () -> {
      StringPsiUtils.addString(file, key, data.getStringResource(key).getDefaultValueAsString());
      myPanel.reloadData();
    });
  }

  @NotNull
  private StringResourceKey findResourceKey(@NotNull StringResourceData data) {
    List<VirtualFile> folders = ResourceFolderManager.getInstance(myPanel.getFacet()).getFolders();

    if (!folders.isEmpty()) {
      StringResourceKey key = new StringResourceKey("app_name", folders.get(0));

      if (data.containsKey(key)) {
        return key;
      }
    }

    return
      data.getKeys().stream().filter(k -> k.getDirectory() != null).findFirst().orElseThrow(IllegalStateException::new);
  }
}
