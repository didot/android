/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.ResourceResolver;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GutterIconRenderer extends com.intellij.openapi.editor.markup.GutterIconRenderer implements DumbAware {
  private final PsiElement myElement;
  private final VirtualFile myFile;
  private final ResourceResolver myResourceResolver;

  public GutterIconRenderer(ResourceResolver resourceResolver, @NotNull PsiElement element, @NotNull VirtualFile file) {
    myResourceResolver = resourceResolver;
    myElement = element;
    myFile = file;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    AndroidFacet facet = AndroidFacet.getInstance(myElement);
    if (facet == null) {
      return AllIcons.General.Error;
    }
    Icon icon = GutterIconCache.getInstance().getIcon(myFile, myResourceResolver, facet);

    if (icon != null) {
      return icon;
    }

    return AllIcons.General.Error;
  }

  @Override
  public AnAction getClickAction() {
    return new GutterIconClickAction(myFile, myResourceResolver, myElement);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GutterIconRenderer that = (GutterIconRenderer)o;

    if (!myElement.equals(that.myElement)) return false;
    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myElement.hashCode();
    result = 31 * result + myFile.hashCode();
    return result;
  }

  private static class GutterIconClickAction extends AnAction {
    private final static int PREVIEW_MAX_WIDTH = JBUI.scale(128);
    private final static int PREVIEW_MAX_HEIGHT = JBUI.scale(128);
    private final static String PREVIEW_TEXT = "Click Image to Open Resource";

    private final VirtualFile myFile;
    private final ResourceResolver myResourceResolver;
    private final PsiElement myElement;

    private GutterIconClickAction(VirtualFile file, ResourceResolver resourceResolver, PsiElement element) {
      myFile = file;
      myResourceResolver = resourceResolver;
      myElement = element;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      final Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());

      if (editor != null) {
        final Project project = editor.getProject();

        if (project != null) {
          JBPopup preview = createPreview(() -> openImageResourceTab(project));

          if (preview == null) {
            openImageResourceTab(project);
          }
          else {
            // Show preview popup at location of mouse click
            preview.show(new RelativePoint((MouseEvent)event.getInputEvent()));
          }
        }
      }
    }

    @Nullable
    private JBPopup createPreview(Runnable onClick) {
      AndroidFacet facet = AndroidFacet.getInstance(myElement);
      if (facet == null) {
        return null;
      }
      Icon icon = GutterIconFactory.createIcon(myFile, myResourceResolver, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT, facet);

      if (icon == null) {
        return null;
      }

      JBLabel label = new JBLabel(icon);
      ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(label, null);
      builder.setAdText(PREVIEW_TEXT, SwingConstants.CENTER);

      JBPopup popup = builder.createPopup();

      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent mouseEvent) {
          onClick.run();
          popup.cancel();
          label.removeMouseListener(this);
        }
      });

      return popup;
    }

    private void openImageResourceTab(@NotNull Project project) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, myFile, -1);
      FileEditorManager.getInstance(project).openEditor(descriptor, true);
    }
  }
}
