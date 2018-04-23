/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant.view;

import com.android.tools.idea.assistant.AssistActionStateManager;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.assistant.datamodel.StepData;
import com.android.tools.idea.assistant.datamodel.StepElementData;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Renders a single step inside of a tutorial.
 *
 * TODO: Move render properties to a form.
 */
public class TutorialStep extends JPanel {

  // TODO: Refactor number related code to be an inner class + revisit colors.
  public final JBColor NUMBER_COLOR = new JBColor(0x52639B, 0x589df6);

  // constant for top/bottom padding for images
  private static final int IMAGE_PADDING = 10;

  private final int myIndex;
  private final StepData myStep;
  private final JPanel myContents;
  private final Project myProject;

  private static Logger getLog() {
    return Logger.getInstance(TutorialStep.class);
  }

  TutorialStep(@NotNull StepData step, int index, @NotNull ActionListener listener, @NotNull Project project, boolean hideStepIndex) {
    super(new GridBagLayout());
    myIndex = index;
    myStep = step;
    myProject = project;
    myContents = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    setOpaque(false);

    // TODO: Consider the setup being in the ctors of customer inner classes.
    if (!hideStepIndex) {
      initStepNumber();
    }
    initLabel();
    initStepContentsContainer();

    for (StepElementData element : step.getStepElements()) {
      // element is a wrapping node to preserve order in a heterogeneous list,
      // hence switching over type.
      switch (element.getType()) {
        case SECTION:
          // TODO: Make a custom inner class to handle this.
          JEditorPane section = new JEditorPane();
          section.setOpaque(false);
          section.setBorder(BorderFactory.createEmptyBorder());
          section.setDragEnabled(false);
          // HACK ALERT: Without a margin on the outer html container, the contents are set to a height of zero on theme change.
          UIUtils.setHtml(section, element.getSection(), ".as-shim { margin-top: 1px; }");
          myContents.add(section);
          break;
        case ACTION:
          if (element.getAction() != null) {
            ActionData action = element.getAction();
            Optional<AssistActionStateManager>
              stateManager =
              Arrays.stream(AssistActionStateManager.EP_NAME.getExtensions()).filter(s -> s.getId().equals(action.getKey())).findFirst();
            myContents
              .add(new StatefulButton(element.getAction(), listener, stateManager.orElse(null), project));
          }
          else {
            getLog().warn("Found action element with no action definition: " + element.toString());
          }
          break;
        case CODE:
          myContents.add(new CodePane(element));
          break;
        case IMAGE:
          File file;
          DefaultTutorialBundle.Image imageElement = element.getImage();
          try {
            file = new File(getClass().getResource(imageElement.getSource()).getPath());
            if (!file.isFile()) {
              getLog().error("Cannot load image: " + imageElement.getSource());
              continue;
            }
            ImageIcon imageIcon = new ImageIcon(ImageIO.read(file));
            Image image = imageIcon.getImage();
            Image scaledImage = image.getScaledInstance(imageElement.getWidth(), imageElement.getHeight(), Image.SCALE_SMOOTH);
            imageIcon = new ImageIcon(scaledImage, imageElement.getDescription());
            JPanel containerPanel = new JPanel(new HorizontalLayout(0));
            containerPanel.setBorder(new JBEmptyBorder(IMAGE_PADDING, 0, IMAGE_PADDING, 0));
            containerPanel.add(new JLabel(imageIcon));
            myContents.add(containerPanel);
          }
          catch (IOException e) {
            getLog().error("Cannot load image: " + imageElement.getSource(), e);
          }
          break;
        default:
          getLog().error("Found a StepElement of unknown type. " + element.toString());
      }
      // Add 5px spacing between elements.
      myContents.add(Box.createRigidArea(new Dimension(0, 5)));
    }
  }

  /**
   * Create and add the step label.
   */
  private void initLabel() {
    JLabel label = new JLabel(myStep.getLabel());
    Font font = label.getFont();
    Font plainFont = new Font(font.getFontName(), Font.PLAIN, 18);
    label.setFont(plainFont);

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = JBUI.insets(8, 10, 10, 5);

    add(label, c);
  }

  /**
   * Configure and add the container holding the set of step elements.
   */
  private void initStepContentsContainer() {
    myContents.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 1;
    c.gridy = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = JBUI.insets(0,10,0,10);

    add(myContents, c);
  }

  /**
   * Create and add the step number indicator. Note that this is a custom
   * display that surrounds the number with a circle thus has some tricky
   * display characteristics. It's unclear if a form can be leveraged for this.
   */
  private void initStepNumber() {
    // Get standard label font.
    Font font = new JLabel().getFont();
    JTextPane stepNumber = new JTextPane();
    stepNumber.setEditable(false);
    stepNumber.setText(String.valueOf(myIndex + 1));
    Font boldFont = new Font(font.getFontName(), Font.BOLD, 11);
    stepNumber.setFont(boldFont);
    stepNumber.setOpaque(false);
    stepNumber.setForeground(NUMBER_COLOR);
    stepNumber.setBorder(new NumberBorder());
    Dimension size = new Dimension(21, 21);
    stepNumber.setSize(size);
    stepNumber.setPreferredSize(size);
    stepNumber.setMinimumSize(size);
    stepNumber.setMaximumSize(size);

    StyledDocument doc = stepNumber.getStyledDocument();
    SimpleAttributeSet center = new SimpleAttributeSet();
    StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
    doc.setParagraphAttributes(0, doc.getLength(), center, false);

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = JBUI.insets(5, 5, 5, 0);

    add(stepNumber, c);
  }

  /**
   * Selects all text within an {@link EditorEx} when a simple click (without drag or selection) happens in the editor.
   *
   * Achieved through tracking whether text is selected when the mouse is depressed. If nothing was selected then and nothing
   * is selected after the mouse is released then select all of the text.
   */
  private static class AutoTextSelectionListener extends EditorMouseAdapter {
    private final EditorEx myEditor;
    private boolean myIsTextSelectedOnMousePressed = false;

    AutoTextSelectionListener(@NotNull EditorEx editor) {
      myEditor = editor;
    }

    private boolean isNothingSelected() {
      return Strings.isNullOrEmpty(myEditor.getSelectionModel().getSelectedText(true));
    }

    private boolean isAnythingSelected() {
      return !Strings.isNullOrEmpty(myEditor.getSelectionModel().getSelectedText(true));
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      if (!myIsTextSelectedOnMousePressed && isNothingSelected()) {
        selectAllText();
        e.consume();
      }
    }

    @Override
    public void mousePressed(EditorMouseEvent e) {
      myIsTextSelectedOnMousePressed = isAnythingSelected();
      if (myIsTextSelectedOnMousePressed) {
        // This disables drag and drop, but ensures developers aren't required to click again to clear the selection before trying to select
        // a different set of text.
        selectNothing();
      }
    }

    private void selectNothing() {
      LogicalPosition docStart = myEditor.visualToLogicalPosition(new VisualPosition(0, 0));
      myEditor.getCaretModel().setCaretsAndSelections(Lists.newArrayList(new CaretState(docStart, docStart, docStart)));
    }

    private void selectAllText() {
      int lineCount = myEditor.getDocument().getLineCount() - 1;
      if (lineCount < 0) {
        // Tutorials shouldn't have empty code snippets, but just in case.
        return;
      }
      int lastLineEndOffset = myEditor.getDocument().getLineEndOffset(lineCount);
      LogicalPosition docStart = myEditor.visualToLogicalPosition(new VisualPosition(0, 0));
      LogicalPosition docEnd = myEditor.visualToLogicalPosition(new VisualPosition(lineCount, lastLineEndOffset));
      myEditor.getCaretModel().setCaretsAndSelections(Lists.newArrayList(new CaretState(docStart, docStart, docEnd)));
    }
  }

  /**
   * A custom border used to create a circle around a specifically sized step number.
   */
  class NumberBorder extends AbstractBorder {

    /**
     * Space needed to render border, used at this value to center the text in the component.
     */
    private static final int INSET = 3;

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2 = (Graphics2D)g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      // Per documentation, the drawn oval covers an area of width + 1 and height + 1, account for this.
      int d = height - 1;
      g2.setColor(NUMBER_COLOR);
      g2.drawOval(x, y, d, d);
      g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.insets(INSET);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      insets.left = insets.right = insets.top = insets.bottom = INSET;
      return insets;
    }
  }

  /**
   * A read-only code editor designed to display code samples, this should live inside a
   * {@code NaturalHeightScrollPane} to render properly.
   *
   * TODO(b/28357327): Try to reduce the number of hacks and fragile code paths.
   * TODO: Determine why the background does not update on theme change.
   */
  private class CodePane extends EditorTextField {

    private static final int PAD = 5;
    private static final int MAX_HEIGHT = 500;

    // Scrollbar height, used for calculating preferred height when the horizontal scrollbar is present. This is somewhat of a hack in that
    // the value is set as a side effect of the scroll pane being instantiated. Unfortunately the pane is released before we can get access
    // so we cache the value (which should be the same across instantiations) each time the scrollpane is created.
    private int myScrollBarHeight = 0;

    public CodePane(StepElementData element) {
      // Default to JAVA rather than PLAIN_TEXT display for better support for quoted strings and properties.
      super(element.getCode() != null ? element.getCode() : "", myProject,
            element.getCodeType() != null ? element.getCodeType() : StdFileTypes.JAVA);
      // Tell the editor that it's a multiline editor, defaults to false and can't be overridden in ctor unless passing in a document
      // instead of text as first argument.
      setOneLineMode(false);
      // NOTE: Monospace must be used or the preferred width will be inaccurate (most likely due to line length calculations based on the
      // width of a sample character.
      setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
      ensureWillComputePreferredSize();
      getDocument().setReadOnly(true);

      // NO-OP to ensure that the editor is created, which has the side effect of instantiating the scroll pane, necessary to get the scroll
      // track height.
      getPreferredSize();

      int height = Math.min(MAX_HEIGHT, getActualPreferredHeight());
      // Preferred height is ignored for some reason, setting the the desired final height via minimum.
      setMinimumSize(new Dimension(1, height));
      setPreferredSize(new Dimension(getActualPreferredWidth(), height));
    }

    /**
     * Gets the actual preferred width, accounting for padding added to internal borders.
     */
    private int getActualPreferredWidth() {
      return (int)getPreferredSize().getWidth() + (2 * PAD);
    }

    /**
     * Gets the actual preferred height by calculating internal content heights and accounting for borders.
     *
     * HACK ALERT: EditorTextField does not return a reasonable preferred height and creating the editor without a file appears to leave
     * the internal editor instance null. As the internal editor would have been the best place to get the height, we fall back to
     * calculating the height of the contents by finding the line height and multiplying by the number of lines.
     */
    private int getActualPreferredHeight() {
      return (getFontMetrics(getFont()).getHeight() * getDocument().getLineCount()) + (2 * PAD) + myScrollBarHeight;
    }

    /**
     * HACK ALERT: The editor is not set after this class is instantiated, being released after it's created. Any necessary overrides to
     * the scroll pane (which resides in the editor) must be made while the scroll pane exists... so the override is placed in this method
     * which is called each time the editor is created.
     *
     * TODO: Only do this on the final editor creation, but ensure that we've got the track height when setting the preffered height in
     * the constructor.
     */
    @Override
    protected EditorEx createEditor() {
      EditorEx editor = super.createEditor();
      // Set the background manually as it appears to persist as an old color on theme change.
      editor.setBackgroundColor(UIUtils.getBackgroundColor());
      editor.addEditorMouseListener(new AutoTextSelectionListener(editor));

      // a11y improvement, disable traversal and add custom key listener for tab/shift + tab so the Tutorial can be navigated using keyboard only
      editor.getContentComponent().setFocusTraversalKeysEnabled(false);
      editor.getContentComponent().addKeyListener(new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {
        }

        @Override
        public void keyPressed(KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
          if (e.getKeyChar() != KeyEvent.VK_TAB) return;

          if (e.getModifiers() == KeyEvent.SHIFT_MASK) {
            editor.getContentComponent().transferFocusBackward();
            return;
          }

          editor.getContentComponent().transferFocus();
        }
      });

      JScrollPane scroll = editor.getScrollPane();

      // Set the background manually as it appears to persist as an old color on theme change.
      scroll.getViewport().setBackground(UIUtils.getBackgroundColor());
      if (scroll.getViewport().getView() != null) {
        scroll.getViewport().getView().setBackground(UIUtils.getBackgroundColor());
      }
      // TODO(b/28968368): When vertical scrolling over a scroll pane that only has horizontal scrolling, underlying system code will apply
      // the vertical scroll as horizontal. This results in side scrolling of the code when the user is just trying to scroll up/down
      // on the assistant window as a whole. While we can disable scrolling in the event that there is no vertical scrollbar, Mac hides
      // scrollbars by default and makes it impossible to scroll the code. We need to find a solution that addresses both issues. Note that
      // the prior (removed) shim was to test {@code getActualPreferredHeight()} against MAX_HEIGHT,
      // setting {@code setWheelScrollingEnabled(false)} in that event. Whatever solution we use must not disable scrolling.

      // Add a custom listener to duplicate/bubble scroll events to the parent scroll context. This avoids the code samples capturing
      // the scroll events when a user is simply trying to move up and down the tutorial.
      scroll.addMouseWheelListener(new CodeMouseWheelListener(scroll));

      // Set margins on the code scroll pane.
      scroll.setViewportBorder(BorderFactory.createMatteBorder(PAD, PAD, PAD, PAD, UIUtils.getBackgroundColor()));

      // Code typically overflows width, causing a horizontal scrollbar, and we need to account for the additional height so as to not
      // occlude the last line in the code sample. Value is used in the constructor so this method _must_ be triggered at least once prior
      // to setting minimum and preferred heights.
      // HACK ALERT: In Mac, with scrollbars always on, this value is reported as 2px less than it is. Adding 2px for safety.
      myScrollBarHeight = scroll.getHorizontalScrollBar().getPreferredSize().height + 2;

      // Set the scrollbars to show if the content overflows.
      // TODO(b/28357327): Why isn't this the default...
      scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      // Due to some unidentified race condition in calculations, we default to being partially scrolled. Reset the scrollbars.
      JScrollBar verticalScrollBar = scroll.getVerticalScrollBar();
      JScrollBar horizontalScrollBar = scroll.getHorizontalScrollBar();
      verticalScrollBar.setValue(verticalScrollBar.getMinimum());
      horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());

      return editor;
    }

    /**
     * Listener to defeat Swing's default behavior of scrollpanes not bubbling scroll events even when scroll to their max in the
     * given direction. This class causes the behavior to be similar to html scroll blocks.
     */
    class CodeMouseWheelListener implements MouseWheelListener {

      private JScrollBar myScrollBar;
      private int myLastScrollOffset = 0;
      private JScrollPane myParentScrollPane;
      private JScrollPane currentScrollPane;

      public CodeMouseWheelListener(JScrollPane scroll) {
        currentScrollPane = scroll;
        myScrollBar = currentScrollPane.getVerticalScrollBar();
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        JScrollPane parent = getParentScrollPane();
        // If we're not in a nested context, remove on first event capture. Note that parent is null in ctor so this safety check must
        // occur at a later time (ie, now).
        if (parent == null) {
          currentScrollPane.removeMouseWheelListener(this);
          return;
        }

        /*
         * Only dispatch if we have reached top/bottom on previous scroll.
         */
        int terminalValue = e.getWheelRotation() < 0 ? 0 : getMax();
        if (myScrollBar.getValue() == terminalValue && myLastScrollOffset == terminalValue) {
          // Clone the event since this pane already consumes it.
          parent.dispatchEvent(cloneEvent(e));
        }
        myLastScrollOffset = myScrollBar.getValue();
      }

      private JScrollPane getParentScrollPane() {
        if (myParentScrollPane == null) {
          Component parent = getParent();
          while (!(parent instanceof JScrollPane) && parent != null) {
            parent = parent.getParent();
          }
          myParentScrollPane = (JScrollPane)parent;
        }
        return myParentScrollPane;
      }

      private int getMax() {
        return myScrollBar.getMaximum() - myScrollBar.getVisibleAmount();
      }

      /**
       * Clones the mousewheel event so that it's not treated as consumed when dispatched to the parent.
       */
      private MouseWheelEvent cloneEvent(MouseWheelEvent e) {
        return new MouseWheelEvent(getParentScrollPane(),
                                   e.getID(),
                                   e.getWhen(),
                                   e.getModifiers(),
                                   e.getX(),
                                   e.getY(),
                                   e.getClickCount(),
                                   e.isPopupTrigger(),
                                   e.getScrollType(),
                                   e.getScrollAmount(),
                                   e.getWheelRotation());
      }
    }
  }
}
