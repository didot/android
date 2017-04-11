/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.SdkConstants;
import com.android.resources.ResourceConstants;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.uibuilder.editor.DefaultNlToolbarActionGroups;
import com.android.tools.idea.naveditor.editor.NavToolbarActionGroups;
import com.android.tools.idea.uibuilder.editor.ToolbarActionGroups;
import com.android.tools.idea.uibuilder.editor.VectorToolbarActionGroups;
import com.android.tools.idea.uibuilder.statelist.StateListActionGroups;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.drawable.fileDescriptions.AdaptiveIconDomFileDescription;
import org.jetbrains.android.dom.font.FontFamilyDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Describes the supported types of editors (where each editor type refers to the type of resource that the editor can handle
 */
public enum NlLayoutType {
  FONT(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return FontFamilyDomFileDescription.isFontFamilyFile(file);
    }
  },

  LAYOUT(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return LayoutDomFileDescription.isLayoutFile(file);
    }
  },

  MENU(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return MenuDomFileDescription.isMenuFile(file);
    }
  },

  PREFERENCE_SCREEN(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return isResourceTypeOf(file, ResourceConstants.FD_RES_XML, SdkConstants.TAG_PREFERENCE_SCREEN);
    }
  },

  STATE_LIST(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return isResourceTypeOf(file, ResourceConstants.FD_RES_DRAWABLE, SdkConstants.TAG_SELECTOR);
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new StateListActionGroups(surface);
    }
  },

  NAV(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return NavSceneManager.enableNavigationEditor() && file.getParent().getName().equals("nav");
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new NavToolbarActionGroups(surface);
    }
  },

  UNKNOWN(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return false;
    }

    @NotNull
    @Override
    public String getPaletteFileName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new ToolbarActionGroups(surface);
    }
  },

  VECTOR(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return isResourceTypeOf(file, ResourceConstants.FD_RES_DRAWABLE, SdkConstants.TAG_VECTOR) ||
             AdaptiveIconDomFileDescription.isAdaptiveIcon(file);
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new VectorToolbarActionGroups(surface);
    }
  };

  private final boolean mySupportedByDesigner;

  NlLayoutType(boolean supportedByDesigner) {
    mySupportedByDesigner = supportedByDesigner;
  }

  public abstract boolean isResourceTypeOf(@NotNull XmlFile file);

  static boolean isResourceTypeOf(@NotNull XmlFile file, @NotNull String directoryName, @NotNull String tagName) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      if (file.getProject().isDisposed()) {
        return false;
      }

      if (!AndroidResourceUtil.isInResourceSubdirectory(file, directoryName)) {
        return false;
      }

      XmlTag tag = file.getRootTag();

      if (tag == null) {
        return false;
      }

      return tag.getName().equals(tagName);
    });
  }

  public boolean isLayout() {
    return this == LAYOUT;
  }

  public static boolean supports(@NotNull XmlFile file) {
    return typeOf(file).isSupportedByDesigner();
  }

  @NotNull
  public static NlLayoutType typeOf(@NotNull XmlFile file) {
    for (NlLayoutType type : values()) {
      if (type.isResourceTypeOf(file)) {
        return type;
      }
    }

    return UNKNOWN;
  }

  public boolean isSupportedByDesigner() {
    return mySupportedByDesigner;
  }

  @NotNull
  public String getPaletteFileName() {
    return toString().toLowerCase(Locale.ROOT) + "_palette.xml";
  }

  @NotNull
  public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
    return new DefaultNlToolbarActionGroups((NlDesignSurface)surface);
  }
}
