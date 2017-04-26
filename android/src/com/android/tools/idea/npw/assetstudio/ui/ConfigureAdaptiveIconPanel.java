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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.assetstudiolib.GraphicGenerator;
import com.android.ide.common.util.AssetUtil;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidAdaptiveIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidAdaptiveIconType;
import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsPanel;
import com.android.tools.idea.ui.properties.AbstractProperty;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.adapters.OptionalToValuePropertyAdapter;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.ui.properties.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.expressions.string.StringExpression;
import com.android.tools.idea.ui.properties.swing.*;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * A panel which allows the configuration of an icon, by specifying the source asset used to
 * generate the icon plus some other options. Note that this panel provides a superset of all
 * options used by each {@link AndroidAdaptiveIconType}, but the relevant options are shown / hidden based
 * on the exact type passed into the constructor.
 *
 * See also {@link GenerateIconsPanel} which owns a couple of these panels, one for each
 * {@link AndroidAdaptiveIconType}.
 */
public class ConfigureAdaptiveIconPanel extends JPanel implements Disposable, ConfigureIconView {

  /**
   * Source material icons are provided in a vector graphics format, but their default resolution
   * is very low (24x24). Since we plan to render them to much larger icons, we will up the detail
   * a fair bit.
   */
  private static final Dimension CLIPART_RESOLUTION = new Dimension(250, 250);
  /**
   * 108x108dp at XXXHDPI is 432x432px
   */
  private static final Dimension LAYER_RESOLUTION = new Dimension(432, 432);

  @NotNull private final List<ActionListener> myAssetListeners = Lists.newArrayListWithExpectedSize(1);

  @NotNull private final AndroidVersion myTargetSdkVersion;
  @NotNull private final BoolProperty myShowGridProperty;
  @NotNull private final BoolProperty myShowSafeZoneProperty;
  @NotNull private final AbstractProperty<Density> myPreviewDensityProperty;
  @NotNull private final AndroidAdaptiveIconGenerator myIconGenerator;
  @NotNull private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myForegroundActiveAssetBindings = new BindingsManager();
  @NotNull private final BindingsManager myBackgroundActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();

  /**
   * This panel presents a list of radio buttons (clipart, image, text), and whichever one is
   * selected sets the active asset.
   */
  private final ObjectProperty<BaseAsset> myForegroundActiveAsset;
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset;
  private final StringProperty myOutputName;
  private final StringProperty myForegroundLayerName;
  private final StringProperty myBackgroundLayerName;

  private final ImmutableMap<JRadioButton, ? extends AssetComponent> myForegroundAssetPanelMap;

  private final Map<GraphicGenerator.Shape, String> myShapeNames = ImmutableMap.of(
    GraphicGenerator.Shape.NONE, "None",
    GraphicGenerator.Shape.CIRCLE, "Circle",
    GraphicGenerator.Shape.SQUARE, "Square",
    GraphicGenerator.Shape.VRECT, "Vertical",
    GraphicGenerator.Shape.HRECT, "Horizontal");

  private JPanel myRootPanel;
  private JBLabel myOutputNameLabel;
  private JTextField myOutputNameTextField;

  private JPanel myForegroundAllOptionsPanel;
  private JRadioButton myForegroundClipartRadioButton;
  private JRadioButton myForegroundTextRadioButton;
  private JRadioButton myForegroundImageRadioButton;
  private JRadioButton myForegroundTrimmedRadioButton;
  private JPanel myForegroundTrimOptionsPanel;
  private JSlider myForegroundResizeSlider;
  private JLabel myForegroundResizeValueLabel;
  private JPanel myForegroundAssetRadioButtonsPanel;
  private JPanel myForegroundResizeSliderPanel;
  private JTextField myForegroundLayerNameTextField;
  private JPanel myForegroundColorRowPanel;
  private ColorPanel myForegroundColorPanel;
  private JPanel mGenerateLegacyIconRadioButtonsPanel;
  private JRadioButton myGenerateLegacyIconYesRadioButton;
  private JBScrollPane myForegroundScrollPane;
  private JPanel myForegroundImageAssetRowPanel;
  private JPanel myForegroundClipartAssetRowPanel;
  private JPanel myForegroundTextAssetRowPanel;
  private ImageAssetBrowser myForegroundImageAssetBrowser;
  private VectorIconButton myForegroundClipartAssetButton;
  private TextAssetEditor myForegroundTextAssetEditor;
  private JBLabel myForegroundLayerNameLabel;
  private JLabel myForegroundAssetTypeLabel;
  private JBLabel myForegroundImagePathLabel;
  private JBLabel myForegroundClipartLabel;
  private JBLabel myForegroundTextLabel;
  private JBLabel myForegroundTrimLabel;
  private JBLabel myForegroundResizeLabel;
  private JBLabel myForegroundColorLabel;
  private JBLabel myGenerateLegacyIconLabel;

  private JPanel myBackgroundAllOptionsPanel;
  private JRadioButton myBackgroundImageRadioButton;
  private JRadioButton myBackgroundColorRadioButton;
  private JRadioButton myBackgroundTrimYesRadioButton;
  private JPanel myBackgroundTrimOptionsPanel;
  private JSlider myBackgroundResizeSlider;
  private JLabel myBackgroundResizeValueLabel;
  private JPanel myBackgroundAssetRadioButtonsPanel;
  private JPanel myBackgroundResizeSliderPanel;
  private JTextField myBackgroundLayerNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundTrimRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundResizeRowPanel;
  private JPanel myBackgroundColorRowPanel;
  private ColorPanel myBackgroundColorPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myGenerateRoundIconRowPanel;
  private JPanel myGenerateRoundIconRadioButtonsPanel;
  private JRadioButton myGenerateRoundIconYesRadioButton;
  private JBScrollPane myBackgroundScrollPane;
  private JComboBox<GraphicGenerator.Shape> myLegacyIconShapeComboBox;
  private JPanel myBackgroundImageAssetRowPanel;
  private ImageAssetBrowser myBackgroundImageAssetBrowser;
  private JBLabel myBackgroundLayerNameLabel;
  private JLabel myBackgroundAssetTypeLabel;
  private JBLabel myBackgroundImagePathLabel;
  private JBLabel myBackgroundTrimLabel;
  private JBLabel myBackgroundResizeLabel;
  private JBLabel myGenerateRoundIconLabel;
  private JBLabel myBackgroundColorLabel;
  private JBLabel myLegacyIconShapeLabel;
  private JBScrollPane myOtherIconsScrollPane;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myOtherIconsAllOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundAssetTypeSourcePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundAssetTypeSourcePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myLegacyIconShapePanelRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myOutputNamePanelRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private TitledSeparator myForegroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private TitledSeparator mySourceAssetTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundResizePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private TitledSeparator myBackgroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private TitledSeparator myBackgroundSourceAssetTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myGenerateLegacyIconRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundTrimPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myLegacyIconShapePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myGenerateWebIconRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JBLabel myGenerateWebIconLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myGenerateWebIconRadioButtonsPanel;
  private JRadioButton myGenerateWebIconYesRadioButton;
  private JRadioButton myBackgroundTrimNoRadioButton;
  private JBLabel myWebIconShapeLabel;
  private JComboBox<GraphicGenerator.Shape> myWebIconShapeComboBox;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myWebIconShapePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myWebIconShapePanelRow;

  private BoolProperty myIgnoreForegroundColor;
  private AbstractProperty<Color> myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private BoolProperty myGenerateLegacyIcon;
  private BoolProperty myGenerateRoundIcon;
  private BoolProperty myGenerateWebIcon;
  private AbstractProperty<GraphicGenerator.Shape> myLegacyIconShape;
  private AbstractProperty<GraphicGenerator.Shape> myWebIconShape;

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a combo box (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public ConfigureAdaptiveIconPanel(@NotNull Disposable disposableParent,
                                    @NotNull AndroidVersion minSdkVersion,
                                    @NotNull AndroidVersion targetSdkVersion,
                                    @NotNull BoolProperty showGridProperty,
                                    @NotNull BoolProperty showSafeZoneProperty,
                                    @NotNull AbstractProperty<Density> previewDensityProperty,
                                    @NotNull ValidatorPanel validatorPanel) {
    super(new BorderLayout());
    myTargetSdkVersion = targetSdkVersion;

    myShowGridProperty = showGridProperty;
    myShowSafeZoneProperty = showSafeZoneProperty;
    myPreviewDensityProperty = previewDensityProperty;
    myIconGenerator =
      (AndroidAdaptiveIconGenerator)AndroidAdaptiveIconType
        .createIconGenerator(AndroidAdaptiveIconType.ADAPTIVE, minSdkVersion.getApiLevel());
    myValidatorPanel = validatorPanel;

    DefaultComboBoxModel<GraphicGenerator.Shape> legacyShapesModel = new DefaultComboBoxModel<>();
    for (GraphicGenerator.Shape shape : myShapeNames.keySet()) {
      legacyShapesModel.addElement(shape);
    }
    myLegacyIconShapeComboBox.setRenderer(new ListCellRendererWrapper<GraphicGenerator.Shape>() {
      @Override
      public void customize(JList list, GraphicGenerator.Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
    myLegacyIconShapeComboBox.setModel(legacyShapesModel);
    myLegacyIconShapeComboBox.setSelectedItem(GraphicGenerator.Shape.SQUARE);

    DefaultComboBoxModel<GraphicGenerator.Shape> webShapesModel = new DefaultComboBoxModel<>();
    for (GraphicGenerator.Shape shape : myShapeNames.keySet()) {
      webShapesModel.addElement(shape);
    }
    myWebIconShapeComboBox.setRenderer(new ListCellRendererWrapper<GraphicGenerator.Shape>() {
      @Override
      public void customize(JList list, GraphicGenerator.Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
    myWebIconShapeComboBox.setModel(webShapesModel);
    myWebIconShapeComboBox.setSelectedItem(GraphicGenerator.Shape.SQUARE);

    myForegroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myForegroundScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myBackgroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myBackgroundScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myOtherIconsScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myOtherIconsScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myOutputName = new TextProperty(myOutputNameTextField);
    myForegroundLayerName = new TextProperty(myForegroundLayerNameTextField);
    myBackgroundLayerName = new TextProperty(myBackgroundLayerNameTextField);

    myForegroundAssetPanelMap = ImmutableMap.of(
      myForegroundImageRadioButton, myForegroundImageAssetBrowser,
      myForegroundClipartRadioButton, myForegroundClipartAssetButton,
      myForegroundTextRadioButton, myForegroundTextAssetEditor
    );
    myForegroundImageAssetBrowser.getAsset().targetSize().setValue(LAYER_RESOLUTION);
    myForegroundClipartAssetButton.getAsset().targetSize().setValue(LAYER_RESOLUTION);
    myForegroundTextAssetEditor.getAsset().targetSize().setValue(LAYER_RESOLUTION);
    myBackgroundImageAssetBrowser.getAsset().targetSize().setValue(LAYER_RESOLUTION);


    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets
    myOutputNameLabel.setLabelFor(myOutputNameTextField);

    myForegroundLayerNameLabel.setLabelFor(myForegroundLayerNameTextField);
    myForegroundAssetTypeLabel.setLabelFor(myForegroundAssetRadioButtonsPanel);
    myForegroundImagePathLabel.setLabelFor(myForegroundImageAssetBrowser);
    myForegroundClipartLabel.setLabelFor(myForegroundClipartAssetButton);
    myForegroundTextLabel.setLabelFor(myForegroundTextAssetEditor);
    myForegroundTrimLabel.setLabelFor(myForegroundTrimOptionsPanel);
    myForegroundResizeLabel.setLabelFor(myForegroundResizeSliderPanel);
    myForegroundColorLabel.setLabelFor(myForegroundColorPanel);
    myGenerateLegacyIconLabel.setLabelFor(mGenerateLegacyIconRadioButtonsPanel);

    myBackgroundLayerNameLabel.setLabelFor(myBackgroundLayerNameTextField);
    myBackgroundAssetTypeLabel.setLabelFor(myBackgroundAssetRadioButtonsPanel);
    myBackgroundImagePathLabel.setLabelFor(myBackgroundImageAssetBrowser);
    myBackgroundTrimLabel.setLabelFor(myBackgroundTrimOptionsPanel);
    myBackgroundResizeLabel.setLabelFor(myBackgroundResizeSliderPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);
    myGenerateRoundIconLabel.setLabelFor(myGenerateRoundIconRadioButtonsPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);
    myLegacyIconShapeLabel.setLabelFor(myLegacyIconShapeComboBox);
    myWebIconShapeLabel.setLabelFor(myWebIconShapeComboBox);

    // Default the active asset type to "clipart", it's the most visually appealing and easy to
    // play around with.
    VectorAsset clipartAsset = myForegroundClipartAssetButton.getAsset();
    clipartAsset.outputWidth().set(CLIPART_RESOLUTION.width);
    clipartAsset.outputHeight().set(CLIPART_RESOLUTION.height);
    myForegroundActiveAsset = new ObjectValueProperty<>(clipartAsset);
    myForegroundClipartRadioButton.setSelected(true);

    // Set a reasonable default path for the background image
    File sampleFile = createSampleBackgroundImage();
    if (sampleFile != null) {
      myBackgroundImageAssetBrowser.getAsset().imagePath().set(sampleFile);
    }
    myBackgroundImageAsset = new OptionalValueProperty<>(myBackgroundImageAssetBrowser.getAsset());

    // For the background layer, use a simple plain color by default
    //myBackgroundColorRadioButton.setSelected(true);
    myBackgroundImageRadioButton.setSelected(true);

    initializeListenersAndBindings();
    initializeValidators();

    Disposer.register(disposableParent, this);
    for (AssetComponent assetComponent : myForegroundAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    add(myRootPanel);
  }

  /**
   * Copy sample image resource to local file system so it can be imported as a file
   */
  @Nullable
  private static File createSampleBackgroundImage() {
    try {
      BufferedImage image = GraphicGenerator.getStencilImage("/images/adaptive_icons_samples/background.png");
      if (image == null) {
        return null;
      }
      Path sampleFile = getImageSamplesPath().resolve("background-layer.png");
      Files.createDirectories(sampleFile.getParent());
      ImageIO.write(image, "PNG", sampleFile.toFile());
      return sampleFile.toFile();
    }
    catch (IOException e) {
      return null;
    }
  }

  @NotNull
  private static Path getImageSamplesPath() {
    String userHome = System.getProperty("user.home");
    String path = null;
    if (SystemInfo.isWindows) {
      // On Windows, we need the localized "Documents" folder name
      path = FileSystemView.getFileSystemView().getDefaultDirectory().getPath();
    }
    else if (SystemInfo.isMac) {
      // On OSX, "Documents" is not localized
      path = FileUtil.join(userHome, "Documents");
    }
    else if (SystemInfo.isLinux) {
      // On Linux, there is no standard "Documents" folder, so use the home folder
      path = userHome;
    }
    if (StringUtil.isEmpty(path)) {
      throw new RuntimeException("Platform is not supported");
    }
    return Paths.get(path, "AndroidStudio", "ImageAssets", "Samples");
  }



  private void initializeListenersAndBindings() {
    final BoolProperty foregroundTrimmed = new SelectedProperty(myForegroundTrimmedRadioButton);
    final BoolProperty backgroundTrimmed = new SelectedProperty(myBackgroundTrimYesRadioButton);

    final IntProperty foregroundResizePercent = new SliderValueProperty(myForegroundResizeSlider);
    final StringProperty foregroundResizeValueString = new TextProperty(myForegroundResizeValueLabel);
    myGeneralBindings.bind(foregroundResizeValueString, new FormatExpression("%d %%", foregroundResizePercent));

    final IntProperty backgroundResizePercent = new SliderValueProperty(myBackgroundResizeSlider);
    final StringProperty backgroundResizeValueString = new TextProperty(myBackgroundResizeValueLabel);
    myGeneralBindings.bind(backgroundResizeValueString, new FormatExpression("%d %%", backgroundResizePercent));

    myIgnoreForegroundColor = new SelectedProperty(myForegroundImageRadioButton);
    myForegroundColor = new OptionalToValuePropertyAdapter<>(new ColorProperty(myForegroundColorPanel));
    myBackgroundColor = new OptionalToValuePropertyAdapter<>(new ColorProperty(myBackgroundColorPanel));
    myGenerateLegacyIcon = new SelectedProperty(myGenerateLegacyIconYesRadioButton);
    myGenerateRoundIcon = new SelectedProperty(myGenerateRoundIconYesRadioButton);
    myGenerateWebIcon = new SelectedProperty(myGenerateWebIconYesRadioButton);

    myLegacyIconShape = new OptionalToValuePropertyAdapter<>(new SelectedItemProperty<>(myLegacyIconShapeComboBox));
    myWebIconShape = new OptionalToValuePropertyAdapter<>(new SelectedItemProperty<>(myWebIconShapeComboBox));

    updateBindingsAndUiForActiveIconType();

    // Update foreground layer asset type depending on asset type radio buttons
    ActionListener radioSelectedListener = e -> {
      JRadioButton source = ((JRadioButton)e.getSource());
      AssetComponent assetComponent = myForegroundAssetPanelMap.get(source);
      myForegroundActiveAsset.set(assetComponent.getAsset());
    };
    myForegroundClipartRadioButton.addActionListener(radioSelectedListener);
    myForegroundImageRadioButton.addActionListener(radioSelectedListener);
    myForegroundTextRadioButton.addActionListener(radioSelectedListener);
    myForegroundImageAssetBrowser.getAsset().setImageImporter(image -> importImageAsset(image, myForegroundResizeSlider));

    // Update background asset depending on asset type radio buttons
    myBackgroundImageRadioButton.addActionListener(e -> myBackgroundImageAsset.setValue(myBackgroundImageAssetBrowser.getAsset()));
    myBackgroundColorRadioButton.addActionListener(e -> myBackgroundImageAsset.clear());

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = e -> fireAssetListeners();
    for (AssetComponent assetComponent : myForegroundAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }
    myBackgroundImageAssetBrowser.addAssetListener(assetPanelListener);
    myBackgroundImageAssetBrowser.getAsset().setImageImporter(image -> importImageAsset(image, myBackgroundResizeSlider));

    final Runnable onAssetModified = this::fireAssetListeners;
    myListeners
      .listenAll(foregroundTrimmed, foregroundResizePercent, myForegroundColor,
                 backgroundTrimmed, backgroundResizePercent, myBackgroundColor,
                 myGenerateLegacyIcon, myLegacyIconShape, myWebIconShape, myGenerateRoundIcon, myGenerateWebIcon)
      .with(onAssetModified);

    myListeners.listenAndFire(myForegroundActiveAsset, sender -> {
      myForegroundActiveAssetBindings.releaseAll();
      myForegroundActiveAssetBindings.bindTwoWay(foregroundTrimmed, myForegroundActiveAsset.get().trimmed());
      myForegroundActiveAssetBindings.bindTwoWay(foregroundResizePercent, myForegroundActiveAsset.get().scalingPercent());
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundColor, myForegroundActiveAsset.get().color());

      getIconGenerator().sourceAsset().setValue(myForegroundActiveAsset.get());
      onAssetModified.run();
    });

    // When switching between Image/Color for background, bind corresponding properties and regenerate asset (to be sure)
    myListeners.listenAndFire(myBackgroundImageAsset, sender -> {
      myBackgroundActiveAssetBindings.releaseAll();
      if (myBackgroundImageAsset.getValueOrNull() != null) {
        myBackgroundActiveAssetBindings.bindTwoWay(backgroundTrimmed, myBackgroundImageAsset.getValue().trimmed());
        myBackgroundActiveAssetBindings.bindTwoWay(backgroundResizePercent, myBackgroundImageAsset.getValue().scalingPercent());
      }

      getIconGenerator().backgroundImageAsset().setNullableValue(myBackgroundImageAsset.getValueOrNull());
      onAssetModified.run();
    });

    BooleanExpression isClipartOrText =
      new BooleanExpression(myForegroundActiveAsset) {
        @NotNull
        @Override
        public Boolean get() {
          return myForegroundClipartAssetButton.getAsset() == myForegroundActiveAsset.get() ||
                 myForegroundTextAssetEditor.getAsset() == myForegroundActiveAsset.get();
        }
      };

    /*
     * Hook up a bunch of UI <- boolean expressions, so that when certain conditions are met,
     * various components show/hide. This also requires refreshing the panel explicitly, as
     * otherwise Swing doesn't realize it should trigger a relayout.
     */
    ImmutableMap.Builder<BoolProperty, ObservableBool> layoutPropertiesBuilder = ImmutableMap.builder();
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundImageAssetRowPanel), new SelectedProperty(myForegroundImageRadioButton));
    layoutPropertiesBuilder
      .put(new VisibleProperty(myForegroundClipartAssetRowPanel), new SelectedProperty(myForegroundClipartRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundTextAssetRowPanel), new SelectedProperty(myForegroundTextRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundColorRowPanel), isClipartOrText);

    // Show either the image or the color UI controls
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundImageAssetRowPanel), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundColorRowPanel), myBackgroundImageAsset.isPresent().not());
    layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundTrimYesRadioButton), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundTrimNoRadioButton), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundResizeSlider), myBackgroundImageAsset.isPresent());

    layoutPropertiesBuilder.put(new EnabledProperty(myLegacyIconShapeComboBox), new SelectedProperty(myGenerateLegacyIconYesRadioButton));
    layoutPropertiesBuilder.put(new EnabledProperty(myWebIconShapeComboBox), new SelectedProperty(myGenerateWebIconYesRadioButton));

    ImmutableMap<BoolProperty, ObservableBool> layoutProperties = layoutPropertiesBuilder.build();
    for (Map.Entry<BoolProperty, ObservableBool> e : layoutProperties.entrySet()) {
      // Initialize everything off, as this makes sure the frame that uses this panel won't start
      // REALLY LARGE by default.
      e.getKey().set(false);
      myGeneralBindings.bind(e.getKey(), e.getValue());
    }
    myListeners.listenAll(layoutProperties.keySet()).with(() -> {
      SwingUtilities.updateComponentTreeUI(myForegroundAllOptionsPanel);
      SwingUtilities.updateComponentTreeUI(myBackgroundAllOptionsPanel);
    });
  }

  @NotNull
  private static BufferedImage importImageAsset(@NotNull BufferedImage image, @NotNull JSlider resizeSlider) {
    Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
    Rectangle layerRect = new Rectangle(0, 0, 108, 108);
    Density highestDpi = Density.XXXHIGH;
    float targetScaleFactor = GraphicGenerator.getMdpiScaleFactor(highestDpi);

    // Look for best matching Density
    for (Density density : new Density[] {Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, highestDpi}) {
      float scaleFactor = GraphicGenerator.getMdpiScaleFactor(density);
      Rectangle densityRect = AssetUtil.scaleRectangle(layerRect, scaleFactor);
      if (densityRect.contains(imageRect)) {
        resizeSlider.setValue(Math.round(100 * targetScaleFactor / scaleFactor));
        break;
      }
      else if (density == highestDpi) {
        // Image is too big for XXXHIGH, scale it down to XXXHIGH
        scaleFactor = AssetUtil.getRectangleInsideScale(imageRect, densityRect);
        resizeSlider.setValue(Math.round(100 * scaleFactor));
      }
    }

    return image;
  }

  private void initializeValidators() {
    // We use this property as a way to trigger the validation when the panel is shown/hidden
    // when the "output icon type" changes in our parent component.
    // For example, we only want to validate the API level (see below) if the user is trying
    // to create an adaptive icon (from this component).
    VisibleProperty isActive = new VisibleProperty(getRootComponent());

    // Validate the API level when our panel is active
    Expression<AndroidVersion> targetSdkVersion = new Expression<AndroidVersion>(isActive) {
      @NotNull
      @Override
      public AndroidVersion get() {
        return myTargetSdkVersion;
      }
    };
    myValidatorPanel.registerValidator(targetSdkVersion, targetSdk -> {
      if (isActive.get() && targetSdk.getFeatureLevel() < 26) {
        return new Validator.Result(Validator.Severity.ERROR, "Project must target API 26 or later to use adaptive icons");
      }
      else {
        return Validator.Result.OK;
      }
    });

    // Validate foreground layer name when our panel is active
    StringExpression foregroundName = new StringExpression(isActive, myForegroundLayerName) {
      @NotNull
      @Override
      public String get() {
        return myForegroundLayerName.get();
      }
    };
    myValidatorPanel.registerValidator(foregroundName, name -> {
      String trimmedName = name.trim();
      if (isActive.get() && trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Foreground layer name must be set");
      }
      else {
        return Validator.Result.OK;
      }
    });

    // Validate background layer name when our panel is active
    StringExpression backgroundName = new StringExpression(isActive, myBackgroundLayerName) {
      @NotNull
      @Override
      public String get() {
        return myBackgroundLayerName.get();
      }
    };
    myValidatorPanel.registerValidator(backgroundName, name -> {
      String trimmedName = name.trim();
      if (isActive.get() && trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Background layer name must be set");
      }
      else {
        return Validator.Result.OK;
      }
    });
  }

  /**
   * Return an icon generator which will create Android icons using the panel's current settings.
   */
  @Override
  @NotNull
  public AndroidAdaptiveIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  @NotNull
  @Override
  public JComponent getRootComponent() {
    return this;
  }

  /**
   * Add a listener which will be triggered whenever the asset represented by this panel is
   * modified in any way.
   */
  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myAssetListeners.add(listener);
  }

  @Override
  @NotNull
  public StringProperty outputName() {
    return myOutputName;
  }

  private void fireAssetListeners() {
    ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
    for (ActionListener assetListener : myAssetListeners) {
      assetListener.actionPerformed(e);
    }
  }

  private void updateBindingsAndUiForActiveIconType() {
    myOutputName.set(AndroidAdaptiveIconType.ADAPTIVE.toOutputName("name"));
    myForegroundLayerName.set("ic_image_foreground");
    myBackgroundLayerName.set("ic_image_background");

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myForegroundActiveAsset));
    myGeneralBindings.bind(myIconGenerator.name(), myOutputName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundImageAsset(), myBackgroundImageAsset);

    AndroidAdaptiveIconGenerator adaptiveIconGenerator = myIconGenerator;
    myGeneralBindings.bind(adaptiveIconGenerator.useForegroundColor(), myIgnoreForegroundColor.not());
    myGeneralBindings.bindTwoWay(myForegroundColor, adaptiveIconGenerator.foregroundColor());
    myGeneralBindings.bindTwoWay(myBackgroundColor, adaptiveIconGenerator.backgroundColor());
    myGeneralBindings.bindTwoWay(myGenerateLegacyIcon, adaptiveIconGenerator.generateLegacyIcon());
    myGeneralBindings.bindTwoWay(myGenerateRoundIcon, adaptiveIconGenerator.generateRoundIcon());
    myGeneralBindings.bindTwoWay(myGenerateWebIcon, adaptiveIconGenerator.generateWebIcon());
    myGeneralBindings.bindTwoWay(myLegacyIconShape, adaptiveIconGenerator.legacyIconShape());
    myGeneralBindings.bindTwoWay(myWebIconShape, adaptiveIconGenerator.webIconShape());
    myGeneralBindings.bindTwoWay(myShowGridProperty, adaptiveIconGenerator.showGrid());
    myGeneralBindings.bindTwoWay(myShowSafeZoneProperty, adaptiveIconGenerator.showSafeZone());
    myGeneralBindings.bindTwoWay(myPreviewDensityProperty, adaptiveIconGenerator.previewDensity());
    myGeneralBindings.bindTwoWay(adaptiveIconGenerator.foregroundLayerName(), myForegroundLayerName);
    myGeneralBindings.bindTwoWay(adaptiveIconGenerator.backgroundLayerName(), myBackgroundLayerName);
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myForegroundActiveAssetBindings.releaseAll();
    myBackgroundActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myAssetListeners.clear();
  }
}
