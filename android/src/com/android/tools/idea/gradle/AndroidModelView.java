/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.*;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.ProxyUtil;
import com.android.tools.idea.gradle.util.ui.ToolWindowAlikePanel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.util.ProxyUtil.getAndroidModelProxyValues;
import static com.android.tools.idea.gradle.util.ProxyUtil.isAndroidModelProxyObject;

/**
 * "Android Model" tool window to visualize the Android-Gradle model data.
 */
public class AndroidModelView {
  private static final ImmutableMultimap<String, String> SOURCE_PROVIDERS_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("SourceProviders", "SourceProvider", "ExtraSourceProviders").build();
  private static final ImmutableMultimap<String, String> SDK_VERSIONS_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("SdkVersions", "MinSdkVersion", "MaxSdkVersion", "TargetSdkVersion").build();
  private static final ImmutableMultimap<String, String> ARTIFACTS_GROUP =
    ImmutableMultimap.<String, String>builder().putAll("Artifacts", "MainArtifact", "ExtraAndroidArtifacts", "ExtraJavaArtifacts").build();

  @NotNull private final Project myProject;
  @NotNull private final Tree myTree;

  public AndroidModelView(@NotNull Project project) {
    myProject = project;
    myTree = new Tree();
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, new GradleSyncListener.Adapter() {
      @Override
      public void syncStarted(@NotNull Project project) {
        updateContents();
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        updateContents();
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        updateContents();
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        updateContents();
      }
    });
  }

  public static AndroidModelView getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidModelView.class);
  }

  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    JPanel toolWindowPanel = ToolWindowAlikePanel.createTreePanel(myProject.getName(), myTree);
    Content content = contentFactory.createContent(toolWindowPanel, "", false);
    toolWindow.getContentManager().addContent(content);
    updateContents();
  }

  private void updateContents() {
    myTree.setRootVisible(true);
    if (GradleSyncState.getInstance(myProject).isSyncInProgress()) {
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Gradle project sync in progress ...")));
      return;
    }
    else {
      myTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Loading ...")));
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(myProject.getName());
        for (Module module : ModuleManager.getInstance(myProject).getModules()) {
          AndroidGradleModel androidModel = AndroidGradleModel.get(module);
          if (androidModel != null) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(module.getName());
            AndroidProject androidProject = androidModel.getProxyAndroidProject();
            addProxyObject(moduleNode, androidProject, false, ImmutableList.of("SyncIssues", "UnresolvedDependencies", "ApiVersion"));
            rootNode.add(moduleNode);
          }
        }

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
            renderer.setLeafIcon(AndroidIcons.AndroidToolWindow);
            myTree.setCellRenderer(renderer);

            DefaultTreeModel model = new DefaultTreeModel(rootNode);
            myTree.setRootVisible(false);
            myTree.setModel(model);
          }
        });
      }
    });
  }

  @VisibleForTesting
  void addProxyObject(@NotNull DefaultMutableTreeNode node, @NotNull Object obj) {
    addProxyObject(node, obj, false);
  }

  private void addProxyObject(@NotNull DefaultMutableTreeNode node, @NotNull Object obj, boolean useDerivedNodeName) {
    addProxyObject(node, obj, useDerivedNodeName, ImmutableList.<String>of());
  }

  private void addProxyObject(@NotNull DefaultMutableTreeNode node,
                              @NotNull Object obj,
                              boolean useDerivedNodeName,
                              @NotNull Collection<String> skipProperties) {
    addProxyObject(node, obj, useDerivedNodeName, skipProperties, ImmutableList.<String>of(), ImmutableMultimap.<String, String>of());
  }

  private void addProxyObject(@NotNull DefaultMutableTreeNode node,
                              @NotNull Object obj,
                              boolean useDerivedNodeName,
                              @NotNull Collection<String> inlineProperties,
                              @NotNull Multimap<String, String> groupProperties) {
    addProxyObject(node, obj, useDerivedNodeName, ImmutableList.<String>of(), inlineProperties, groupProperties);
  }

  private void addProxyObject(@NotNull DefaultMutableTreeNode node,
                              @NotNull Object obj,
                              boolean useDerivedNodeName,
                              @NotNull Collection<String> skipProperties,
                              @NotNull Collection<String> inlineProperties,
                              @NotNull Multimap<String, String> groupProperties) {
    assert isAndroidModelProxyObject(obj);
    Map<String, DefaultMutableTreeNode> groupPropertyNodes = Maps.newHashMap();

    String name = null;
    for (Map.Entry<String, Object> entry : getAndroidModelProxyValues(obj).entrySet()) {
      String property = entry.getKey(); // method name in canonical form.
      property = property.substring(0, property.lastIndexOf('('));
      property = property.substring(property.lastIndexOf('.') + 1, property.length());
      if (property.startsWith("get")) {
        property = property.substring(3);
      }
      if (skipProperties.contains(property)) {
        continue;
      }

      boolean useDerivedNameValue = false;
      boolean addToParentNode = false;

      Object value = entry.getValue();
      if (value != null && property.equals("Name")) {
        name = value.toString();
      }
      if (inlineProperties.contains(property)) {
        addToParentNode = true;
        useDerivedNameValue = useDerivedNodeName;
      }

      DefaultMutableTreeNode parentNode = node;
      if (groupProperties.values().contains(property)) {
        for (Map.Entry<String, String> group : groupProperties.entries()) {
          if (group.getValue().equals(property)) {
            String groupName = group.getKey();
            DefaultMutableTreeNode groupNode = groupPropertyNodes.get(groupName);
            if (groupNode == null) {
              groupNode = new DefaultMutableTreeNode(groupName);
              groupPropertyNodes.put(groupName, groupNode);
            }
            parentNode = groupNode;
            useDerivedNameValue = true;
            break;
          }
        }
      }

      addPropertyNode(parentNode, property, value, useDerivedNameValue, addToParentNode);
    }

    for (DefaultMutableTreeNode groupNode : groupPropertyNodes.values()) {
      addToNode(node, groupNode);
    }

    if (useDerivedNodeName && name != null) {
      node.setUserObject(name);
    }
  }

  private void addPropertyNode(@NotNull DefaultMutableTreeNode node,
                               @NotNull String property,
                               @Nullable Object value,
                               boolean useDerivedNodeName,
                               boolean addToParentNode) {
    DefaultMutableTreeNode propertyNode = addToParentNode ? node : new DefaultMutableTreeNode(property);

    if (value != null && (isAndroidModelProxyObject(value))) {
      if (!customizeProxyObject(propertyNode, value, useDerivedNodeName)) {
        addProxyObject(propertyNode, value, useDerivedNodeName);
      }
    }
    else if (value instanceof Collection && (!((Collection)value).isEmpty() || addToParentNode)) {
      for (Object obj : (Collection)value) {
        addPropertyNode(propertyNode, "", obj, true, false);
      }
    }
    else if (value instanceof Map && (!((Map)value).isEmpty() || addToParentNode)) {
      Map map = (Map)value;
      for (Object key : map.keySet()) {
        addPropertyNode(propertyNode, key.toString(), map.get(key), false, false);
      }
    }
    else if (value instanceof ProxyUtil.InvocationErrorValue) {
      Throwable exception = ((ProxyUtil.InvocationErrorValue)value).exception;
      propertyNode.setUserObject(getNodeValue(property, "Error: " + exception.getClass().getName()));
    }
    else {
      propertyNode.setUserObject(getNodeValue(property, getStringForValue(value)));
    }

    if (!addToParentNode) {
      addToNode(node, propertyNode);
    }
  }

  private boolean customizeProxyObject(@NotNull DefaultMutableTreeNode propertyNode, @NotNull Object value, boolean useDerivedName) {
    assert isAndroidModelProxyObject(value);
    if (value instanceof ProductFlavorContainer) {
      addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("ProductFlavor", "ExtraSourceProviders"),
                     SOURCE_PROVIDERS_GROUP);
    }
    else if (value instanceof BuildTypeContainer) {
      addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("BuildType", "ExtraSourceProviders"), SOURCE_PROVIDERS_GROUP);
    }
    else if (value instanceof SourceProviderContainer) {
      addProxyObject(propertyNode, value, useDerivedName, ImmutableList.of("SourceProvider"), ImmutableMultimap.<String, String>of());
    }
    else if (value instanceof ProductFlavor) {
      addProxyObject(propertyNode, value, useDerivedName, ImmutableList.<String>of(), SDK_VERSIONS_GROUP);
    }
    else if (value instanceof Variant) {
      addProxyObject(propertyNode, value, useDerivedName, ImmutableList.<String>of(),
                     ImmutableList.of("ExtraAndroidArtifacts", "ExtraJavaArtifacts"), ARTIFACTS_GROUP);
    }
    else {
      return false;
    }
    return true;
  }

  @NotNull
  private String getStringForValue(@Nullable Object value) {
    if (value != null && value instanceof File) {
      String filePath = ((File)value).getPath();
      String basePath = myProject.getBasePath();
      if (basePath != null) {
        if (!basePath.endsWith(File.separator)) {
          basePath += File.separator;
        }
        if (filePath.startsWith(basePath)) {
          return filePath.substring(basePath.length());
        }
      }
    }
    return value == null ? "null" : value.toString();
  }

  @NotNull
  private static String getNodeValue(@NotNull String property, @NotNull String value) {
    return property.isEmpty() ? value : property + " -> " + value;
  }

  // Inserts the new child node at appropriate place in the parent.
  private static void addToNode(@NotNull DefaultMutableTreeNode parent, @NotNull DefaultMutableTreeNode newChild) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      DefaultMutableTreeNode existingChild = (DefaultMutableTreeNode)parent.getChildAt(i);
      if (compareTo(existingChild, newChild) >= 0) {
        parent.insert(newChild, i);
        return;
      }
    }
    parent.add(newChild);
  }

  private static int compareTo(@NotNull DefaultMutableTreeNode node1, @NotNull DefaultMutableTreeNode node2) {
    if (node1.isLeaf() && !node2.isLeaf()) {
      return -1;
    }
    else if (!node1.isLeaf() && node2.isLeaf()) {
      return 1;
    }
    else {
      return node1.getUserObject().toString().compareTo(node2.getUserObject().toString());
    }
  }
}
