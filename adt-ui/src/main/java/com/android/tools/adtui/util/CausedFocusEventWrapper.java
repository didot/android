// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.adtui.util;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;

/**
 * This class is to hide differences in FocusEvent implementations on JDK8 and JDK9+
 */
// FIXME-ank4: this class is not needed anymore. just use Java 11 API
public class CausedFocusEventWrapper {
  private final Enum<?> cause;

  public CausedFocusEventWrapper(FocusEvent e) {
    this.cause = getCause(e);
  }

  @VisibleForTesting
  CausedFocusEventWrapper(Enum<?> cause) {
    this.cause = cause;
  }

  @Nullable
  private Enum<?> getCause(FocusEvent e) {
    Enum<?> cause = null;
    try {
      Method getCause = FocusEvent.class.getMethod("getCause");
      cause = (Enum<?>)getCause.invoke(e);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
      Logger.getInstance(CausedFocusEventWrapper.class).error("Failed to get FocusEvent cause", ex);
    }
    return cause;
  }

  public boolean isTraversal() {
    return cause != null && "TRAVERSAL".equals(cause.name());
  }

  public boolean isTraversalForward(){
    return cause != null && "TRAVERSAL_FORWARD".equals(cause.name());
  }

  public boolean isTraversalBackward(){
    return cause != null && "TRAVERSAL_BACKWARD".equals(cause.name());
  }

  public boolean isTraversalUp() {
    return cause != null && "TRAVERSAL_UP".equals(cause.name());
  }

  public boolean isTraversalDown() {
    return cause != null && "TRAVERSAL_DOWN".equals(cause.name());
  }

  public static boolean isFocusEventWithCause(AWTEvent e) {
    // On Java9+ all the FocusEvents have cause.
    return e instanceof FocusEvent;
  }

  @Nullable
  public static CausedFocusEventWrapper newInstanceOrNull(AWTEvent e) {
    if (isFocusEventWithCause(e)) {
      return new CausedFocusEventWrapper((FocusEvent)e);
    }
    else {
      return null;
    }
  }
}
