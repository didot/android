/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Generated file from illustrations mapping process.  DO NOT EDIT DIRECTLY
 **/
public class StudioIllustrations {
  // Collections of constants, do not instantiate.
  private StudioIllustrations() {}

  private static Icon load(String path) {
    return IconLoader.getIcon(path, StudioIllustrations.class);
  }

  public static class Common {
    public static final Icon DISCONNECT_PROFILER = load("/studio/illustrations/common/disconnect-profiler.png"); // 171x97
    public static final Icon DISCONNECT = load("/studio/illustrations/common/disconnect.png"); // 171x97
  }
}