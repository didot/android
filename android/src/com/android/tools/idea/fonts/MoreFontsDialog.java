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
package com.android.tools.idea.fonts;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontSource;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.assistant.view.UIUtils;
import com.android.tools.idea.ui.SearchField;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.properties.swing.SelectedListValueProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.Validator.Result;
import com.android.tools.idea.ui.validation.Validator.Severity;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.ide.common.fonts.FontFamilyKt.FILE_PROTOCOL_START;
import static com.android.ide.common.fonts.FontFamilyKt.HTTPS_PROTOCOL_START;

/**
 * Font selection dialog, which displays and causes the font cache to be populated.
 */
public class MoreFontsDialog extends DialogWrapper {
  private static final float FONT_SIZE_IN_LIST = 16f;
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 5;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 10;
  private static final int DEFAULT_HEIGHT = JBUI.scale(400);
  private static final int DEFAULT_WIDTH = JBUI.scale(600);
  private static final int MIN_FONT_LIST_HEIGHT = JBUI.scale(200);
  private static final int MIN_FONT_LIST_WIDTH = JBUI.scale(250);
  private static final int MIN_FONT_PREVIEW_HEIGHT = JBUI.scale(200);
  private static final int MIN_FONT_PREVIEW_WIDTH = JBUI.scale(150);
  private static final int DESCENDER_SPACE = JBUI.scale(4);

  private final FontListModel myModel;
  private final DefaultListModel<FontDetail> myDetailModel;
  private final FontFamilyCreator myFontCreator;
  private final ResourceResolver myResolver;
  private final StringProperty myNewFontName;
  private final SelectedListValueProperty<FontFamily> mySelectedFontFamily;
  private SearchField mySearchField;
  private JBList<FontFamily> myFontList;
  private JComboBox<String> myProvider;
  private JPanel myContentPanel;
  private JBScrollPane myFontListScrollPane;
  private JBList<FontDetail> myFontDetailList;
  private JBLabel myFontLabel;
  private JPanel myPreviewPanel;
  private JPanel myFontListPanel;
  private JPanel myCreateParams;
  private JTextField myFontNameEditor;
  private JBLabel myFontName;
  private JPanel myDownloadable;
  private JRadioButton myMakeDownloadable;
  private ValidatorPanel myValidatorPanel;
  private FontFamily myLastSelectedFont;
  private String myResultingFont;

  private void createUIComponents() {
    myContentPanel = new JPanel();
    mySearchField = new SearchField(false);
    myValidatorPanel = new ValidatorPanel(myDisposable, new JPanel());
  }

  public MoreFontsDialog(@NotNull AndroidFacet facet, @NotNull ResourceResolver resolver, @Nullable String currentValue) {
    super(facet.getModule().getProject());
    setTitle("Resources");
    myResolver = resolver;
    myContentPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    myFontList.setMinimumSize(new Dimension(MIN_FONT_LIST_WIDTH, MIN_FONT_LIST_HEIGHT));
    myFontDetailList.setMinimumSize(new Dimension(MIN_FONT_PREVIEW_WIDTH, MIN_FONT_PREVIEW_HEIGHT));
    myModel = new FontListModel(resolver);
    myModel.setRepopulateListener(this::repopulated);
    myDetailModel = new DefaultListModel<>();
    myCreateParams.setLayout(createGroupLayoutForCreateParams());
    myFontNameEditor.setVisible(false);
    myDownloadable.setVisible(false);
    myFontList.setCellRenderer(new FontFamilyRenderer());
    // We want to set fixed cell height and width. This makes the list render much faster.
    myFontList.setFixedCellHeight(computeFontHeightInFontList(myFontList));
    myFontList.setFixedCellWidth(MIN_FONT_LIST_WIDTH + JBUI.scale(DESCENDER_SPACE));
    //noinspection unchecked
    myFontList.setModel(myModel);
    myFontListScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    JScrollBar scrollBar = myFontListScrollPane.getVerticalScrollBar();
    scrollBar.setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    scrollBar.setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);
    myFontDetailList.setCellRenderer(new FontDetailRenderer());
    myFontDetailList.setModel(myDetailModel);

    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent event) {
        myModel.setFilter(mySearchField.getText().trim());
      }
    });
    myFontList.addListSelectionListener(event -> fontListSelectionChanged());
    myFontDetailList.addListSelectionListener(event -> fontDetailSelectionChanged());
    myFontCreator = new FontFamilyCreator(facet);
    myContentPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent event) {
        Insets fontListInsets = myFontListPanel.getBorder().getBorderInsets(myFontListPanel);
        int width = (myContentPanel.getWidth() - myFontLabel.getWidth()) / 2 - fontListInsets.left - fontListInsets.right;
        myFontList.setFixedCellWidth(Math.min(MIN_FONT_LIST_WIDTH, width));
        myPreviewPanel.setPreferredSize(new Dimension(width, MIN_FONT_PREVIEW_HEIGHT));
      }
    });
    myProvider.addItem("Google Fonts");
    myProvider.setSelectedIndex(0);
    myNewFontName = new StringValueProperty();
    mySelectedFontFamily = new SelectedListValueProperty<>(myFontList);
    bindComponents();
    addValidators();
    if (currentValue != null) {
      myFontList.setSelectedValue(myModel.getFont(currentValue), true);
    }

    init();
  }

  @Nullable
  public String getResultingFont() {
    return myResultingFont;
  }

  @Override
  public void show() {
    if (myModel.getSize() == 0) {
      Messages.showErrorDialog("Please setup your SDK first. Make sure the folder is writable. Then try again.", "Font Cache Missing");
      return;
    }
    super.show();
  }

  @Override
  protected void doOKAction() {
    FontFamily family = myFontList.getSelectedValue();
    if (family == null) {
      Messages.showErrorDialog(myContentPanel, "Please select a font family on the left");
      return;
    }
    FontDetail detail = myFontDetailList.getSelectedValue();
    if (detail == null) {
      Messages.showErrorDialog(myContentPanel, "Please select a specific font among the previewed fonts on the right");
      return;
    }
    try {
      switch (family.getFontSource()) {
        case SYSTEM:
          myResultingFont = family.getName();
          break;
        case PROJECT:
          myResultingFont = "@font/" + family.getName();
          break;
        case DOWNLOADABLE:
          myResultingFont = myFontCreator.createFontFamily(detail, myFontNameEditor.getText(), myMakeDownloadable.isSelected());
          break;
        default:
          throw new RuntimeException("Unexpected font source " + family.getFontSource());
      }
    }
    catch (IOException ex) {
      Logger.getInstance(MoreFontsDialog.class).warn("Could not create font resource file", ex);
      Messages.showErrorDialog(myContentPanel, "Could not create font resource file");
      return;
    }
    super.doOKAction();  // close dialog
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "Downloadable.Font.Dialog.Size";
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  private static int computeFontHeightInFontList(@NotNull JComponent component) {
    return component.getFontMetrics(component.getFont().deriveFont(FONT_SIZE_IN_LIST)).getHeight();
  }

  private LayoutManager createGroupLayoutForCreateParams() {
    GroupLayout layout = new GroupLayout(myCreateParams);
    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(
      layout.createSequentialGroup()
        .addComponent(myFontName)
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(myFontNameEditor)
                    .addComponent(myDownloadable))
    );
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(myFontName)
                    .addComponent(myFontNameEditor))
        .addComponent(myDownloadable)
    );
    return layout;
  }

  private void bindComponents() {
    BindingsManager bindings = new BindingsManager();
    bindings.bindTwoWay(new TextProperty(myFontNameEditor), myNewFontName);
  }

  private void addValidators() {
    myValidatorPanel.registerValidator(myNewFontName, this::checkFontName);
    myValidatorPanel.registerValidator(mySelectedFontFamily, this::checkSelectedFontFamily);
    myValidatorPanel.registerValidator(myValidatorPanel.hasErrors(), this::updateOkButton);
  }

  @NotNull
  private Result checkFontName(@NotNull String fontName) {
    if (!myFontNameEditor.isVisible()) {
      return Result.OK;
    }
    if (myResolver.getProjectResource(ResourceType.FONT, fontName) != null) {
      return new Result(Severity.ERROR, "A font named: \"" + fontName + "\" already exists");
    }
    if (fontName.isEmpty()) {
      return new Result(Severity.ERROR, "A font name must be specified");
    }
    return Result.OK;
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  @NotNull
  private Result checkSelectedFontFamily(@NotNull Optional<FontFamily> font) {
    return Result.fromNullableMessage(myModel.getErrorMessage(font.orElse(null)));
  }

  @NotNull
  private Result updateOkButton(@NotNull Boolean hasErrors) {
    setOKActionEnabled(!hasErrors.booleanValue());
    return Result.OK;
  }

  private void fontListSelectionChanged() {
    FontFamily family = myFontList.getSelectedValue();
    if (Objects.equals(family, myLastSelectedFont)) {
      // Often we get multiple selection notifications. Avoid multiple downloads of the same files:
      return;
    }
    if (family == null || family.getFontSource() == FontSource.HEADER) {
      myFontName.setText("");
      myFontNameEditor.setVisible(false);
      myDownloadable.setVisible(false);
      myDetailModel.clear();
      setOKActionEnabled(false);
    }
    else {
      Runnable downloaded = () -> selectedFontLoaded(family);
      FontDownloadService.download(Collections.singletonList(family), false, downloaded, downloaded);
      setOKActionEnabled(!myValidatorPanel.hasErrors().get());
    }
    myLastSelectedFont = family;
  }

  private void selectedFontLoaded(@NotNull FontFamily familyLoaded) {
    UIUtil.invokeLaterIfNeeded(() -> selectedFontLoadedEDT(familyLoaded));
  }

  private void selectedFontLoadedEDT(@NotNull FontFamily familyLoaded) {
    if (!familyLoaded.equals(myLastSelectedFont)) {
      return;
    }
    switch (familyLoaded.getFontSource()) {
      case SYSTEM:
      case PROJECT:
        myFontName.setText("Font Name: " + familyLoaded.getName());
        myFontNameEditor.setVisible(false);
        myFontNameEditor.setText("");
        myDownloadable.setVisible(false);
        break;
      case DOWNLOADABLE:
        myFontName.setText("Font Name:");
        myFontNameEditor.setText("");
        myFontNameEditor.setVisible(true);
        myDownloadable.setVisible(true);
        break;
      default:
        throw new RuntimeException("Unexpected font source " + familyLoaded.getFontSource());
    }
    myDetailModel.clear();
    for (FontDetail detail : familyLoaded.getFonts()) {
      myDetailModel.addElement(detail);
    }
    if (!familyLoaded.getFonts().isEmpty()) {
      myFontDetailList.setSelectedIndex(0);
    }
  }

  private void fontDetailSelectionChanged() {
    if (!myFontNameEditor.isVisible()) {
      return;
    }
    FontFamily family = myFontList.getSelectedValue();
    FontDetail detail = myFontDetailList.getSelectedValue();
    if (family != null && detail != null) {
      myFontNameEditor.setText(FontFamilyCreator.getFontName(detail));
    }
  }

  private void repopulated() {
    myFontList.setSelectedIndex(myModel.getElementIndex(myLastSelectedFont));
  }

  private static String findDisplayableTextForFont(@NotNull Font font, @NotNull String text) {
    if (font.canDisplayUpTo(text) < 0) {
      return text;
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 200; i < 0xFFFF; i++) {
      if (font.canDisplay((char)i)) {
        builder.append((char)i);
        if (builder.length() > 14) {
          break;
        }
      }
    }
    return builder.toString();
  }

  private static class FontFamilyRenderer extends ColoredListCellRenderer<FontFamily> {
    private final DownloadableFontCacheService myFontService;
    private final JLabel myTitle;

    private FontFamilyRenderer() {
      myFontService = DownloadableFontCacheService.getInstance();
      myTitle = new HeaderLabel();
      myTitle.setBorder(JBUI.Borders.empty(0, 35, 0, 5));
    }

    @Override
    public Component getListCellRendererComponent(@NotNull JList<? extends FontFamily> list,
                                                  @NotNull FontFamily fontFamily, int index, boolean selected, boolean hasFocus) {
      if (fontFamily.getFontSource() == FontSource.HEADER) {
        myTitle.setText(fontFamily.getName());
        return myTitle;
      }
      else {
        return super.getListCellRendererComponent(list, fontFamily, index, selected, hasFocus);
      }
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends FontFamily> list,
                                         @NotNull FontFamily fontFamily, int index, boolean selected, boolean hasFocus) {
      String text = fontFamily.getMenuName();
      Font font = myFontService.loadMenuFont(fontFamily);
      if (font != null && font.canDisplayUpTo(text) < 0) {
        setFont(font.deriveFont(FONT_SIZE_IN_LIST));
      }
      append(text);
      setIconTextGap(JBUI.scale(4));

      switch (fontFamily.getFontSource()) {
        case SYSTEM:
          setIcon(AndroidIcons.Android);
          break;
        case DOWNLOADABLE:
          setIcon(AndroidIcons.NeleIcons.Link);
          break;
        case PROJECT:
          if (fontFamily.getMenu().startsWith(FILE_PROTOCOL_START)) {
            setIcon(AndroidIcons.FontFile);
          }
          else if (fontFamily.getMenu().startsWith(HTTPS_PROTOCOL_START)) {
            setIcon(AndroidIcons.NeleIcons.Link);
          }
          else {
            setIcon(AllIcons.General.BalloonError);
          }
          break;
        default:
          break;
      }
    }
  }

  private static class FontDetailRenderer extends ColoredListCellRenderer<FontDetail> {
    private final DownloadableFontCacheService myFontService;

    private FontDetailRenderer() {
      myFontService = DownloadableFontCacheService.getInstance();
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends FontDetail> list,
                                         @NotNull FontDetail fontDetail, int index, boolean selected, boolean hasFocus) {
      String text = fontDetail.getStyleName();
      Font font = myFontService.loadDetailFont(fontDetail);
      if (font != null) {
        setFont(font.deriveFont(FONT_SIZE_IN_LIST));
        text = findDisplayableTextForFont(font, text);
      }
      mySelectionForeground = myForeground;
      setBackground(null);
      append(text);
      if (selected) {
        setIcon(AllIcons.Actions.Checked);
        setBorderInsets(new JBInsets(0, 0, 0, 0));
      }
      else {
        //noinspection UseDPIAwareInsets
        setBorderInsets(new Insets(0, AllIcons.Actions.Checked.getIconWidth() + getIconTextGap(), 0, 0));
      }
    }
  }

  private static class FontListModel extends DefaultListModel<FontFamily> {
    private static final int DOWNLOAD_SIZE = 25;

    private final DownloadableFontCacheService myFontService;
    private final ProjectFonts myProjectFonts;
    private final SpeedSearchComparator myComparator;
    private final List<FontFamily> myFilteredList;
    private Runnable myRepopulateListener;
    private String myFilter;
    private int myFirstLoadedFontIndex;
    private int myLoadedFontIndex;

    private FontListModel(@NotNull ResourceResolver resolver) {
      myFontService = DownloadableFontCacheService.getInstance();
      myProjectFonts = new ProjectFonts(resolver);
      myComparator = new SpeedSearchComparator();
      myFilteredList = new ArrayList<>();
      myFilter = "";
      populateModel();
      myLoadedFontIndex = -1;
      myFirstLoadedFontIndex = -1;
      myFontService.refresh(this::repopulateModel, null);
    }

    public void setRepopulateListener(@NotNull Runnable listener) {
      myRepopulateListener = listener;
    }

    public void setFilter(@NotNull String filter) {
      myFilter = filter;
      redoFiltering();
    }

    @Override
    public int getSize() {
      return myFilter.isEmpty() ? super.getSize() : myFilteredList.size();
    }

    @Override
    public FontFamily getElementAt(int index) {
      return myFilter.isEmpty() ? super.get(index) : myFilteredList.get(index);
    }

    public int getElementIndex(@Nullable FontFamily family) {
      if (family == null) {
        return -1;
      }
      return myFilter.isEmpty() ? super.indexOf(family) : myFilteredList.indexOf(family);
    }

    private void redoFiltering() {
      myFilteredList.clear();
      if (!myFilter.isEmpty()) {
        int size = super.getSize();
        for (int index = 0; index < size; index++) {
          FontFamily family = super.get(index);
          if (family.getFontSource() != FontSource.HEADER && myComparator.matchingFragments(myFilter, family.getName()) != null) {
            myFilteredList.add(family);
          }
        }
      }
      if (myRepopulateListener != null) {
        fireContentsChanged(this, 0, getSize() - 1);
        myRepopulateListener.run();
      }
    }

    private void repopulateModel() {
      UIUtil.invokeLaterIfNeeded(this::repopulateModelEDT);
    }

    private void repopulateModelEDT() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      boolean startLoad;
      startLoad = myLoadedFontIndex < 0;
      populateModel();
      if (startLoad) {
        myLoadedFontIndex = 0;
        loadRemainingFonts();
      }
    }

    private void populateModel() {
      clear();
      addFamilies("Project", myProjectFonts.getFonts());
      addFamilies("Android", myFontService.getSystemFontFamilies());
      addFamilies("Downloadable", myFontService.getFontFamilies());
      redoFiltering();
    }

    private void addFamilies(@NotNull String sectionName, @NotNull Collection<FontFamily> families) {
      if (families.isEmpty()) {
        return;
      }
      addElement(new FontFamily(FontProvider.EMPTY_PROVIDER, FontSource.HEADER, sectionName, "", "", Collections.emptyList()));
      for (FontFamily fontFamily : families) {
        addElement(fontFamily);
      }
    }

    @Nullable
    public FontFamily getFont(@NotNull String name) {
      return myProjectFonts.getFont(name);
    }

    @Nullable
    public String getErrorMessage(@Nullable FontFamily family) {
      return myProjectFonts.getErrorMessage(family);
    }

    private void loadRemainingFonts() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      List<FontFamily> fontsToDownload;
      int size = super.getSize();
      if (myLoadedFontIndex >= size) {
        myLoadedFontIndex = -1;
        myFirstLoadedFontIndex = -1;
        return; // Stop loading
      }
      fontsToDownload = new ArrayList<>();
      myFirstLoadedFontIndex = myLoadedFontIndex;
      while (myLoadedFontIndex < size && fontsToDownload.size() < DOWNLOAD_SIZE) {
        FontFamily family = super.get(myLoadedFontIndex++);
        if (family.getFontSource() == FontSource.DOWNLOADABLE) {
          fontsToDownload.add(family);
        }
      }
      FontDownloadService.download(fontsToDownload, true, this::loadDone, this::loadDone);
    }

    private void loadDone() {
      UIUtil.invokeLaterIfNeeded(this::loadDoneEDT);
    }

    private void loadDoneEDT() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      fireContentsChanged(this, myFirstLoadedFontIndex, myLoadedFontIndex);
      loadRemainingFonts();
    }
  }

  private static class HeaderLabel extends JBLabel {
    @Override
    protected void paintComponent(@NotNull Graphics graphics) {
      super.paintComponent(graphics);
      int width = getWidth();
      int height = getHeight() / 2;
      int textWidth = (int)getFontMetrics(getFont()).getStringBounds(getText(), graphics).getWidth();
      graphics.setColor(UIUtils.getSeparatorColor());
      graphics.drawLine(JBUI.scale(5), height, JBUI.scale(30), height);
      graphics.drawLine(textWidth + JBUI.scale(40), height, width - JBUI.scale(5), height);
    }
  }
}
