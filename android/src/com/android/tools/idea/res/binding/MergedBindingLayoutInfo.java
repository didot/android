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
package com.android.tools.idea.res.binding;

import com.android.ide.common.resources.DataBindingResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Binding info merged from multiple BindingLayoutInfo instances from different configurations.
 */
public class MergedBindingLayoutInfo implements BindingLayoutInfo {
  @NotNull private final List<DefaultBindingLayoutInfo> myInfoList;
  @NotNull private final DefaultBindingLayoutInfo myBaseInfo;

  private PsiClass myPsiClass;

  @NotNull private final CachedValue<Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>>> myResourceItemCache;

  public MergedBindingLayoutInfo(@NotNull List<DefaultBindingLayoutInfo> infoList) {
    myInfoList = infoList;
    myBaseInfo = selectBaseInfo();
    CachedValuesManager cacheManager = CachedValuesManager.getManager(myBaseInfo.getProject());

    myResourceItemCache = cacheManager.createCachedValue(() -> {
      Map<DataBindingResourceType, Map<String, PsiDataBindingResourceItem>> result = new EnumMap<>(DataBindingResourceType.class);
      for (BindingLayoutInfo info : myInfoList) {
        for (DataBindingResourceType type : DataBindingResourceType.values()) {
          Map<String, PsiDataBindingResourceItem> itemsByName = info.getItems(type);
          for (Map.Entry<String, PsiDataBindingResourceItem> entry : itemsByName.entrySet()) {
            Map<String, PsiDataBindingResourceItem> resultItemsByName = result.computeIfAbsent(type, t -> new HashMap<>());
            String name = entry.getKey();
            PsiDataBindingResourceItem item = entry.getValue();
            resultItemsByName.putIfAbsent(name, item);
          }
        }
      }
      return CachedValueProvider.Result.create(result, myInfoList);
    }, false);
  }

  @SuppressWarnings("ConstantConditions")
  public DefaultBindingLayoutInfo selectBaseInfo() {
    DefaultBindingLayoutInfo best = null;
    for (DefaultBindingLayoutInfo info : myInfoList) {
      if (best == null ||
          best.getConfigurationName().length() > info.getConfigurationName().length()) {
        best = info;
      }
    }
    return best;
  }

  @Override
  public AndroidFacet getFacet() {
    return myBaseInfo.getFacet();
  }

  @Override
  public String getClassName() {
    return myBaseInfo.getNonConfigurationClassName();
  }

  @Override
  public String getPackageName() {
    return myBaseInfo.getPackageName();
  }

  @Override
  public Project getProject() {
    return myBaseInfo.getProject();
  }

  @Override
  public String getQualifiedName() {
    return myBaseInfo.getPackageName() + "." + getClassName();
  }

  @Override
  public PsiElement getNavigationElement() {
    return myBaseInfo.getNavigationElement();
  }

  @Override
  public PsiFile getPsiFile() {
    return myBaseInfo.getPsiFile();
  }

  @Override
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public void setPsiClass(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  @Override
  @NotNull
  public Map<String, PsiDataBindingResourceItem> getItems(@NotNull DataBindingResourceType type) {
    Map<String, PsiDataBindingResourceItem> itemsByName = myResourceItemCache.getValue().get(type);
    return itemsByName == null ? Collections.emptyMap() : itemsByName;
  }

  @Override
  @Nullable
  public Module getModule() {
    return myBaseInfo.getModule();
  }

  @Override
  public long getModificationCount() {
    int total = myInfoList.size();
    for (BindingLayoutInfo info : myInfoList) {
      total += info.getModificationCount();
    }
    return total;
  }

  @NotNull
  @Override
  public LayoutType getLayoutType() {
    return myBaseInfo.getLayoutType();
  }

  @Override
  public boolean isMerged() {
    return true;
  }

  @Override
  @Nullable
  public BindingLayoutInfo getMergedInfo() {
    return null;
  }

  @NotNull
  public List<DefaultBindingLayoutInfo> getInfos() {
    return myInfoList;
  }
}
