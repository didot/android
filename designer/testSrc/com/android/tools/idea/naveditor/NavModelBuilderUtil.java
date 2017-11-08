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
package com.android.tools.idea.naveditor;

import com.android.SdkConstants;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Function;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION;
import static org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_START_DESTINATION;
import static org.mockito.Mockito.when;

/**
 * Descriptors used for building navigation {@link com.android.tools.idea.common.model.NlModel}s
 */
public class NavModelBuilderUtil {
  private static final String TAG_FRAGMENT = "fragment";
  private static final String TAG_NAVIGATION = "navigation";

  @NotNull
  public static ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root, @NotNull AndroidFacet facet,
                                   @NotNull JavaCodeInsightTestFixture fixture) {
    Function<? super SyncNlModel, ? extends SceneManager> managerFactory = model -> {
      NavDesignSurface surface = (NavDesignSurface)model.getSurface();

      when(surface.getSchema()).thenReturn(NavigationSchema.getOrCreateSchema(facet));
      when(surface.getCurrentNavigation()).then(invocation -> model.getComponents().get(0));
      when(surface.getExtentSize()).thenReturn(new Dimension(500, 500));
      when(surface.getScrollPosition()).thenReturn(new Point(0, 0));

      return new NavSceneManager(model, surface);
    };

    return new ModelBuilder(facet, fixture, name, root, managerFactory,
                            NavSceneManager::updateHierarchy, "navigation", NavDesignSurface.class);
  }

  @NotNull
  public static NavigationComponentDescriptor rootComponent(@Nullable String id) {
    NavigationComponentDescriptor descriptor = new NavigationComponentDescriptor();
    if (id != null) {
      descriptor.id("@+id/" + id);
    }
    return descriptor;
  }

  @NotNull
  public static NavigationComponentDescriptor navigationComponent(@Nullable String id) {
    NavigationComponentDescriptor descriptor = new NavigationComponentDescriptor();
    if (id != null) {
      descriptor.id("@+id/" + id);
    }
    return descriptor;
  }

  @NotNull
  public static IncludeComponentDescriptor includeComponent(@NotNull String graphId) {
    IncludeComponentDescriptor descriptor = new IncludeComponentDescriptor();
    descriptor.withAttribute(AUTO_URI, SdkConstants.ATTR_GRAPH, SdkConstants.NAVIGATION_PREFIX + graphId);
    return descriptor;
  }

  @NotNull
  public static FragmentComponentDescriptor fragmentComponent(@NotNull String id) {
    FragmentComponentDescriptor descriptor = new FragmentComponentDescriptor();
    descriptor.id("@+id/" + id);
    return descriptor;
  }

  @NotNull
  public static ActionComponentDescriptor actionComponent(@NotNull String id) {
    ActionComponentDescriptor descriptor = new ActionComponentDescriptor();
    descriptor.id("@+id/" + id);
    return descriptor;
  }

  @NotNull
  public static ActivityComponentDescriptor activityComponent(@NotNull String id) {
    ActivityComponentDescriptor descriptor = new ActivityComponentDescriptor();
    descriptor.id("@+id/" + id);
    return descriptor;
  }

  @NotNull
  public static DeepLinkComponentDescriptor deepLinkComponent(@NotNull String uri) {
    DeepLinkComponentDescriptor descriptor = new DeepLinkComponentDescriptor();
    descriptor.withUriAttribute(uri);
    return descriptor;
  }

  public static class NavigationComponentDescriptor extends ComponentDescriptor {
    public NavigationComponentDescriptor() {
      super(TAG_NAVIGATION);
    }

    @NotNull
    public NavigationComponentDescriptor withStartDestinationAttribute(@NotNull String startDestination) {
      withAttribute(AUTO_URI, ATTR_START_DESTINATION, "@id/" + startDestination);
      return this;
    }

    @NotNull
    public NavigationComponentDescriptor withLabelAttribute(@NotNull String label) {
      withAttribute(ANDROID_URI, ATTR_LABEL, label);
      return this;
    }
  }

  public static class FragmentComponentDescriptor extends ComponentDescriptor {
    public FragmentComponentDescriptor() {
      super(TAG_FRAGMENT);
    }

    @NotNull
    public FragmentComponentDescriptor withLayoutAttribute(@NotNull String layout) {
      withAttribute(TOOLS_URI, ATTR_LAYOUT, "@layout/" + layout);
      return this;
    }
  }

  public static class ActionComponentDescriptor extends ComponentDescriptor {
    public ActionComponentDescriptor() {
      super(TAG_ACTION);
    }

    @NotNull
    public ActionComponentDescriptor withDestinationAttribute(@NotNull String destination) {
      withAttribute(AUTO_URI, ATTR_DESTINATION, "@id/" + destination);
      return this;
    }
  }

  public static class ActivityComponentDescriptor extends ComponentDescriptor {
    public ActivityComponentDescriptor() {
      super("activity");
    }
  }

  public static class IncludeComponentDescriptor extends ComponentDescriptor {
    public IncludeComponentDescriptor() {
      super("include");
    }
  }

  public static class DeepLinkComponentDescriptor extends ComponentDescriptor {
    public DeepLinkComponentDescriptor() {
      super(SdkConstants.TAG_DEEPLINK);
    }

    @NotNull
    public DeepLinkComponentDescriptor withUriAttribute(@NotNull String uri) {
      withAttribute(AUTO_URI, "uri", uri);
      return this;
    }
  }
}
