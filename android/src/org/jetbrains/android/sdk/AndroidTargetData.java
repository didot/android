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
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.LayoutLibraryLoader;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.FrameworkResourceRepository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTargetData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidTargetData");

  private final AndroidSdkData mySdkData;
  private final IAndroidTarget myTarget;

  private volatile AttributeDefinitionsImpl myAttrDefs;
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
    AttributeDefinitionsImpl attrDefs = getAllAttrDefs(project);
    return attrDefs != null ? new PublicAttributeDefinitions(attrDefs) : null;
  }

  /**
   * Returns all attributes
   */
  @Nullable
  public AttributeDefinitionsImpl getAllAttrDefs(@NotNull Project project) {
    if (myAttrDefs == null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        String attrsPath = FileUtil.toSystemIndependentName(myTarget.getPath(IAndroidTarget.ATTRIBUTES));
        String attrsManifestPath = FileUtil.toSystemIndependentName(myTarget.getPath(IAndroidTarget.MANIFEST_ATTRIBUTES));

        XmlFile[] files = findXmlFiles(project, attrsPath, attrsManifestPath);
        if (files != null) {
          myAttrDefs = new AttributeDefinitionsImpl(files);
        }
      });
    }
    return myAttrDefs;
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

      AttributeDefinitionsImpl attrDefs = getAllAttrDefs(project);
      if (attrDefs == null) {
        return null;
      }
      myLayoutLibrary = LayoutLibraryLoader.load(myTarget, attrDefs.getEnumMap());
    }

    return myLayoutLibrary;
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

  @Nullable
  private static XmlFile[] findXmlFiles(@NotNull Project project, @NotNull String... paths) {
    XmlFile[] xmlFiles = new XmlFile[paths.length];
    for (int i = 0; i < paths.length; i++) {
      String path = paths[i];
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      PsiFile psiFile = file != null ? AndroidPsiUtils.getPsiFileSafely(project, file) : null;
      if (psiFile == null) {
        LOG.info("File " + path + " is not found");
        return null;
      }
      if (!(psiFile instanceof XmlFile)) {
        LOG.info("File " + path + "  is not an xml psiFile");
        return null;
      }
      xmlFiles[i] = (XmlFile)psiFile;
    }
    return xmlFiles;
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
    protected boolean isAttributeAcceptable(@NotNull String name) {
      return isResourcePublic(ResourceType.ATTR.getName(), name);
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
