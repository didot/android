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
package com.android.tools.idea.uibuilder.actions;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.SdkConstants.*;
import static java.util.Locale.ROOT;

/**
 * Action which converts a given layout hierarchy to a ConstraintLayout.
 * <p>
 * <ul>TODO:
 * <li>If it's a RelativeLayout *convert* layout constraints to the equivalents?
 * <li>When removing also remove other layout params (those that don't apply to constraint layout's styleable)
 * </ul>
 * </p>
 */
public class ConvertToConstraintLayoutAction extends AnAction {
  public static final String TITLE = "Convert to ConstraintLayout";
  public static final boolean ENABLED = true;

  public static final String ATTR_LAYOUT_CONVERSION_ABSOLUTE_X = "layout_conversion_absoluteX"; //$NON-NLS-1$
  public static final String ATTR_LAYOUT_CONVERSION_ABSOLUTE_Y = "layout_conversion_absoluteY"; //$NON-NLS-1$
  public static final String ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH = "layout_conversion_absoluteWidth"; //$NON-NLS-1$
  public static final String ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT = "layout_conversion_absoluteHeight"; //$NON-NLS-1$

  private final NlDesignSurface mySurface;

  public ConvertToConstraintLayoutAction(@NotNull NlDesignSurface surface) {
    super(TITLE, TITLE, null);
    mySurface = surface;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    ScreenView screenView = mySurface.getCurrentSceneView();
    NlComponent target = findTarget(screenView);
    if (target != null) {
      String tagName = target.getTagName();
      // Don't show action if it's already a ConstraintLayout
      if (NlComponentHelperKt.isOrHasSuperclass(target, CONSTRAINT_LAYOUT)) {
        presentation.setVisible(false);
        return;
      }
      presentation.setVisible(true);
      tagName = tagName.substring(tagName.lastIndexOf('.') + 1);
      presentation.setText("Convert " + tagName + " to ConstraintLayout");
      presentation.setEnabled(true);
    } else {
      presentation.setText(TITLE);
      presentation.setEnabled(false);
      presentation.setVisible(true);
    }
  }

  @Nullable
  private static NlComponent findTarget(@Nullable ScreenView screenView) {
    if (screenView != null) {
      List<NlComponent> selection = screenView.getSelectionModel().getSelection();
      if (selection.size() == 1) {
        NlComponent selected = selection.get(0);
        while (selected != null && !selected.isRoot() && selected.getChildren().isEmpty()) {
          selected = selected.getParent();
        }

        return selected;
      }
    }

    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ScreenView screenView = mySurface.getCurrentSceneView();
    if (screenView == null) {
      return;
    }
    NlComponent target = findTarget(screenView);
    if (target == null) {
      // Shouldn't happen, enforced by update(AnActionEvent)
      return;
    }

    // Step #1: UI

    Project project = mySurface.getProject();
    ConvertToConstraintLayoutForm dialog = new ConvertToConstraintLayoutForm(project);
    if (!dialog.showAndGet()) {
      return;
    }

    boolean flatten = dialog.getFlattenHierarchy();
    boolean includeIds = dialog.getFlattenReferenced();


    // Step #2: Ensure ConstraintLayout is available in the project
    GradleDependencyManager manager = GradleDependencyManager.getInstance(project);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(CONSTRAINT_LAYOUT_LIB_ARTIFACT + ":+");
    if (!manager.ensureLibraryIsIncluded(screenView.getModel().getModule(), Collections.singletonList(coordinate), null)) {
      return;
    }

    // Step #3: Migrate

    NlModel model = screenView.getModel();

    @SuppressWarnings("ConstantConditions")
    ConstraintLayoutConverter converter = new ConstraintLayoutConverter(screenView, target, flatten, includeIds);
    converter.execute();
  }

  private static void inferConstraints(@NotNull NlComponent target) {
    try {
      Scout.inferConstraints(target);
      ArrayList<NlComponent> list = new ArrayList<>(target.getChildren());
      list.add(0, target);

      for (NlComponent component : list) {
        AttributesTransaction transaction = component.startAttributeTransaction();
        transaction.commit();
      }
      removeAbsolutePositionAndSizes(target);
    }
    catch (Throwable t) {
      Logger.getInstance(ConvertToConstraintLayoutAction.class).warn(t);
    }
  }

  /** Removes absolute x/y/width/height conversion attributes */
  private static void removeAbsolutePositionAndSizes(NlComponent component) {
    // Work bottom up to ensure the children aren't invalidated when processing the parent
    for (NlComponent child : component.getChildren()) {
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_X, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_Y, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH, null);
      child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT, null);
      removeAbsolutePositionAndSizes(child);
    }
  }

  private static class ConstraintLayoutConverter extends WriteCommandAction {
    private final ScreenView myScreenView;
    private final boolean myFlatten;
    private final boolean myIncludeIds;
    private ViewEditorImpl myEditor;
    private List<NlComponent> myToBeFlattened;
    private final NlComponent myRoot;
    private final NlComponent myLayout;

    public ConstraintLayoutConverter(@NotNull ScreenView screenView, @NotNull NlComponent target, boolean flatten, boolean includeIds) {
      super(screenView.getSurface().getProject(), TITLE, screenView.getModel().getFile());
      myScreenView = screenView;
      myFlatten = flatten;
      myIncludeIds = includeIds;
      myLayout = target;
      myRoot = myScreenView.getModel().getComponents().get(0);
      myEditor = new ViewEditorImpl(myScreenView);
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      if (myLayout == null) {
        return;
      }

      myToBeFlattened = Lists.newArrayList();
      processComponent(myLayout);

      // TODO: If the old layout had attributes that don't apply anymore (such as "android:orientation" when converting
      // from a LinearLayout), remove these

      // We should also remove padding attributes on the root element - these confuse the constraint layout handler (issue #209905)
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING, null);
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING_LEFT, null);
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING_RIGHT, null);
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING_START, null);
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING_END, null);
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING_TOP, null);
      myLayout.setAttribute(ANDROID_URI, ATTR_PADDING_BOTTOM, null);

      flatten();
      PsiElement tag = myLayout.getTag().setName(CLASS_CONSTRAINT_LAYOUT);
      CodeStyleManager.getInstance(getProject()).reformat(tag);
      inferConstraints(myLayout);
    }

    /** Add bounds to components and record components to be flattened into {@link #myToBeFlattened} */
    private void processComponent(NlComponent component) {
      // Work bottom up to ensure the children aren't invalidated when processing the parent
      for (NlComponent child : component.getChildren()) {
        int dpx = myEditor.pxToDp(NlComponentHelperKt.getX(child) - NlComponentHelperKt.getX(myRoot));
        int dpy = myEditor.pxToDp(NlComponentHelperKt.getY(child) - NlComponentHelperKt.getY(myRoot));
        int dpw = myEditor.pxToDp(NlComponentHelperKt.getW(child));
        int dph = myEditor.pxToDp(NlComponentHelperKt.getH(child));

        child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_X, String.format(ROOT, VALUE_N_DP, dpx));
        child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_Y, String.format(ROOT, VALUE_N_DP, dpy));
        child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH, String.format(ROOT, VALUE_N_DP, dpw));
        child.setAttribute(TOOLS_URI, ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT, String.format(ROOT, VALUE_N_DP, dph));

        // First gather attributes to delete; can delete during iteration (concurrent modification exceptions will ensure)
        List<String> toDelete = null;
        for (AttributeSnapshot attribute : child.getAttributes()) {
          String name = attribute.name;
          if (!name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) ||
              !ANDROID_URI.equals(attribute.namespace) ||
              name.equals(ATTR_LAYOUT_WIDTH) ||
              name.equals(ATTR_LAYOUT_HEIGHT)) {
            continue;
          }
          if (toDelete == null) {
            toDelete = Lists.newArrayList();
          }
          toDelete.add(name);
        }
        if (toDelete != null) {
          for (String name : toDelete) {
            child.setAttribute(ANDROID_URI, name, null);
          }
        }

        if (isLayout(child)) {
          if (myFlatten) {
            if (shouldFlatten(child)) {
              myToBeFlattened.add(child);
            } else {
              continue;
            }
          } else {
            continue;
          }
        }
        processComponent(child);
      }
    }

    /**
     * Flatten layouts listed in {@link #myToBeFlattened} in order.
     * These should already be in bottom up order
     */
    private void flatten() {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
      Document document = documentManager.getDocument(myScreenView.getModel().getFile());
      if (document == null) {
        return;
      }
      documentManager.doPostponedOperationsAndUnblockDocument(document);

      List<TextRange> ranges = Lists.newArrayList();
      for (NlComponent component : myToBeFlattened) {
        XmlTag tag = component.getTag();
        PsiElement openStart = null;
        PsiElement openEnd = null;
        PsiElement closeStart = null;
        PsiElement closeEnd = null;
        PsiElement curr = tag.getFirstChild();
        while (curr != null) {
          IElementType elementType = curr.getNode().getElementType();
          if (elementType == XmlTokenType.XML_START_TAG_START) {
            openStart = curr;
          }
          else if (elementType == XmlTokenType.XML_TAG_END) {
            if (closeStart == null) {
              openEnd = curr;
            }
            else {
              closeEnd = curr;
              break;
            }
          }
          else if (elementType == XmlTokenType.XML_END_TAG_START) {
            closeStart = curr;
          }
          else if (elementType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
            openEnd = curr;
            break;
          }
          curr = curr.getNextSibling();
        }

        if (openStart != null && openEnd != null && closeStart != null && closeEnd != null) {
          ranges.add(new TextRange(openStart.getTextOffset(), openEnd.getTextOffset() + openEnd.getTextLength()));
          ranges.add(new TextRange(closeStart.getTextOffset(), closeEnd.getTextOffset() + closeEnd.getTextLength()));
        }
      }

      ranges.sort(new Comparator<TextRange>() {
        @Override
        public int compare(TextRange o1, TextRange o2) {
          // There should be no overlaps
          return o2.getStartOffset() - o1.getStartOffset();
        }
      });

      for (TextRange range : ranges) {
        document.deleteString(range.getStartOffset(), range.getEndOffset());
      }

      documentManager.commitDocument(document);
    }

    private static boolean isLayout(@NotNull NlComponent component) {
      List<NlComponent> children = component.getChildren();
      if (children.size() > 1) {
        return true;
      } else if (children.size() == 1) {
        NlComponent child = children.get(0);
        if (!REQUEST_FOCUS.equals(child.getTagName())) {
          // Some child *other* than <requestFocus> - must be a layout
          return true;
        }
        // If the child is a <requestFocus> we don't know
      }

      if (NlComponentHelperKt.getViewInfo(component) != null) {
        Object viewObject = NlComponentHelperKt.getViewInfo(component).getViewObject();
        if (viewObject != null) {
          Class<?> cls = viewObject.getClass();
          while (cls != null) {
            String fqcn = cls.getName();
            if (FQCN_ADAPTER_VIEW.equals(fqcn)) {
              // ListView etc - a ViewGroup but NOT considered a layout
              return false;
            }
            if (fqcn.startsWith(ANDROID_WEBKIT_PKG) && fqcn.endsWith(WEB_VIEW)) {
              // WebView: an AbsoluteLayout child class but NOT a "layout"
              return false;
            }
            if (CLASS_VIEWGROUP.equals(fqcn)) {
              return true;
            }
            cls = cls.getSuperclass();
          }
        }
      }

      return false;
    }

    private boolean shouldFlatten(@NotNull NlComponent component) {
      // See if the component seems to have a visual purpose - e.g. sets background or other styles
      if (component.getAttribute(ANDROID_URI, ATTR_BACKGROUND) != null
          || component.getAttribute(ANDROID_URI, ATTR_FOREGROUND) != null // such as ?android:selectableItemBackground
          || component.getAttribute(null, ATTR_ID) != null) {
        return false;
      }

      String id = component.getAttribute(ANDROID_URI, ATTR_ID);
      if (id == null) {
        return true;
      }

      // If it defines an ID, see if the ID is used anywhere
      if (!myIncludeIds) {
        XmlAttribute attribute = component.getTag().getAttribute(ATTR_ID, ANDROID_URI);
        if (attribute != null) {
          XmlAttributeValue valueElement = attribute.getValueElement();
          if (valueElement != null && valueElement.isValid()) {
            // Exact replace only, no comment/text occurrence changes since it is non-interactive
            RenameProcessor processor = new RenameProcessor(myScreenView.getSurface().getProject(), valueElement, "NONEXISTENT_ID12345",
                                                            false /*comments*/, false /*text*/);
            processor.setPreviewUsages(false);
            XmlFile layoutFile = myScreenView.getModel().getFile();

            // Do a quick usage search to see if we need to ask about renaming
            UsageInfo[] usages = processor.findUsages();
            for (UsageInfo info : usages) {
              PsiFile file = info.getFile();
              if (!layoutFile.equals(file)) {
                // Referenced from outside of this layout file!
                return false;
              }
            }
          }
        }
      }

      return true;
    }
  }
}
