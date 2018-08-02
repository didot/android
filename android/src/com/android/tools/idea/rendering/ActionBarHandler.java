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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.model.MergedManifest.ActivityAttributes;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_SPLIT_ACTION_BAR_WHEN_NARROW;

/**
 * A callback to provide information related to the Action Bar as required by the
 * {@link LayoutLibrary}
 */
public class ActionBarHandler extends ActionBarCallback {
  private static final String ON_CREATE_OPTIONS_MENU = "onCreateOptionsMenu";                   //$NON-NLS-1$
  private static final Pattern MENU_FIELD_PATTERN = Pattern.compile("R\\.menu\\.([a-z0-9_]+)"); //$NON-NLS-1$

  @Nullable private final Object myCredential;
  @NotNull private RenderTask myRenderTask;
  @Nullable private List<ResourceReference> myMenus;
  @Nullable private List<String> myMenuNames;

  ActionBarHandler(@NotNull RenderTask renderTask, @Nullable Object credential) {
    myRenderTask = renderTask;
    myCredential = credential;
  }

  @Override
  public boolean getSplitActionBarWhenNarrow() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null) {
      return VALUE_SPLIT_ACTION_BAR_WHEN_NARROW.equals(attributes.getUiOptions());
    }
    return false;
  }

  // TODO: Handle this per file instead.
  /** Flag which controls whether we should be showing the menu */
  private static boolean ourShowMenu = false;

  public static boolean isShowingMenu(@SuppressWarnings("UnusedParameters") @Nullable EditorDesignSurface surface) {
    return ourShowMenu;
  }

  public static boolean showMenu(boolean showMenu, @Nullable EditorDesignSurface surface, boolean repaint) {
    if (showMenu != ourShowMenu) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourShowMenu = showMenu;
      if (surface != null && repaint) {
        surface.forceUserRequestedRefresh();
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean isOverflowPopupNeeded() {
    return ourShowMenu || myRenderTask.getContext().getFolderType() == ResourceFolderType.MENU;
  }

  @Override
  @Deprecated
  public List<String> getMenuIdNames() {
    if (myMenuNames != null) {
      return myMenuNames;
    }

    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      XmlFile xmlFile = myRenderTask.getXmlFile();
      String commaSeparatedMenus = xmlFile == null ? null : AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_MENU, TOOLS_URI);
      if (commaSeparatedMenus != null) {
        myMenuNames = new ArrayList<>();
        Iterables.addAll(myMenuNames, Splitter.on(',').trimResults().omitEmptyStrings().split(commaSeparatedMenus));
      } else {
        String fqn = xmlFile == null ? null : AndroidPsiUtils.getDeclaredContextFqcn(myRenderTask.getContext().getModule(), xmlFile);
        if (fqn != null) {
          Project project = xmlFile.getProject();
          DumbService.getInstance(project).smartInvokeLater(() -> {
            // Glance at the onCreateOptionsMenu of the associated context and use any menus found there.
            // This is just a simple textual search; we need to replace this with a proper model lookup.
            PsiClass clz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
            if (clz != null) {
              for (PsiMethod method : clz.findMethodsByName(ON_CREATE_OPTIONS_MENU, true)) {
                if (method instanceof PsiCompiledElement) {
                  continue;
                }
                // TODO: This should instead try to use the GotoRelated implementation's notion
                // of associated activities; see what is done in
                // AndroidMissingOnClickHandlerInspection. However, the AndroidGotoRelatedProvider
                // will first need to properly handle menus.
                String matchText = method.getText();
                Matcher matcher = MENU_FIELD_PATTERN.matcher(matchText);
                Set<String> menus = new TreeSet<>();
                int index = 0;
                while (true) {
                  if (matcher.find(index)) {
                    menus.add(matcher.group(1));
                    index = matcher.end();
                  } else {
                    break;
                  }
                }
                if (!menus.isEmpty()) {
                  myMenuNames = new ArrayList<>(menus);
                }
              }
            }
          });
        }
      }

      if (myMenuNames == null) {
        myMenuNames = Collections.emptyList();
      }
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }

    return myMenuNames;
  }

  @Override
  @NotNull
  public List<ResourceReference> getMenuIds() {
    if (myMenus != null) {
      return myMenus;
    }

    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      Module module = myRenderTask.getContext().getModule();
      ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(module);
      if (repositoryManager != null) {
        ResourceNamespace namespace = repositoryManager.getNamespace();
        XmlFile xmlFile = myRenderTask.getXmlFile();
        String commaSeparatedMenus = xmlFile == null ? null : AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_MENU, TOOLS_URI);
        if (commaSeparatedMenus != null) {
          List<String> names = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(commaSeparatedMenus);
          myMenus = new ArrayList<>(names.size());
          for (String name : names) {
            myMenus.add(new ResourceReference(namespace, ResourceType.MENU, name));
          }
        } else {
          String fqn = xmlFile == null ? null : AndroidPsiUtils.getDeclaredContextFqcn(module, xmlFile);
          if (fqn != null) {
            Project project = xmlFile.getProject();
            DumbService.getInstance(project).smartInvokeLater(() -> {
              // Glance at the onCreateOptionsMenu of the associated context and use any menus found there.
              // This is just a simple textual search; we need to replace this with a proper model lookup.
              PsiClass clz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
              if (clz != null) {
                for (PsiMethod method : clz.findMethodsByName(ON_CREATE_OPTIONS_MENU, true)) {
                  if (method instanceof PsiCompiledElement) {
                    continue;
                  }
                  // TODO: This should instead try to use the GotoRelated implementation's notion
                  // of associated activities; see what is done in
                  // AndroidMissingOnClickHandlerInspection. However, the AndroidGotoRelatedProvider
                  // will first need to properly handle menus.
                  String matchText = method.getText();
                  Matcher matcher = MENU_FIELD_PATTERN.matcher(matchText);
                  Set<ResourceReference> menus = new TreeSet<>();
                  int index = 0;
                  while (true) {
                    if (matcher.find(index)) {
                      String name = matcher.group(1);
                      menus.add(new ResourceReference(namespace, ResourceType.MENU, name));
                      index = matcher.end();
                    }
                    else {
                      break;
                    }
                  }
                  if (!menus.isEmpty()) {
                    myMenus = new ArrayList<>(menus);
                  }
                }
              }
            });
          }
        }
      }

      if (myMenus == null) {
        myMenus = Collections.emptyList();
      }
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }

    return myMenus;
  }

  @Override
  public HomeButtonStyle getHomeButtonStyle() {
    ActivityAttributes attributes = getActivityAttributes();
    if (attributes != null && attributes.getParentActivity() != null) {
      return HomeButtonStyle.SHOW_HOME_AS_UP;
    }
    return HomeButtonStyle.NONE;
  }

  @Override
  public int getNavigationMode() {
    XmlFile xmlFile = myRenderTask.getXmlFile();
    String navMode =
        StringUtil.notNullize(xmlFile == null ? null : AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_NAV_MODE, TOOLS_URI)).trim();
    if (navMode.equalsIgnoreCase(VALUE_NAV_MODE_TABS)) {
      return NAVIGATION_MODE_TABS;
    }
    if (navMode.equalsIgnoreCase(VALUE_NAV_MODE_LIST)) {
      return NAVIGATION_MODE_LIST;
    }
    return NAVIGATION_MODE_STANDARD;
  }

  /**
   * If set to null, this searches for the associated menu using tools:context and tools:menu attributes.
   * To set no menu, pass an empty list.
   */
  public void setMenuIdNames(@Nullable List<String> menus) {
    myMenuNames = menus;
  }

  @Nullable
  private ActivityAttributes getActivityAttributes() {
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      MergedManifest manifest = MergedManifest.get(myRenderTask.getContext().getModule());
      String activity = StringUtil.notNullize(myRenderTask.getContext().getConfiguration().getActivity());
      return manifest.getActivityAttributes(activity);
    } finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }
}
