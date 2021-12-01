/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import java.util.Optional;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DetailsPanelPanel2 extends JBPanel<DetailsPanelPanel2> implements Disposable {
  private final @NotNull JComponent myScrollPane;
  private @Nullable Disposable myDetailsPanel;
  private @Nullable Splitter mySplitter;

  public DetailsPanelPanel2(@NotNull JComponent scrollPane) {
    super(new BorderLayout());

    myScrollPane = scrollPane;
    add(scrollPane);
  }

  @Override
  public void dispose() {
    if (myDetailsPanel != null) {
      Disposer.dispose(myDetailsPanel);
    }
  }

  void viewDetails(@NotNull DetailsPanel detailsPanel) {
    if (mySplitter != null) {
      assert myDetailsPanel != null;
      Disposer.dispose(myDetailsPanel);

      myDetailsPanel = detailsPanel;
      mySplitter.setSecondComponent(detailsPanel);

      return;
    }

    myDetailsPanel = detailsPanel;
    remove(myScrollPane);

    mySplitter = new JBSplitter(true);
    mySplitter.setFirstComponent(myScrollPane);
    mySplitter.setSecondComponent(detailsPanel);

    add(mySplitter);
  }

  public void removeSplitter() {
    remove(mySplitter);
    mySplitter = null;

    assert myDetailsPanel != null;
    Disposer.dispose(myDetailsPanel);
    myDetailsPanel = null;

    add(myScrollPane);
  }

  @VisibleForTesting
  @NotNull Optional<@NotNull Object> getDetailsPanel() {
    return Optional.ofNullable(myDetailsPanel);
  }

  @NotNull Optional<@NotNull Splitter> getSplitter() {
    return Optional.ofNullable(mySplitter);
  }
}
