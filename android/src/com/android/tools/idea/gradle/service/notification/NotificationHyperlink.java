/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

abstract class NotificationHyperlink {
  @NotNull private final String myUrl;
  @NotNull private final String myValue;

  NotificationHyperlink(@NotNull String url, @NotNull String text) {
    myUrl = url;
    myValue = String.format("<a href=\"%1$s\">%2$s</a>", StringUtil.escapeXml(url), text);
  }

  abstract void execute();

  boolean executeIfClicked(@NotNull HyperlinkEvent event) {
    if (myUrl.equals(event.getDescription())) {
      execute();
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return myValue;
  }
}
