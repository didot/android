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
package org.jetbrains.android.sdk;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.LayoutLibraryLoader;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.FrameworkResourceRepository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeDefinitionsImpl;
import org.jetbrains.android.resourceManagers.FilteredAttributeDefinitions;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTargetData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidTargetData");

  private final AndroidSdkData mySdkData;
  private final IAndroidTarget myTarget;

  private final Object myAttrDefsLock = new Object();
  @GuardedBy("myAttrDefsLock")
  private AttributeDefinitions myAttrDefs;

  private volatile LayoutLibrary myLayoutLibrary;

  private final Object myPublicResourceCacheLock = new Object();
  @GuardedBy("myPublicResourceCacheLock")
  private volatile Map<String, Set<String>> myPublicResourceCache;
  @GuardedBy("myPublicResourceCacheLock")
  private TIntObjectHashMap<String> myPublicResourceIdMap;

  private volatile MyStaticConstantsData myStaticConstantsData;
  private FrameworkResourceRepository myFrameworkResources;

  public AndroidTargetData(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  /**
   * Filters attributes through the public.xml file
   */
  @Nullable
  public AttributeDefinitions getPublicAttrDefs(@NotNull Project project) {
    AttributeDefinitions attrDefs = getAllAttrDefs(project);
    return attrDefs == null ? null : new PublicAttributeDefinitions(attrDefs);
  }

  /**
   * Returns all attributes
   */
  @Nullable
  public AttributeDefinitions getAllAttrDefs(@NotNull Project project) {
    synchronized (myAttrDefsLock) {
      if (myAttrDefs == null) {
        String attrsPath = FileUtil.toSystemIndependentName(myTarget.getPath(IAndroidTarget.ATTRIBUTES));
        String attrsManifestPath = FileUtil.toSystemIndependentName(myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES));
        myAttrDefs = AttributeDefinitionsImpl.parseFrameworkFiles(new File(attrsPath), new File(attrsManifestPath));
      }
      return myAttrDefs;
    }
  }

  @Nullable
  private Map<String, Set<String>> getPublicResourceCache() {
    synchronized (myPublicResourceCacheLock) {
      if (myPublicResourceCache == null) {
        parsePublicResCache();
      }
      return myPublicResourceCache;
    }
  }

  @Nullable
  public TIntObjectHashMap<String> getPublicIdMap() {
    synchronized (myPublicResourceCacheLock) {
      if (myPublicResourceIdMap == null) {
        parsePublicResCache();
      }
      return myPublicResourceIdMap;
    }
  }

  public boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    Map<String, Set<String>> publicResourceCache = getPublicResourceCache();

    if (publicResourceCache == null) {
      return false;
    }
    Set<String> set = publicResourceCache.get(type);
    return set != null && set.contains(name);
  }

  private void parsePublicResCache() {
    String resDirPath = myTarget.getPath(IAndroidTarget.RESOURCES);
    String publicXmlPath = resDirPath + '/' + SdkConstants.FD_RES_VALUES + "/public.xml";
    VirtualFile publicXml = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(publicXmlPath));

    if (publicXml != null) {
      try {
        MyPublicResourceCacheBuilder builder = new MyPublicResourceCacheBuilder();
        NanoXmlUtil.parse(publicXml.getInputStream(), builder);

        synchronized (myPublicResourceCacheLock) {
          myPublicResourceCache = builder.getPublicResourceCache();
          myPublicResourceIdMap = builder.getIdMap();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  public synchronized LayoutLibrary getLayoutLibrary(@NotNull Project project) throws RenderingException, IOException {
    if (myLayoutLibrary == null) {
      if (myTarget instanceof CompatibilityRenderTarget) {
        IAndroidTarget target = ((CompatibilityRenderTarget)myTarget).getRenderTarget();
        AndroidTargetData targetData = mySdkData.getTargetData(target);
        if (targetData != this) {
          myLayoutLibrary = targetData.getLayoutLibrary(project);
          return myLayoutLibrary;
        }
      }

      if (!(myTarget instanceof StudioEmbeddedRenderTarget)) {
        LOG.warn("Rendering will not use the StudioEmbeddedRenderTarget");
      }
      myLayoutLibrary = LayoutLibraryLoader.load(myTarget, getFrameworkEnumValues());
    }

    return myLayoutLibrary;
  }

  /**
   * The keys of the returned map are attr names. The values are maps defining numerical values of the corresponding enums or flags.
   */
  @NotNull
  private Map<String, Map<String, Integer>> getFrameworkEnumValues() {
    AbstractResourceRepository resources = getFrameworkResources(false);
    if (resources == null) {
      return Collections.emptyMap();
    }

    Map<String, Map<String, Integer>> result = new HashMap<>();
    List<ResourceItem> items = resources.getResourceItems(ResourceNamespace.ANDROID, ResourceType.ATTR);
    for (ResourceItem item: items) {
      ResourceValue attr = item.getResourceValue();
      if (attr instanceof AttrResourceValue) {
        Map<String, Integer> valueMap = ((AttrResourceValue)attr).getAttributeValues();
        if (valueMap != null && !valueMap.isEmpty()) {
          result.put(attr.getName(), valueMap);
        }
      }
    }

    items = resources.getResourceItems(ResourceNamespace.ANDROID, ResourceType.STYLEABLE);
    for (ResourceItem item: items) {
      ResourceValue styleable = item.getResourceValue();
      if (styleable instanceof StyleableResourceValue) {
        List<AttrResourceValue> attrs = ((StyleableResourceValue)styleable).getAllAttributes();
        for (AttrResourceValue attr: attrs) {
          Map<String, Integer> valueMap = attr.getAttributeValues();
          if (valueMap != null && !valueMap.isEmpty()) {
            result.put(attr.getName(), valueMap);
          }
        }
      }
    }
    return result;
  }

  public void clearLayoutBitmapCache(Module module) {
    if (myLayoutLibrary != null) {
      myLayoutLibrary.clearCaches(module);
    }
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @NotNull
  public synchronized MyStaticConstantsData getStaticConstantsData() {
    if (myStaticConstantsData == null) {
      myStaticConstantsData = new MyStaticConstantsData();
    }
    return myStaticConstantsData;
  }

  @Nullable
  public synchronized AbstractResourceRepository getFrameworkResources(boolean withLocale) {
    // If the framework resources that we got was created by someone else who didn't need locale data.
    if (myFrameworkResources != null && withLocale && !myFrameworkResources.isWithLocaleResources()) {
      myFrameworkResources = null;
    }
    if (myFrameworkResources == null) {
      File resFolder = myTarget.getFile(IAndroidTarget.RESOURCES);
      if (!resFolder.isDirectory()) {
        LOG.error(AndroidBundle.message("android.directory.cannot.be.found.error", resFolder.getPath()));
        return null;
      }

      myFrameworkResources = FrameworkResourceRepository.create(resFolder, withLocale, true);
    }
    return myFrameworkResources;
  }

  /**
   * This method can return null when the user is changing the SDK setting in their project.
   */
  @Nullable
  public static AndroidTargetData getTargetData(@NotNull IAndroidTarget target, @NotNull Module module) {
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    return platform != null ? platform.getSdkData().getTargetData(target) : null;
  }

  private class PublicAttributeDefinitions extends FilteredAttributeDefinitions {
    protected PublicAttributeDefinitions(@NotNull AttributeDefinitions wrappee) {
      super(wrappee);
    }

    @Override
    protected boolean isAttributeAcceptable(@NotNull ResourceReference attr) {
      return attr.getNamespace().equals(ResourceNamespace.ANDROID) && isResourcePublic(ResourceType.ATTR.getName(), attr.getName());
    }
  }

  @VisibleForTesting
  static class MyPublicResourceCacheBuilder extends NanoXmlUtil.IXMLBuilderAdapter {
    private final Map<String, Set<String>> myResult = new HashMap<>();
    private final TIntObjectHashMap<String> myIdMap = new TIntObjectHashMap<>(3000);

    private String myName;
    private String myType;
    private int myId;
    private boolean inGroup;

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) {
      if ("public".equals(name) && myName != null && myType != null) {
        Set<String> set = myResult.get(myType);

        if (set == null) {
          set = new HashSet<>();
          myResult.put(myType, set);
        }
        set.add(myName);

        if (myId != 0) {
          myIdMap.put(myId, SdkConstants.ANDROID_PREFIX + myType + "/" + myName);

          // Within <public-group> we increase the id based on a given first id.
          if (inGroup) {
            myId++;
          }
        }
      }
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) {
      switch (key) {
        case "name":
          myName = value;
          break;
        case "type":
          myType = value;
          break;
        case "first-id":
        case "id":
          try {
            myId = Integer.decode(value);
          } catch (NumberFormatException e) {
            myId = 0;
          }
          break;
      }
    }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
      if (!inGroup) {
        // This is a top-level <attr> so clear myType and myId
        myType = null;
        myId = 0;
      }

      if ("public-group".equals(name)) {
        inGroup = true;
      }

      myName = null;
    }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) {
      if ("public-group".equals(name)) {
        inGroup = false;
      }
    }

    public Map<String, Set<String>> getPublicResourceCache() {
      return myResult;
    }

    public TIntObjectHashMap<String> getIdMap() {
      return myIdMap;
    }
  }

  public class MyStaticConstantsData {
    private final Set<String> myActivityActions;
    private final Set<String> myServiceActions;
    private final Set<String> myReceiverActions;
    private final Set<String> myCategories;

    private MyStaticConstantsData() {
      myActivityActions = collectValues(IAndroidTarget.ACTIONS_ACTIVITY);
      myServiceActions = collectValues(IAndroidTarget.ACTIONS_SERVICE);
      myReceiverActions = collectValues(IAndroidTarget.ACTIONS_BROADCAST);
      myCategories = collectValues(IAndroidTarget.CATEGORIES);
    }

    @Nullable
    public Set<String> getActivityActions() {
      return myActivityActions;
    }

    @Nullable
    public Set<String> getServiceActions() {
      return myServiceActions;
    }

    @Nullable
    public Set<String> getReceiverActions() {
      return myReceiverActions;
    }

    @Nullable
    public Set<String> getCategories() {
      return myCategories;
    }

    @Nullable
    private Set<String> collectValues(int pathId) {
      try (BufferedReader reader = new BufferedReader(new FileReader(myTarget.getPath(pathId)))) {
        Set<String> result = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();

          if (!line.isEmpty() && !line.startsWith("#")) {
            result.add(line);
          }
        }
        return result;
      }
      catch (IOException e) {
        return null;
      }
    }
  }
}
