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
package com.android.tools.idea.uibuilder.lint;

import com.android.tools.idea.lint.SuppressLintIntentionAction;
import com.android.tools.idea.rendering.HtmlBuilderHelper;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel.IssueData;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.android.tools.lint.detector.api.Issue;
import com.android.utils.HtmlBuilder;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.MouseChecker;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static java.awt.RenderingHints.*;

/**
 * Pane which lets you see the current lint warnings and apply fix/suppress
 */
public class LintNotificationPanel implements HyperlinkListener, ActionListener {
  /**
   * Margin around the popup to avoid accidental dismissals
   */
  private static final int DISMISS_MARGIN_PX = JBUI.scale(20);
  private static final Dimension MIN_POPUP_SIZE = new Dimension(600, 300);
  private final SceneView mySceneView;
  private JEditorPane myExplanationPane;
  private JBList myIssueList;
  private JPanel myPanel;
  private JBLabel myPreviewLabel;
  private JBLabel myTagLabel;
  private JBCheckBox myShowIcons;

  private HtmlLinkManager myLinkManager = new HtmlLinkManager();

  public static final String DIMENSION_KEY = "lint.notification";
  private JBPopup myPopup;

  public LintNotificationPanel(@NotNull SceneView sceneView, @NotNull LintAnnotationsModel model) {
    mySceneView = sceneView;

    List<IssueData> issues = getSortedIssues(sceneView, model);
    if (issues == null) {
      return;
    }
    //noinspection unchecked
    myIssueList.setModel(new CollectionListModel<>(issues));
    configureCellRenderer();

    myPanel.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.scale(8)));
    myExplanationPane.setMargin(JBUI.insets(3, 3, 3, 3));
    myExplanationPane.setContentType(UIUtil.HTML_MIME);
    myExplanationPane.addHyperlinkListener(this);

    myIssueList.setSelectedIndex(0);
    selectIssue(issues.get(0));
    myIssueList.addListSelectionListener(e -> {
      Object selectedValue = myIssueList.getSelectedValue();
      if (!(selectedValue instanceof IssueData)) {
        return;
      }
      IssueData selected = (IssueData)selectedValue;
      selectIssue(selected);
    });

    myPanel.setFocusable(false);
    myShowIcons.setSelected(AndroidEditorSettings.getInstance().getGlobalState().isShowLint());
    myShowIcons.addActionListener(this);
    ApplicationManager.getApplication().invokeLater(() -> myIssueList.requestFocus());
  }

  private void configureCellRenderer() {
    myIssueList.setCellRenderer(new ColoredListCellRenderer<IssueData>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, IssueData value, int index, boolean selected, boolean hasFocus) {
        if (value.level == HighlightDisplayLevel.ERROR) {
          append("Error: ", SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else if (value.level == HighlightDisplayLevel.WARNING) {
          append("Warning: ");
        }
        append(value.message);
      }
    });
  }

  @Nullable
  private static List<IssueData> getSortedIssues(@NotNull SceneView screenView, @NotNull LintAnnotationsModel model) {
    List<IssueData> issues = model.getIssues();
    if (issues.isEmpty()) {
      return null;
    }

    // Sort -- and prefer the selected components first
    List<NlComponent> selection = screenView.getSelectionModel().getSelection();
    Collections.sort(issues, (o1, o2) -> {
      boolean selected1 = selection.contains(o1.component);
      boolean selected2 = selection.contains(o2.component);
      if (selected1 != selected2) {
        return selected1 ? -1 : 1;
      }

      int compare = -o1.level.getSeverity().compareTo(o2.level.getSeverity());
      if (compare != 0) {
        return compare;
      }
      compare = o2.issue.getPriority() - o1.issue.getPriority();
      if (compare != 0) {
        return compare;
      }
      compare = o1.issue.compareTo(o2.issue);
      if (compare != 0) {
        return compare;
      }

      compare = o1.message.compareTo(o2.message);
      if (compare != 0) {
        return compare;
      }

      return o1.startElement.getTextOffset() - o2.startElement.getTextOffset();
    });
    return issues;
  }

  private void selectIssue(@Nullable IssueData selected) {
    NlComponent component = selected != null ? selected.component : null;
    updateIdLabel(component);
    updateExplanation(selected);
    updatePreviewImage(component);
  }

  private void updateIdLabel(@Nullable NlComponent component) {
    String text = "";
    if (component != null) {
      String id = component.getId();
      if (id != null) {
        text = id;
      } else {
        String tagName = component.getTagName();
        // custom views: strip off package:
        tagName = tagName.substring(tagName.lastIndexOf(".")+1);
        text = "<" + tagName + ">";
      }

      // Include position too to help disambiguate
      text += " at (" + Coordinates.pxToDp(mySceneView, component.x) + "," + Coordinates.pxToDp(mySceneView, component.y) + ") dp";
    }
    myTagLabel.setText(text);
  }

  private void updateExplanation(@Nullable IssueData selected) {
    // We have the capability to show markup text here, e.g.
    // myExplanationPane.setContentType(UIUtil.HTML_MIME)
    // and then use an HtmlBuilder to populate it with for
    // example issue.getExplanation(HTML). However, the builtin
    // HTML formatter ends up using a bunch of weird fonts etc
    // so the dialog just ends up looking tacky.

    String headerFontColor = HtmlBuilderHelper.getHeaderFontColor();
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();

    if (selected != null) {
      builder.addHeading("Message: ", headerFontColor);
      builder.add(selected.message).newline();


      // Look for quick fixes
      AndroidLintInspectionBase inspection = selected.inspection;
      AndroidLintQuickFix[] quickFixes = inspection.getQuickFixes(selected.startElement, selected.endElement, selected.message,
                                                                  selected.quickfixData);
      IntentionAction[] intentions = inspection.getIntentions(selected.startElement, selected.endElement);
      builder.addHeading("Suggested Fixes:", headerFontColor).newline();
      builder.beginList();
      for (final AndroidLintQuickFix fix : quickFixes) {
        builder.listItem();
        builder.addLink(fix.getName(), myLinkManager.createRunnableLink(() -> {
          myPopup.cancel();
          // TODO: Pull in editor context?
          WriteCommandAction.runWriteCommandAction(selected.startElement.getProject(), () -> {
            fix.apply(selected.startElement, selected.endElement, AndroidQuickfixContexts.BatchContext.getInstance());
          });
        }));
      }

      for (final IntentionAction fix : intentions) {
        builder.listItem();
        builder.addLink(fix.getText(), myLinkManager.createRunnableLink(() -> {
          NlModel model = mySceneView.getModel();
          Editor editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(selected.startElement);
          if (editor != null) {
            editor.getCaretModel().getCurrentCaret().moveToOffset(selected.startElement.getTextOffset());
            myPopup.cancel();
            WriteCommandAction.runWriteCommandAction(model.getProject(), () -> {
              fix.invoke(model.getProject(), editor, model.getFile());
            });
          }
        }));
      }

      final SuppressLintIntentionAction suppress = new SuppressLintIntentionAction(selected.issue, selected.startElement);
      builder.listItem();
      builder.addLink(suppress.getText(), myLinkManager.createRunnableLink(() -> {
        myPopup.cancel();
        WriteCommandAction.runWriteCommandAction(selected.startElement.getProject(), () -> {
          suppress.invoke(selected.startElement.getProject(), null, mySceneView.getModel().getFile());
        });
      }));

      builder.endList();

      Issue issue = selected.issue;

      builder.addHeading("Priority: ", headerFontColor);
      builder.addHtml(String.format("%1$d / 10", issue.getPriority()));
      builder.newline();
      builder.addHeading("Category: ", headerFontColor);
      builder.add(issue.getCategory().getFullName());
      builder.newline();

      builder.addHeading("Severity: ", headerFontColor);
      builder.beginSpan();

      // Use converted level instead of *default* severity such that we match any user configured overrides
      HighlightDisplayLevel level = selected.level;
      builder.add(StringUtil.capitalize(level.getName().toLowerCase(Locale.US)));
      builder.endSpan();
      builder.newline();

      builder.addHeading("Explanation: ", headerFontColor);
      String description = issue.getBriefDescription(HTML);
      builder.addHtml(description);
      if (!description.isEmpty()
          && Character.isLetter(description.charAt(description.length() - 1))) {
        builder.addHtml(".");
      }
      builder.newline();
      String explanationHtml = issue.getExplanation(HTML);
      builder.addHtml(explanationHtml);
      List<String> moreInfo = issue.getMoreInfo();
      builder.newline();
      int count = moreInfo.size();
      if (count > 1) {
        builder.addHeading("More Info: ", headerFontColor);
        builder.beginList();
      }
      for (String uri : moreInfo) {
        if (count > 1) {
          builder.listItem();
        }
        builder.addLink(uri, uri);
      }
      if (count > 1) {
        builder.endList();
      }
      builder.newline();
    }

    builder.closeHtmlBody();

    try {
      myExplanationPane.read(new StringReader(builder.getHtml()), null);
      HtmlBuilderHelper.fixFontStyles(myExplanationPane);
      myExplanationPane.setCaretPosition(0);
    }
    catch (IOException ignore) { // can't happen for internal string reading
    }
  }

  private void updatePreviewImage(@Nullable NlComponent component) {
    // Show the icon in the image view
    if (component != null) {
      // Try to get the image
      int iw = myPreviewLabel.getSize().width;
      int ih = myPreviewLabel.getSize().height;
      if (iw == 0 || ih == 0) {
        iw = 200;
        ih = 200;
      }

      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);

      RenderResult renderResult = mySceneView.getModel().getRenderResult();
      if (renderResult != null && renderResult.hasImage()) {
        // Draw the component into the preview image
        Graphics2D g2d = (Graphics2D)image.getGraphics();

        int sx1 = component.x;
        int sy1 = component.y;
        int sx2 = sx1 + component.w;
        int sy2 = sy1 + component.h;

        int dx1 = 0;
        int dy1 = 0;
        int dx2 = image.getWidth();
        int dy2 = image.getHeight();

        int ex1 = 0;
        int ey1 = 0;
        int ew = image.getWidth();
        int eh = image.getHeight();

        if (component.isRoot()) {
          int w = image.getWidth();
          int h = image.getHeight();

          double aspectRatio = (sx2 - sx1) / (double) (sy2 - sy1);
          if (aspectRatio >= 1) {
            int newH = (int)(h / aspectRatio);
            dy1 += (h - newH) / 2;
            h = newH;

            if (w >= (sx2 - sx1)) {
              // No need to scale: just buildDisplayList 1-1
              dx1 = (w - (sx2 - sx1)) / 2;
              w = sx2 - sx1;
              dy1 = (h - (sy2 - sy1)) / 2;
              h = sy2 - sy1;
            }
          } else {
            int newW = (int)(w * aspectRatio);
            dx1 += (w - newW) / 2;
            w = newW;

            if (h >= (sy2 - sy1)) {
              // No need to scale: just buildDisplayList 1-1
              dx1 = (w - (sx2 - sx1)) / 2;
              w = sx2 - sx1;
              dy1 = (h - (sy2 - sy1)) / 2;
              h = sy2 - sy1;
            }
          }
          dx2 = dx1 + w;
          dy2 = dy1 + h;
        } else {
          double aspectRatio = (sx2 - sx1) / (double)(sy2 - sy1);
          if (aspectRatio >= 1) {
            // Include enough context
            int verticalPadding = ((sx2 - sx1) - (sy2 - sy1)) / 2;
            sy1 -= verticalPadding;
            sy2 += verticalPadding;
            double scale = (sx2 - sx1) / (double)(dx2 - dx1);
            ey1 = (int)(verticalPadding / scale);
            eh = (int)(component.h / scale);
          }
          else {
            int horizontalPadding = ((sy2 - sy1) - (sx2 - sx1)) / 2;
            sx1 -= horizontalPadding;
            sx2 += horizontalPadding;
            double scale = (sy2 - sy1) / (double)(dy2 - dy1);
            ex1 = (int)(horizontalPadding / scale);
            ew = (int)(component.w / scale);
          }
        }

        // Use a gradient buildDisplayList here with alpha?

        //graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(KEY_ALPHA_INTERPOLATION, VALUE_ALPHA_INTERPOLATION_QUALITY);
        renderResult.getRenderedImage().drawImageTo(g2d, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2);

        if (!component.isRoot()) {
          Area outside = new Area(new Rectangle2D.Double(0, 0, iw, ih));
          int padding = 10;
          Area area = new Area(new Ellipse2D.Double(ex1 - padding, ey1 - padding, ew + 2 * padding, eh + 2 * padding));
          outside.subtract(area);

          // To get anti=aliased shape clipping (e.g. soft shape clipping) we need to use an intermediate image:
          GraphicsConfiguration gc = g2d.getDeviceConfiguration();
          BufferedImage img = gc.createCompatibleImage(iw, ih, Transparency.TRANSLUCENT);
          Graphics2D g2 = img.createGraphics();
          g2.setComposite(AlphaComposite.Clear);
          g2.fillRect(0, 0, iw, ih);
          g2.setComposite(AlphaComposite.Src);
          g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
          // This color is relative to the Android image being painted, not dark/light IDE theme
          //noinspection UseJBColor
          g2.setColor(Color.WHITE);
          g2.fill(outside);
          g2.setComposite(AlphaComposite.SrcAtop);
          Color background = myPanel.getBackground();
          if (background == null) {
            background = Gray._230;
          }
          g2.setPaint(background);
          g2.fillRect(0, 0, iw, ih);
          g2.dispose();
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
          g2d.drawImage(img, 0, 0, null);
        }

        g2d.dispose();
      }

      myPreviewLabel.setIcon(new ImageIcon(image));
    } else {
      myPreviewLabel.setIcon(null);
    }
  }

  /**
   * Sets-up the panel popup and calls the passed {@link Consumer<JBPopup>} with the new {@link JBPopup} instance.
   * This method will not display the popup.
   */
  public JBPopup setupPopup(@Nullable Project project, @NotNull Consumer<JBPopup> onPopupBuilt) {
    JBPopup builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myPanel, myPanel)
      .setProject(project)
      .setDimensionServiceKey(project, DIMENSION_KEY, false)
      .setResizable(true)
      .setMovable(true)
      .setMinSize(MIN_POPUP_SIZE)
      .setRequestFocus(true)
      .setTitle("Lint Warnings in Layout")
      .setCancelOnClickOutside(true)
      .setLocateWithinScreenBounds(true)
      .setShowShadow(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelOnMouseOutCallback(new MouseChecker() {
        Rectangle myDismissRectangle;
        double myPreviousDistance = 0;

        @Override
        public boolean check(MouseEvent event) {
          if (myPopup == null) {
            return false;
          }

          Point mousePosition = event.getPoint();
          SwingUtilities.convertPointToScreen(mousePosition, event.getComponent());

          Point popupPosition = myPopup.getLocationOnScreen();
          Dimension popupDimension = myPopup.getSize();
          int centerX = popupPosition.x + popupDimension.width / 2;
          int centerY = popupPosition.y + popupDimension.height / 2;

          // Is it the mouse getting closer to the center of the popup or moving away? We only close the dialog if the mouse is
          // moving away.
          double currentDistance = mousePosition.distance(centerX, centerY);
          double previousDistance = myPreviousDistance;
          myPreviousDistance = currentDistance;
          boolean mouseMovingAway = previousDistance != 0 && currentDistance > previousDistance;

          if (!mouseMovingAway) {
            // We only dismiss the dialog if the mouse is moving away from the center
            return false;
          }

          int dismissRectX = popupPosition.x - DISMISS_MARGIN_PX;
          int dismissRectY = popupPosition.y - DISMISS_MARGIN_PX;
          int dismissRectW = popupDimension.width + 2 * DISMISS_MARGIN_PX;
          int dismissRectH = popupDimension.height + 2 * DISMISS_MARGIN_PX;
          if (myDismissRectangle == null) {
            myDismissRectangle = new Rectangle(dismissRectX, dismissRectY, dismissRectW, dismissRectH);
          }
          else {
            myDismissRectangle.setBounds(dismissRectX, dismissRectY, dismissRectW, dismissRectH);
          }

          return !myDismissRectangle.contains(mousePosition);
        }
      })
      .createPopup();

    myPopup = builder;
    Disposer.register(mySceneView.getSurface(), myPopup);
    onPopupBuilt.accept(builder);
    return builder;
  }

  @NotNull
  public JBPopup showInScreenPosition(@Nullable Project project, @NotNull Component owner, @NotNull Point point) {
    return setupPopup(project, popup -> popup.showInScreenCoordinates(owner, point));
  }

  @NotNull
  public JBPopup showInScreenPosition(@Nullable Project project, @NotNull RelativePoint point) {
    return setupPopup(project, popup -> popup.show(point));
  }

  @NotNull
  public JBPopup showInBestPositionFor(@Nullable Project project, @NotNull DataContext dataContext) {
    return setupPopup(project, popup -> popup.showInBestPositionFor(dataContext));
  }

  @NotNull
  public JBPopup showInBestPositionFor(@Nullable Project project, @NotNull JComponent component) {
    return setupPopup(project, popup -> {
      Dimension preferredSize = DimensionService.getInstance().getSize(DIMENSION_KEY, project);
      if (preferredSize == null) {
        preferredSize = myPanel.getPreferredSize();
      }

      showInScreenPosition(project,
                           new RelativePoint(component, new Point(component.getWidth() - preferredSize.width, component.getHeight())));
    });
  }

  /**
   * Selects a lint issue for the component located at x, y
   */
  public void selectIssueAtPoint(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    LintAnnotationsModel lintModel = mySceneView.getModel().getLintAnnotationsModel();
    if (lintModel == null) {
      return;
    }

    lintModel.getIssues().stream()
      .filter((issue) -> issue.component.containsX(x) && issue.component.containsY(y))
      .findAny()
      .ifPresent((issue) -> myIssueList.setSelectedValue(issue, true));
  }

  // ---- Implements HyperlinkListener ----

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      JEditorPane pane = (JEditorPane)e.getSource();
      if (e instanceof HTMLFrameHyperlinkEvent) {
        HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
        HTMLDocument doc = (HTMLDocument)pane.getDocument();
        doc.processHTMLFrameHyperlinkEvent(evt);
        return;
      }

      String url = e.getDescription();
      NlModel model = mySceneView.getModel();
      Module module = model.getModule();
      PsiFile file = model.getFile();
      DataContext dataContext = DataManager.getInstance().getDataContext(mySceneView.getSurface());
      assert dataContext != null;

      myLinkManager.handleUrl(url, module, file, dataContext, null);
    }
  }

  // ---- Implements ActionListener ----

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myShowIcons) {
      AndroidEditorSettings.getInstance().getGlobalState().setShowLint(myShowIcons.isSelected());
    }
  }
}
