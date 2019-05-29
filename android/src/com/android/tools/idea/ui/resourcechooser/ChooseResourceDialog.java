// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.ui.resourcechooser;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.adtui.treegrid.TreeGridSpeedSearch;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ColorUtils;
import com.android.tools.idea.editors.theme.MaterialColorUtils;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.GraphicalResourceRendererEditor;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.rendering.HtmlBuilderHelper;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.*;
import com.android.tools.idea.ui.MaterialColors;
import com.android.tools.idea.ui.resourcechooser.groups.ResourceChooserGroup;
import com.android.tools.idea.ui.resourcechooser.groups.ResourceChooserGroups;
import com.android.tools.idea.ui.resourcechooser.icons.IconFactory;
import com.android.tools.idea.ui.resourcechooser.preview.*;
import com.android.tools.idea.ui.resourcechooser.util.SimpleTabUI;
import com.android.tools.idea.util.DefaultIgnorable;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformIcons;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import icons.StudioIcons;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidBaseLayoutRefactoringAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.android.SdkConstants.*;

/**
 * Resource Chooser, with previews. Based on ResourceDialog in the android-designer.
 * <p>
 * TODO: Perform validation (such as cyclic layout resource detection for layout selection)
 */
public class ChooseResourceDialog extends DialogWrapper {
  private static final String TYPE_KEY = "ResourceType";
  private static final String FOLDER_TYPE_KEY = "ResourceFolderType";
  private static final String GRID_MODE_KEY = "ResourceChooserGridMode";
  private static final String APP_NAMESPACE_LABEL = "Project";
  private static final int GRID_ICON_SIZE = JBUI.scale(50);
  private static final int GRID_CHECK_SIZE = JBUI.scale(8);
  private static final int GRID_CELL_SIZE = JBUI.scale(120);
  private static final int LIST_ICON_SIZE = JBUI.scale(28);
  private static final int LIST_CHECK_SIZE = JBUI.scale(5);
  private static final int LIST_CELL_HEIGHT = JBUI.scale(40);
  static final int TABLE_CELL_HEIGHT = JBUI.scale(30);
  private static final JBColor LIST_DIVIDER_COLOR = new JBColor(Gray._245, Gray._80);
  private static final JBInsets LIST_PADDING = JBUI.insets(7, 6);
  private static final JBDimension PANEL_PREFERRED_SIZE = JBUI.size(850, 620);
  private static final SimpleTextAttributes SEARCH_MATCH_ATTRIBUTES = new SimpleTextAttributes(null, null, null,
                                                                                               SimpleTextAttributes.STYLE_SEARCH_MATCH);
  private static final Action[] EMPTY_ACTIONS = new Action[0];
  private static final ResourceChooserGroup[] EMPTY_RESOURCE_CHOOSER_GROUPS = new ResourceChooserGroup[0];
  private static final Border GRID_SELECTION_BORDER = BorderFactory.createLineBorder(UIUtil.getListSelectionBackground());

  @NotNull private final Module myModule;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final Configuration myConfiguration;
  @Nullable private final XmlTag myTag;

  private final JComponent myContentPanel;
  private final JTabbedPane myTabbedPane;
  private final JPanel myAltPane; // Alternative used instead of tabbed pane if there is only one resource type
  private final JComponent myViewOption;
  private final String myResourceNameSuggestion;
  private final EnumSet<ResourceType> myTypes;
  private final String myInitialValue;
  private final boolean myInitialValueIsFramework;
  private final ResourceNameVisibility myResourceNameVisibility;
  /**
   * If true, we will display only colors but not color state lists.
   * This is useful when the resource picker
   * is being used to select a drawable since colors are valid but color state lists are not.
   */
  private final boolean myFilterColorStateLists;
  /**
   * If true, the list of resources to pick from, will include sample data resources
   */
  private final boolean myShowSampleDataPicker;
  private boolean myGridMode = PropertiesComponent.getInstance().getBoolean(GRID_MODE_KEY, false);

  // if we are picking a resource that can't be a color, then all these are null
  @Nullable private ResourceEditorTab myColorPickerPanel;
  @Nullable private ColorPicker myColorPicker;

  // we can ONLY ever have the state-list picker in {@link ResourceType#COLOR} or {@link ResourceType#DRAWABLE} mode.
  // We only ever need one stateList picker because Android can never allow picking both types for any attribute.
  @Nullable private ResourceEditorTab myStateListPickerPanel;
  @Nullable private StateListPicker myStateListPicker;
  @Nullable private ResourcePickerListener myResourcePickerListener;
  @NotNull private ImmutableMap<String, Color> myContrastColorsWithDescription = ImmutableMap.of();
  private boolean myIsBackgroundColor;
  private final SearchTextField mySearchField;
  private final boolean myHideLeftSideActions;
  private String myResultResourceName;
  private boolean myUseGlobalUndo;
  private final IconFactory myIconFactory;
  private RenderTask myRenderTask;
  private final MultiMap<ResourceType, String> myThemAttributes;

  /**
   * Condition used to filter elements based on the search text
   */
  private final Condition<ResourceChooserItem> myFilterCondition;


  /**
   * Creates a builder for a new resource chooser dialog
   */
  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for constructing a resource chooser
   */
  public static class Builder {
    private Module myModule;
    private Configuration myConfiguration;
    private XmlTag myTag;
    private XmlFile myFile;
    private boolean myIsFrameworkValue;
    private String myCurrentValue;
    private EnumSet<ResourceType> myTypes;
    private ResourceNameVisibility myResourceNameVisibility = ResourceNameVisibility.SHOW;
    private String myResourceNameSuggestion;
    private boolean myHideLeftSideActions;
    @Nullable private ResourceType myDefaultType;
    private boolean myFilterColorStateLists;
    private boolean myShowSampleDataPicker;

    private Builder() {
    }

    public Builder setModule(@NotNull Module module) {
      myModule = module;
      return this;
    }

    public Builder setTag(@Nullable XmlTag tag) {
      myTag = tag;
      if (myTag != null && myFile == null) {
        myFile = (XmlFile)myTag.getContainingFile();
      }
      return this;
    }

    public Builder setFile(@Nullable XmlFile file) {
      myFile = file;
      return this;
    }

    public Builder setIsFrameworkValue(boolean frameworkValue) {
      myIsFrameworkValue = frameworkValue;
      return this;
    }

    public Builder setCurrentValue(@Nullable String currentValue) {
      myCurrentValue = currentValue;
      return this;
    }

    public Builder setTypes(@NotNull Set<ResourceType> types) {
      myTypes = EnumSet.copyOf(types);
      return this;
    }

    public Builder setResourceNameVisibility(@NotNull ResourceNameVisibility resourceNameVisibility) {
      myResourceNameVisibility = resourceNameVisibility;
      return this;
    }

    public Builder setResourceNameSuggestion(@Nullable String resourceNameSuggestion) {
      if (resourceNameSuggestion != null &&
          (resourceNameSuggestion.startsWith(PREFIX_RESOURCE_REF) ||
           resourceNameSuggestion.startsWith(PREFIX_THEME_REF) ||
           resourceNameSuggestion.startsWith("#"))) {
        throw new IllegalArgumentException("invalid name suggestion " + resourceNameSuggestion);
      }

      myResourceNameSuggestion = resourceNameSuggestion;
      return this;
    }

    public Builder setHideLeftSideActions() {
      myHideLeftSideActions = true;
      return this;
    }

    public Builder setConfiguration(@Nullable Configuration configuration) {
      myConfiguration = configuration;
      return this;
    }

    /**
     * Configures the dialog to not display color state lists. Please not that this only means state lists
     * contained within the color resource type folder.
     * This setting is useful to filter those lists when we are selecting a drawable resource (for example ImageView src).
     * In those cases, we can filter the color state lists since they are not supported.
     */
    @NotNull
    public Builder setFilterColorStateLists(boolean shouldFilterColorStateLists) {
      myFilterColorStateLists = shouldFilterColorStateLists;
      return this;
    }

    @NotNull
    public ChooseResourceDialog build() {
      Configuration configuration = myConfiguration;
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      assert facet != null;

      if (configuration == null) {
        if (myFile != null && myFile.getVirtualFile() != null) {
          ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
          configuration = configurationManager.getConfiguration(myFile.getVirtualFile());
        }

        if (configuration == null) {
          configuration = ThemeEditorUtils.getConfigurationForModule(myModule);
        }
      }

      return new ChooseResourceDialog(facet, configuration, myTag, myTypes, myDefaultType, myCurrentValue, myIsFrameworkValue,
                                      myResourceNameVisibility, myResourceNameSuggestion, myHideLeftSideActions,
                                      myFilterColorStateLists, myShowSampleDataPicker);
    }

    @NotNull
    public Builder setDefaultType(@Nullable ResourceType defaultType) {
      myDefaultType = defaultType;
      return this;
    }

    /**
     * If true, the list of resources to pick from, will include sample data resources
     */
    @NotNull
    public Builder setShowSampleDataPicker(boolean picker) {
      myShowSampleDataPicker = picker;
      return this;
    }
  }

  public interface ResourcePickerListener {
    void resourceChanged(@Nullable String resource);
  }

  private ChooseResourceDialog(@NotNull AndroidFacet facet,
                               @NotNull Configuration configuration,
                               @Nullable XmlTag tag,
                               @NotNull EnumSet<ResourceType> types,
                               @Nullable ResourceType defaultType,
                               @Nullable String value,
                               boolean isFrameworkValue,
                               @NotNull ResourceNameVisibility resourceNameVisibility,
                               @Nullable String resourceNameSuggestion,
                               boolean hideLeftSideActions,
                               boolean filterColorStateLists,
                               boolean showSampleDataPicker) {
    super(facet.getModule().getProject());
    myModule = facet.getModule();
    myFacet = facet;
    myConfiguration = configuration;
    myTag = tag;
    myInitialValue = value;
    myInitialValueIsFramework = isFrameworkValue;
    myResourceNameVisibility = resourceNameVisibility;
    myShowSampleDataPicker = showSampleDataPicker;

    // Treat mipmaps as a type of drawable
    myTypes = types.clone();
    if (myTypes.contains(ResourceType.MIPMAP)) {
      myTypes.add(ResourceType.DRAWABLE);
      myTypes.remove(ResourceType.MIPMAP);
    }

    // You can specify a color in place of a drawable
    if (myTypes.contains(ResourceType.DRAWABLE) || types.contains(ResourceType.MIPMAP) && !types.contains(ResourceType.COLOR)) {
      myTypes.add(ResourceType.COLOR);
    }

    myHideLeftSideActions = hideLeftSideActions;
    myResourceNameSuggestion = resourceNameSuggestion;

    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;

    myThemAttributes = initializeThemeAttributes(configuration, resolver, facet);

    ResourceValue resValue = null;
    if (value != null) {
      resValue = resolver.findResValue(value, isFrameworkValue);
    }

    myViewOption = createViewOptions();
    myTabbedPane = initializeTabbedPane(defaultType);
    if (myTabbedPane == null) {
      myAltPane = new JPanel(new BorderLayout());
      myAltPane.setPreferredSize(PANEL_PREFERRED_SIZE);
      myAltPane.setBorder(JBUI.Borders.emptyLeft(12));
    }
    else {
      myAltPane = null;
    }

    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(myTabbedPane != null ? myTabbedPane : myAltPane);
    mySearchField = createSearchField();
    myContentPanel.add(createToolbar(), BorderLayout.NORTH);
    myFilterColorStateLists = filterColorStateLists;

    myFilterCondition = item -> {
      ResourcePanel panel = getSelectedPanel();
      final String text = mySearchField.getText();
      // If we need to do a color resolution, avoid doing it multiple times by caching the first result in the method
      List<Color> cachedColorResolution = null;

      if (myFilterColorStateLists && panel.getType() == ResourceType.COLOR) {
        cachedColorResolution = ResourceHelper.resolveMultipleColors(getResourceResolver(), item.getResourceValue(), myModule.getProject());
        if (cachedColorResolution.size() > 1) {
          // Filter color state lists
          return false;
        }
      }

      if (text.isEmpty()) {
        return true;
      }

      if (panel.getType() == ResourceType.COLOR && text.startsWith("#")) {
        assert item.getType() == ResourceType.COLOR;

        // For colors, we allow to search the exact value if the search starts with #
        // This allow to search all the project colors that match a give value.
        final Color color = ResourceHelper.parseColor(text);
        return (cachedColorResolution == null ?
                ResourceHelper.resolveMultipleColors(getResourceResolver(), item.getResourceValue(), myModule.getProject()) :
                cachedColorResolution).contains(color);
      }

      if (item.getType() == ResourceType.STRING) {
        // For string items, we check the search string against the string value
        // TODO: Cache on item!
        String string = ResourceHelper.resolveStringValue(getResourceResolver(), item.getResourceUrl());
        if (StringUtil.containsIgnoreCase(string, text)) {
          return true;
        }
      }

      // If the search ends with a space, use the exact text value
      if (text.endsWith(" ")) {
        return StringUtil.equalsIgnoreCase(item.getName(), text.trim()); // Text needs to be trimmed to match the item name
      }
      return StringUtil.containsIgnoreCase(item.getName(), text);
    };
    myIconFactory = new IconFactory(this::getRenderTask);

    setTitle("Resources");
    setupViewOptions();
    init();

    updateFilter();
    selectResourceValue(resValue);

    // we need to trigger this once before the window is made visible to update any extra labels
    doValidate();
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  private void selectResourceValue(@Nullable ResourceValue resValue) {
    // initial selection
    if (resValue != null) {
      ResourcePanel panel = getSelectedPanel();
      if (panel.select(resValue)) {
        return;
      }

      // Selection not found in the current panel: switch tab to show it

      ResourceType type;
      if (resValue instanceof StyleItemResourceValue) { // type is always null for StyleItemResourceValue
        type = ResolutionUtils.getAttrType((StyleItemResourceValue)resValue, myConfiguration);
      }
      else {
        type = resValue.getResourceType();
      }
      // panel is null if the reference is incorrect, e.g. "@sdfgsdfgs" (user error).
      // Also Sample Data does not have it's own panel and it's included as part of other panels.
      if (type != null && type != ResourceType.SAMPLE_DATA) {
        panel = getPanel(myTabbedPane, type);
        if (panel != null) {
          if (myTabbedPane != null) {
            myTabbedPane.setSelectedComponent(panel.myComponent.getParent());
          }
          if (!panel.select(resValue) && type == ResourceType.COLOR) {
            // You might have selected a private framework color; we *can* edit these
            panel.showPreview(null, true);
          }
        }
      }
    }
  }

  private static MultiMap<ResourceType, String> initializeThemeAttributes(@NotNull Configuration configuration,
                                                                          @NotNull ResourceResolver resolver,
                                                                          @NotNull AndroidFacet facet) {
    MultiMap<ResourceType, String> attrs = new MultiMap<>();
    String themeName = configuration.getTheme();
    for (StyleItemResourceValue item : ResolutionUtils.getThemeAttributes(resolver, themeName)) {
      ResourceType type = ResolutionUtils.getAttrType(item, configuration);
      if (type != null && ResourceHelper.isAccessibleInXml(item, facet)) {
        attrs.putValue(type, ResolutionUtils.getQualifiedItemAttrName(item));
      }
    }

    return attrs;
  }

  private boolean allowColors() {
    return myTypes.contains(ResourceType.COLOR);
  }

  private boolean allowDrawables() {
    return myTypes.contains(ResourceType.DRAWABLE) || myTypes.contains(ResourceType.MIPMAP);
  }

  @NotNull
  private JComponent createToolbar() {
    JComponent toolbar = Box.createHorizontalBox();
    toolbar.add(mySearchField);
    toolbar.add(Box.createHorizontalStrut(JBUI.scale(20)));
    toolbar.add(myViewOption);

    toolbar.add(Box.createHorizontalGlue());
    JBLabel addNew = new JBLabel("Add new resource");
    addNew.setIcon(PlatformIcons.COMBOBOX_ARROW_ICON);
    addNew.setHorizontalTextPosition(SwingConstants.LEFT);
    addNew.setIconTextGap(0);
    if (ScreenReader.isActive()) {
      addNew.setFocusable(true);
    }
    toolbar.add(addNew);
    MyAddNewResourceLabelListener listener = new MyAddNewResourceLabelListener();
    addNew.addMouseListener(listener);
    addNew.addKeyListener(listener);

    toolbar.setBorder(new CompoundBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, 0, 0, 1, 0), JBUI.Borders.empty(8)));
    return toolbar;
  }

  private JComponent createViewOptions() {
    ToggleAction listView = createListViewAction();
    ToggleAction gridView = createGridViewAction();
    DefaultActionGroup group = new DefaultActionGroup(listView, gridView);
    JComponent component = ActionManager.getInstance().createActionToolbar("ResourceViewOptionToolbar", group, true).getComponent();
    component.setBorder(null);
    component.setMaximumSize(new Dimension(JBUI.scale(100), component.getMaximumSize().height));
    return component;
  }

  /**
   * @param defaultType The type corresponding to the tab to select by default
   */
  @Nullable
  private JTabbedPane initializeTabbedPane(@Nullable ResourceType defaultType) {
    if (myTypes.size() <= 1) {
      return null;
    }

    //noinspection UndesirableClassUsage We install our own special UI, intellij stuff will break it
    JTabbedPane pane = new JTabbedPane(SwingConstants.LEFT);
    pane.setName("ResourceTypeTabs"); // for UI tests
    pane.setUI(new SimpleTabUI());

    List<ResourceType> sorted = Lists.newArrayList(myTypes);
    // Sort drawables above colors
    sorted.sort(Comparator.comparingInt(ChooseResourceDialog::typeRank));

    int defaultTypesIndex = 0;
    int currentTypeIndex = 0;
    for (ResourceType type : sorted) {
      // only show color state lists if we are not showing drawables
      JPanel container = new JPanel(new BorderLayout());
      container.setPreferredSize(PANEL_PREFERRED_SIZE);
      container.putClientProperty(ResourceType.class, type);
      pane.addTab(type.getDisplayName(), container);
      if (type.equals(defaultType)) {
        defaultTypesIndex = currentTypeIndex;
      }
      currentTypeIndex++;
    }

    pane.setSelectedIndex(defaultTypesIndex);
    pane.addChangeListener(e -> handleTabChange());

    return pane;
  }

  private static int typeRank(ResourceType type) {
    switch (type) {
      case DRAWABLE:
        return 0;
      case COLOR:
        return 1;
      default:
        return type.ordinal() + 2;
    }
  }

  private void handleTabChange() {
    ResourcePanel panel = getSelectedPanel();
    panel.configureList(myGridMode);
    updateFilter();
    setupViewOptions();
  }

  @NotNull
  private ResourcePanel getSelectedPanel() {
    if (myTabbedPane != null) {
      JPanel selectedComponent = (JPanel)myTabbedPane.getSelectedComponent();
      ResourceType type = (ResourceType)selectedComponent.getClientProperty(ResourceType.class);
      return getPanel(myTabbedPane, type);
    }
    else {
      // Just one type
      return getPanel(null, myTypes.iterator().next());
    }
  }

  private final Map<ResourceType, ResourcePanel> myTypeToPanels = Maps.newEnumMap(ResourceType.class);

  private ResourcePanel getPanel(@Nullable JTabbedPane tabbedPane, @NotNull ResourceType resourceType) {
    // All ResourceType requests for MIPMAP should be converted into a drawable instead
    ResourceType type = resourceType;
    if (type == ResourceType.MIPMAP) { // mipmaps are treated as drawables
      type = ResourceType.DRAWABLE;
    }

    ResourcePanel panel = myTypeToPanels.get(type);
    if (panel == null) {
      boolean includeFileResources = type != ResourceType.COLOR || !allowDrawables() || !myFilterColorStateLists;
      panel = new ResourcePanel(type, includeFileResources, myThemAttributes.get(type), myShowSampleDataPicker);
      panel.expandAll();

      JPanel container = myAltPane;
      if (container == null && tabbedPane != null) {
        for (int i = 0, n = tabbedPane.getComponentCount(); i < n; i++) {
          JPanel tab = (JPanel)tabbedPane.getComponentAt(i);
          if (tab.getClientProperty(ResourceType.class) == type) {
            container = tab;
            break;
          }
        }
      }
      if (container != null) {
        container.add(panel.myComponent, BorderLayout.CENTER);
        myTypeToPanels.put(type, panel);
      }
    }

    return panel;
  }

  @NotNull
  private ToggleAction createGridViewAction() {
    return new ToggleAction(null, "grid", StudioIcons.LayoutEditor.Palette.GRID_VIEW) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myGridMode;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setGridMode(state);
      }
    };
  }

  @NotNull
  private ToggleAction createListViewAction() {
    return new ToggleAction(null, "list", StudioIcons.LayoutEditor.Palette.LIST_VIEW) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return !myGridMode;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        setGridMode(!state);
      }
    };
  }

  @NotNull
  private AnAction createNewResourceValueAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ResourceType type = (ResourceType)getTemplatePresentation().getClientProperty(TYPE_KEY);
        createNewResourceValue(type);
      }
    };
  }

  @NotNull
  private AnAction createNewResourceFileAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ResourceFolderType type = (ResourceFolderType)getTemplatePresentation().getClientProperty(FOLDER_TYPE_KEY);
        createNewResourceFile(type);
      }
    };
  }

  @NotNull
  private AbstractAction createNewResourceAction() {
    return new AbstractAction("New Resource", AllIcons.General.ComboArrowDown) {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComponent component = (JComponent)e.getSource();
        ActionPopupMenu popupMenu = createNewResourcePopupMenu();
        popupMenu.getComponent().show(component, 0, component.getHeight());
      }
    };
  }

  @NotNull
  private AnAction createExtractStyleAction() {
    return new AnAction("Extract Style...") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        extractStyle();
      }
    };
  }

  @NotNull
  private AnAction createNewResourceReferenceAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ResourcePanel panel = getSelectedPanel();
        panel.showNewResource(panel.myReferencePanel);
      }
    };
  }

  @NotNull
  private SearchTextField createSearchField() {
    SearchTextField searchField = new SearchTextField(false) { // no history: interferes with arrow down to jump into the list
      @Override
      protected void showPopup() {
        // Turn off search popup; we're overriding the Down key to jump into the list instead
      }
    };
    searchField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        // Allow arrow down to jump directly into the list
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          e.consume();
          getSelectedPanel().selectFirst();
        }
      }
    });
    searchField.setMaximumSize(new Dimension(JBUI.scale(300), searchField.getMaximumSize().height));
    searchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateFilter();
      }
    });

    return searchField;
  }

  private void updateFilter() {
    final String text = mySearchField.getText();
    ResourcePanel panel = getSelectedPanel();
    panel.setShowFiltered(!text.isEmpty());

    if (text.isEmpty() && !myFilterColorStateLists) {
      // Optimization to just remove the filter when we do not needed at all
      if (panel.isFiltered()) {
        panel.setFilter(null);
      }
    }

    panel.setFilter(myFilterCondition);
  }

  private void initializeColorPicker(@Nullable String value,
                                     @NotNull final ResourceNameVisibility resourceNameVisibility,
                                     ResourceResolver resolver, ResourceValue resValue) {
    Color color = null;
    if (resValue != null) {
      color = ResourceHelper.resolveColor(resolver, resValue, myModule.getProject());
    }

    if (color == null) {
      color = ResourceHelper.parseColor(value);
    }
    myColorPicker = new ColorPicker(myDisposable, color, true, new ColorPickerListener() {
      @Override
      public void colorChanged(Color color) {
        notifyResourcePickerListeners(ResourceHelper.colorToString(color));
      }

      @Override
      public void closed(@Nullable Color color) {
      }
    });
    myColorPicker.pickARGB();

    myColorPickerPanel = new ResourceEditorTab(myModule, "Color", myColorPicker, resourceNameVisibility,
                                               ResourceFolderType.VALUES, true, ResourceType.COLOR) {
      @NotNull
      @Override
      public String doSave() {
        String value = ResourceHelper.colorToString(myColorPicker.getColor());
        if (getResourceNameVisibility() == ResourceNameVisibility.FORCE ||
            (getResourceNameVisibility() == ResourceNameVisibility.SHOW && !getSelectedPanel().myEditorPanel.getResourceName().isEmpty())) {
          value = saveValuesResource(getSelectedPanel().myEditorPanel.getResourceName(), value, getLocationSettings());
        }
        // else we use the value we got at the start of the method
        return value;
      }
    };
  }

  private void ensurePickersInitialized() {
    boolean allowDrawables = allowDrawables();
    boolean allowColors = allowColors();

    if (allowColors || allowDrawables) {
      if (myStateListPicker != null || myColorPicker != null) {
        return;
      }
      Configuration configuration = getConfiguration();
      ResourceResolver resolver = configuration.getResourceResolver();
      assert resolver != null;

      ResourceValue resValue = null;
      if (myInitialValue != null) {
        resValue = resolver.findResValue(myInitialValue, myInitialValueIsFramework);
      }

      final ResourceType stateListType;
      final ResourceFolderType stateListFolderType;
      if (allowDrawables) {
        stateListType = ResourceType.DRAWABLE;
        stateListFolderType = ResourceFolderType.DRAWABLE;
      }
      else {
        stateListType = ResourceType.COLOR;
        stateListFolderType = ResourceFolderType.COLOR;
      }

      initializeStateListPicker(configuration, resolver, resValue, stateListType, stateListFolderType);
      initializeColorPicker(myInitialValue, myResourceNameVisibility, resolver, resValue);
    }
  }

  private void initializeStateListPicker(@NotNull Configuration configuration,
                                         ResourceResolver resolver,
                                         ResourceValue resValue,
                                         final ResourceType stateListType, final ResourceFolderType stateListFolderType) {
    StateList stateList = null;
    if (resValue != null) {
      stateList = ResourceHelper.resolveStateList(resolver, resValue, myModule.getProject());
      if (stateList != null && stateList.getType() != stateListType) {
        // this is very strange, this means we have asked to open the resource picker to allow drawables but with a color state-list
        // or to 'not allow drawables', but with a drawables state-list, must be a user error, this should not normally happen.
        Logger.getInstance(ChooseResourceDialog.class).warn("StateList type mismatch " + stateList.getType() + " " + stateListType);
        stateList = null;
      }
    }

    myStateListPicker = new StateListPicker(stateList, myModule, configuration);
    myStateListPickerPanel = new ResourceEditorTab(myModule, "Statelist", myStateListPicker, ResourceNameVisibility.FORCE,
                                                   stateListFolderType, false, stateListType) {
      @Override
      @Nullable
      public ValidationInfo doValidate() {
        ValidationInfo error = super.doValidate();
        if (error == null) {
          int minDirectoriesApi = ThemeEditorUtils.getMinFolderApi(getLocationSettings().getDirNames(), myModule);
          error = myStateListPicker.doValidate(minDirectoriesApi);
        }
        return error;
      }

      @NotNull
      @Override
      public String doSave() {
        String stateListName = getSelectedPanel().myEditorPanel.getResourceName();
        Module module = getSelectedModule();
        VirtualFile resDir = getResourceDirectory();
        List<String> dirNames = getLocationSettings().getDirNames();
        ResourceFolderType resourceFolderType = ResourceFolderType.getFolderType(dirNames.get(0));
        assert resourceFolderType != null;
        ResourceType resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(resourceFolderType);

        Project project = module.getProject();
        List<VirtualFile> files = null;
        if (resDir == null) {
          AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", module.getName()));
        }
        else {
          if (resourceType != null) {
            files = AndroidResourceUtil.findOrCreateStateListFiles(project, resDir, resourceFolderType, resourceType,
                                                                   stateListName, dirNames);
          }
        }
        if (files != null) {
          assert myStateListPicker != null;
          StateList stateList1 = myStateListPicker.getStateList();
          assert stateList1 != null;
          AndroidResourceUtil.updateStateList(project, stateList1, files);
        }

        if (resourceFolderType == ResourceFolderType.COLOR) {
          return COLOR_RESOURCE_PREFIX + stateListName;
        }
        assert resourceFolderType == ResourceFolderType.DRAWABLE;
        return DRAWABLE_PREFIX + stateListName;
      }
    };
  }

  @NotNull
  Configuration getConfiguration() {
    return myConfiguration;
  }

  private void setupViewOptions() {
    myViewOption.setVisible(getSelectedPanel().supportsGridMode());
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    // will draw the line between the main panel and the action buttons.
    return DialogStyle.COMPACT;
  }

  public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription,
                                    boolean isBackground,
                                    boolean displayWarning) {
    ensurePickersInitialized();
    if (myColorPicker != null) {
      myColorPicker.setContrastParameters(contrastColorsWithDescription, isBackground, displayWarning);
    }
    if (myStateListPicker != null) {
      myStateListPicker.setContrastParameters(contrastColorsWithDescription, isBackground);
    }
    myContrastColorsWithDescription = contrastColorsWithDescription;
    myIsBackgroundColor = isBackground;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  public enum ResourceNameVisibility {
    /**
     * Show field, but do not force name to be used.
     */
    SHOW,

    /**
     * Force creation of named color.
     */
    FORCE
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    return getSelectedPanel().doValidate();
  }

  public void setResourcePickerListener(@Nullable ResourcePickerListener resourcePickerListener) {
    myResourcePickerListener = resourcePickerListener;
  }

  private void notifyResourcePickerListeners(@Nullable String resource) {
    if (myResourcePickerListener != null) {
      myResourcePickerListener.resourceChanged(resource);
    }
  }

  public void generateColorSuggestions(@NotNull Color primaryColor, @NotNull String attributeName) {
    List<Color> suggestedColors = null;
    switch (attributeName) {
      case MaterialColors.PRIMARY_MATERIAL_ATTR:
        suggestedColors = MaterialColorUtils.suggestPrimaryColors();
        break;
      case MaterialColors.PRIMARY_DARK_MATERIAL_ATTR:
        suggestedColors = MaterialColorUtils.suggestPrimaryDarkColors(primaryColor);
        break;
      case MaterialColors.ACCENT_MATERIAL_ATTR:
        suggestedColors = MaterialColorUtils.suggestAccentColors(primaryColor);
        break;
    }
    if (suggestedColors != null) {
      ensurePickersInitialized();
      assert myColorPicker != null;
      myColorPicker.setRecommendedColors(suggestedColors);
    }
  }

  private ActionPopupMenu createNewResourcePopupMenu() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    ResourcePanel panel = getSelectedPanel();
    ResourceType resourceType = panel.getType();

    ResourceFolderType folderType = FolderTypeRelationship.getNonValuesRelatedFolder(resourceType);
    if (folderType != null) {
      AnAction newFileAction = createNewResourceFileAction();
      newFileAction.getTemplatePresentation().setText("New " + folderType.getName() + " File...");
      newFileAction.getTemplatePresentation().putClientProperty(FOLDER_TYPE_KEY, folderType);
      actionGroup.add(newFileAction);
    }
    if (AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceType)) {
      String title = "New " + resourceType + " Value...";
      if (resourceType == ResourceType.LAYOUT) {
        title = "New Layout Alias";
      }
      AnAction newValueAction = createNewResourceValueAction();
      newValueAction.getTemplatePresentation().setText(title);
      newValueAction.getTemplatePresentation().putClientProperty(TYPE_KEY, resourceType);
      actionGroup.add(newValueAction);
    }
    if (myTag != null && ResourceType.STYLE.equals(resourceType)) {
      final boolean enabled = AndroidBaseLayoutRefactoringAction.getLayoutViewElement(myTag) != null &&
                              AndroidExtractStyleAction.doIsEnabled(myTag);
      AnAction extractStyleAction = createExtractStyleAction();
      extractStyleAction.getTemplatePresentation().setEnabled(enabled);
      actionGroup.add(extractStyleAction);
    }
    if (GraphicalResourceRendererEditor.COLORS_AND_DRAWABLES.contains(resourceType)) {
      AnAction newReferenceAction = createNewResourceReferenceAction();
      newReferenceAction.getTemplatePresentation().setText("New " + resourceType + " Reference...");
      actionGroup.add(newReferenceAction);
    }

    return actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, actionGroup);
  }

  private void createNewResourceValue(ResourceType resourceType) {
    ensurePickersInitialized();
    if (resourceType == ResourceType.COLOR && myColorPickerPanel != null) {
      getSelectedPanel().showNewResource(myColorPickerPanel);
      return;
    }

    CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(myModule, resourceType, null, null, true,
                                                                 null, null);
    dialog.setTitle("New " + StringUtil.capitalize(resourceType.getDisplayName()) + " Value Resource");
    if (!dialog.showAndGet()) {
      return;
    }

    Project project = myModule.getProject();
    final VirtualFile resDir = dialog.getResourceDirectory();
    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", myModule));
      return;
    }

    String fileName = dialog.getFileName();
    List<String> dirNames = dialog.getDirNames();
    String resValue = dialog.getValue();
    String resName = dialog.getResourceName();
    if (!AndroidResourceUtil.createValueResource(project, resDir, resName, resourceType, fileName, dirNames, resValue)) {
      return;
    }

    PsiDocumentManager.getInstance(myModule.getProject()).commitAllDocuments();

    myResultResourceName = "@" + resourceType.getName() + "/" + resName;
    close(OK_EXIT_CODE);
  }

  private void createNewResourceFile(ResourceFolderType folderType) {
    ensurePickersInitialized();

    // if we are not showing the stateList picker, and we do have a stateList in it, then we can open it to allow the user to edit it.
    if (myStateListPicker != null && myStateListPicker.getStateList() != null &&
        folderType == myStateListPicker.getStateList().getFolderType()) {
      assert myStateListPickerPanel != null;
      getSelectedPanel().showNewResource(myStateListPickerPanel);
      return;
    }

    XmlFile newFile = CreateResourceFileAction.createFileResource(myFacet, folderType, null, null, null, true, null, null, null);
    if (newFile != null) {
      String name = newFile.getName();
      int index = name.lastIndexOf('.');
      if (index != -1) {
        name = name.substring(0, index);
      }
      myResultResourceName = "@" + folderType.getName() + "/" + name;
      close(OK_EXIT_CODE);
    }
  }

  private void extractStyle() {
    assert myTag != null;
    final String resName = AndroidExtractStyleAction.doExtractStyle(myModule, myTag, false, null);
    if (resName == null) {
      return;
    }
    myResultResourceName = "@style/" + resName;
    close(OK_EXIT_CODE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  // These actions are placed in the "Add new resource" label's menu instead of in the dialog's own
  // actions (by overriding createLeftSideActions() with the below method body) such that they're not listed redundantly.
  @NotNull
  protected Action[] getCreateActions() {
    return !myHideLeftSideActions ? new Action[]{createNewResourceAction()} : EMPTY_ACTIONS;
  }

  /**
   * Expands the location settings panel
   */
  public void openLocationSettings() {
    ensurePickersInitialized();
    if (myColorPickerPanel != null) {
      myColorPickerPanel.setLocationSettingsOpen(true);
    }
    if (myStateListPickerPanel != null) {
      myStateListPickerPanel.setLocationSettingsOpen(true);
    }
    getSelectedPanel().myReferencePanel.setLocationSettingsOpen(true);
  }

  public String getResourceName() {
    return myResultResourceName;
  }

  @Override
  protected void doOKAction() {
    ResourcePanel resourcePanel = getSelectedPanel();
    ResourceEditorTab editor = resourcePanel.getCurrentResourceEditor();

    // we are about to close, and potentially create/edit resources, that may cause all sorts of refreshes, so lets clear any live preview values.
    notifyResourcePickerListeners(null);

    if (editor != null) {
      myResultResourceName = editor.doSave();
    }
    else {
      ResourceChooserItem item = resourcePanel.getSelectedItem();
      myResultResourceName = item != null ? item.getResourceUrl() : null;
    }
    super.doOKAction();
  }

  private void setGridMode(boolean gridMode) {
    if (gridMode != myGridMode) {
      myGridMode = gridMode;
      getSelectedPanel().configureList(myGridMode);
      PropertiesComponent.getInstance().setValue(GRID_MODE_KEY, gridMode, false);
    }
  }

  public void setUseGlobalUndo() {
    myUseGlobalUndo = true;
  }

  @Nullable
  private Icon getIcon(@NotNull ResourceChooserItem item, int size, int checkerboardSize, @Nullable Runnable onLoadComplete) {
    Icon cachedIcon = item.getIcon(size);
    if (cachedIcon != null) {
      return cachedIcon;
    }

    switch (item.getType()) {
      case COLOR:
      case DRAWABLE:
      case MIPMAP:
        Icon icon = null;
        String path = item.getPath();
        if (path != null) {
          icon = myIconFactory.createIconFromPath(size,
                                                  checkerboardSize,
                                                  true,
                                                  item.getPath());
        }

        if (icon == null) {
          icon = myIconFactory.createAsyncIconFromResourceValue(size,
                                                                checkerboardSize,
                                                                true,
                                                                item.getResourceValue(),
                                                                EmptyIcon.create(size),
                                                                onLoadComplete);
        }
        item.setIcon(icon);

        return icon;
      default:
    }

    return null;
  }

  @NotNull
  private ResourceSwatchComponent.SwatchIcon getSwatchIcon(@Nullable String name) {
    return StateListPicker.getSwatchIcon(name, getResourceResolver(), getRenderTask());
  }

  @NotNull
  private ResourceResolver getResourceResolver() {
    Configuration config = getConfiguration();
    ResourceResolver resolver = config.getResourceResolver();
    assert resolver != null;
    return resolver;
  }

  @NotNull
  private RenderTask getRenderTask() {
    if (myRenderTask == null) {
      myRenderTask = DrawableRendererEditor.configureRenderTask(myModule, getConfiguration());
      Disposer.register(getDisposable(), () -> myRenderTask.dispose());
      myRenderTask.setMaxRenderSize(150, 150); // don't make huge images here
    }
    return myRenderTask;
  }

  /**
   * Saves any value that can be saved into the values.xml file and does not require its own file.
   *
   * @param value of the resource being edited to be saved
   * @return the value that is returned by the resource chooser.
   */
  @NotNull
  private String saveValuesResource(@NotNull String name, @NotNull String value, @NotNull CreateXmlResourcePanel locationSettings) {
    ResourceType type = locationSettings.getType();
    String fileName = locationSettings.getFileName();
    List<String> dirNames = locationSettings.getDirNames();

    Project project = myModule.getProject();
    final VirtualFile resDir = locationSettings.getResourceDirectory();
    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", myModule.getName()));
    }
    else {
      if (!AndroidResourceUtil.changeValueResource(project, resDir, name, type, value, fileName, dirNames, myUseGlobalUndo)) {
        // Changing value resource has failed, one possible reason is that resource isn't defined in the project.
        // Trying to create the resource instead.
        AndroidResourceUtil.createValueResource(project, resDir, name, type, fileName, dirNames, value);
      }
    }
    return PREFIX_RESOURCE_REF + type + "/" + name;
  }

  @NotNull
  private static EnumSet<ResourceType> getAllowedTypes(@NotNull ResourceType type) {
    switch (type) {
      case COLOR:
        return GraphicalResourceRendererEditor.COLORS_ONLY;
      case DRAWABLE:
        return GraphicalResourceRendererEditor.DRAWABLES_ONLY;
      default:
        return EnumSet.of(type);
    }
  }

  private class ResourcePanel {

    private static final String NONE = "None";
    private static final String TEXT = "Text";
    private static final String EDITOR = "Editor";
    private static final String DRAWABLE = "Bitmap";
    private static final String TABLE = "Table";
    private static final String SAMPLE_DRAWABLE = "Sample Data";

    @NotNull public final JBSplitter myComponent;
    @Nullable private TreeGrid<ResourceChooserItem> myList;
    @Nullable private JBTable myTable;
    @NotNull private final JPanel myPreviewPanel;

    private JLabel myNoPreviewComponent;
    private JTextPane myHtmlTextArea;
    private EditResourcePanel myEditorPanel;
    @Nullable private ResourceDrawablePanel myDrawablePanel;
    @Nullable private SampleDrawablePanel mySampleImagePanel;
    @Nullable private ResourceTablePanel myTablePanel;

    private ResourceComponent myReferenceComponent;
    private ResourceEditorTab myReferencePanel;

    @NotNull private ResourceChooserGroup[] myGroups = new ResourceChooserGroup[0];
    @NotNull private final ResourceType myType;

    private ResourceValue mySelectedValue;

    /**
     * @param type                 The type of resource to show
     * @param includeFileResources See {@link ResourceItem#isFileBased()}
     * @param showSampleDataPicker True if sample data should be displayed in the panel
     */
    public ResourcePanel(@NotNull ResourceType type, boolean includeFileResources,
                         @NotNull Collection<String> attrs, boolean showSampleDataPicker) {
      myType = type;

      myComponent = new JBSplitter(false, 0.5f);
      // The JBLoadingPanel requires a disposable to be passed so it knows when to stop the animation thread.
      // We create a Disposable here that we dispose when we've finished loading everything.
      Disposable animationDisposable = Disposer.newDisposable();
      JBLoadingPanel loadingPanel = new JBLoadingPanel(new FlowLayout(), animationDisposable);
      myComponent.setFirstComponent(loadingPanel);
      loadingPanel.startLoading();
      myComponent.setSplitterProportionKey("android.resource_dialog_splitter");
      CompletableFuture.runAsync(() -> {
        List<ResourceChooserGroup> groups = Lists.newArrayListWithCapacity(4);
        if (showSampleDataPicker && StudioFlags.NELE_SAMPLE_DATA_UI.get()) {
          ResourceChooserGroup sampleDataItems = ResourceChooserGroups.createSampleDataGroup(type, myFacet);
          if (!sampleDataItems.isEmpty()) {
            groups.add(sampleDataItems);
          }
        }
        ResourceChooserGroup projectItems =
          ResourceChooserGroups.createResourceItemsGroup(APP_NAMESPACE_LABEL, type, myFacet, false, includeFileResources);
        if (!projectItems.isEmpty()) {
          groups.add(projectItems);
        }
        ResourceChooserGroup frameworkItems =
          ResourceChooserGroups.createResourceItemsGroup(ANDROID_NS_NAME, type, myFacet, true, includeFileResources);
        if (!frameworkItems.isEmpty()) {
          groups.add(frameworkItems);
        }
        ResourceChooserGroup themeItems = ResourceChooserGroups.createThemeAttributesGroup(type, myFacet, attrs);
        if (!themeItems.isEmpty()) {
          groups.add(themeItems);
        }
        myGroups = groups.toArray(EMPTY_RESOURCE_CHOOSER_GROUPS);

        JComponent firstComponent = createListPanel(myGroups);
        firstComponent.setPreferredSize(JBUI.size(200, 600));

        myComponent.setFirstComponent(firstComponent);
        Disposer.dispose(animationDisposable);
      }, PooledThreadExecutor.INSTANCE)
                       .thenRunAsync(() -> {
                         updateFilter();
                         select(mySelectedValue);
                       }, EdtExecutorService.getInstance());

      myPreviewPanel = new JPanel(new CardLayout());
      myPreviewPanel.setPreferredSize(JBUI.size(400, 600));
      myComponent.setSecondComponent(myPreviewPanel);

      showPreview(null);
    }

    @NotNull
    private JComponent createListPanel(@NotNull ResourceChooserGroup[] groups) {
      JComponent component;
      if (myType == ResourceType.DRAWABLE
          || myType == ResourceType.COLOR
          || myType == ResourceType.MIPMAP
          // Styles and IDs: no "values" to show
          || myType == ResourceType.STYLE
          || myType == ResourceType.ID) {
        AbstractTreeStructure treeContentProvider = new ResourceTreeContentProvider(groups);
        TreeGrid<ResourceChooserItem> list = new TreeGrid<>(treeContentProvider);
        new TreeGridSpeedSearch<>(list);
        list.addListSelectionListener(e -> {
          showPreview(getSelectedItem());
          notifyResourcePickerListeners(getValueForLivePreview());
        });
        component = myList = list;
        // setup default list look and feel
        configureList(myGridMode);
      }
      else {
        // Table view (strings, dimensions, etc
        final AbstractTableModel model = new ResourceTableContentProvider(groups);

        FilteringTableModel<ResourceChooserItem> tableModel = new FilteringTableModel<>(new AbstractTableModel() {
          @Override
          public int getRowCount() {
            return model.getRowCount();
          }

          @Override
          public int getColumnCount() {
            return model.getColumnCount();
          }

          @Override
          public Object getValueAt(int rowIndex, int columnIndex) {
            return model.getValueAt(rowIndex, columnIndex);
          }
        }, ResourceChooserItem.class);
        tableModel.refilter(); // Needed as otherwise the filtered list does not show any content.

        component = myTable = new JBTable(tableModel);
        component.setName("nameTable"); // for tests
        myTable.setFillsViewportHeight(true);
        myTable.setTableHeader(null);
        myTable.setBorder(null);
        TableColumnModel columnModel = myTable.getColumnModel();
        columnModel.getColumn(0).setHeaderValue("Key");
        columnModel.getColumn(1).setHeaderValue("Default Value");
        columnModel.getColumn(0).setCellRenderer(new ColoredTableCellRenderer() {
          @Override
          protected void customizeCellRenderer(JTable table,
                                               Object value,
                                               boolean isSelected,
                                               boolean hasFocus,
                                               int row,
                                               int column) {
            boolean isHeader = false;
            if (value instanceof ResourceChooserItem) {
              ResourceChooserItem item = (ResourceChooserItem)value;
              String string = item.getName();
              String filter = mySearchField.getText();
              if (!filter.isEmpty()) {
                int match = StringUtil.indexOfIgnoreCase(string, filter, 0);
                if (match != -1) {
                  append(string.substring(0, match));
                  append(string.substring(match, match + filter.length()), SEARCH_MATCH_ATTRIBUTES);
                  append(string.substring(match + filter.length()));
                }
                else {
                  append(string);
                }
              }
              else {
                append(string);
              }
            }
            else {
              isHeader = true;
              append(value.toString());
            }

            if (isHeader) {
              setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
              if (!isSelected) {
                setBackground(UIUtil.getLabelBackground());
              }
            }
            else {
              setFont(UIUtil.getLabelFont());
              if (!isSelected) {
                setBackground(table.getBackground());
              }
            }
          }
        });
        columnModel.getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
          @SuppressWarnings("AssignmentToMethodParameter") // for value
          @Override
          public Component getTableCellRendererComponent(JTable table,
                                                         Object value,
                                                         boolean isSelected,
                                                         boolean hasFocus,
                                                         int row,
                                                         int column) {
            if (value instanceof ResourceChooserItem) {
              value = StringUtil.shortenTextWithEllipsis(StringUtil.notNullize(((ResourceChooserItem)value).getDefaultValue()), 80, 10);
              setBackground(table.getBackground());
            }
            else {
              // Header node
              setBackground(UIUtil.getLabelBackground());
              value = "";
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          }
        });
        myTable.setRowHeight(TABLE_CELL_HEIGHT);
        myTable.setStriped(false);
        myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        myTable.getSelectionModel().addListSelectionListener(e -> {
          showPreview(getSelectedItem());
          notifyResourcePickerListeners(getValueForLivePreview());
        });
        myTable.setBorder(BorderFactory.createEmptyBorder());

        TableSpeedSearch speedSearch = new TableSpeedSearch(myTable);
        speedSearch.setClearSearchOnNavigateNoMatch(true);
      }

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          ResourceChooserItem selected = getSelectedItem();
          if (selected != null) {
            myResultResourceName = selected.getResourceUrl();
            close(OK_EXIT_CODE);
            return true;
          }
          return false;
        }
      }.installOn(component);

      JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(component, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                                  ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(BorderFactory.createEmptyBorder());
      scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
      scrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
      return scrollPane;
    }

    boolean isFiltered() {
      if (myList != null) {
        return myList.isFiltered();
      }
      else if (myTable != null) {
        // Not tracking this yet; err on the side of caution
        return true;
      }
      else {
        return false;
      }
    }

    void setFilter(@Nullable Condition<ResourceChooserItem> condition) {
      if (myList != null) {
        myList.setFilter(condition);
        if (condition != null) {
          // Select the only single item after filtering, if any
          myList.selectIfUnique();
        }
      }
      else if (myTable != null) {
        //noinspection unchecked
        ((FilteringTableModel<ResourceChooserItem>)myTable.getModel()).setFilter(condition);
        if (condition != null) {
          TableModel model = myTable.getModel();
          ResourceChooserItem single = null;
          for (int row = 0, rowCount = model.getRowCount(); row < rowCount; row++) {
            Object value = model.getValueAt(row, 0);
            if (value instanceof ResourceChooserItem) {
              if (single == null) {
                single = (ResourceChooserItem)value;
              }
              else {
                single = null;
                break;
              }
            }
          }
          if (single != null) {
            setSelectedItem(single);
          }
        }
      }
    }

    void selectFirst() {
      if (myList != null) {
        myList.selectFirst();
      }
      else if (myTable != null) {
        List<ResourceChooserItem> first = myGroups.length > 0 ? myGroups[0].getItems() : Collections.emptyList();
        if (!first.isEmpty()) {
          setSelectedItem(first.get(0));
          myTable.requestFocus();
        }
      }
    }

    private void showDrawableItem(ResourceChooserItem item) {
      if (myDrawablePanel == null) {
        myDrawablePanel = new ResourceDrawablePanel(myConfiguration, myFacet, myIconFactory);
        myPreviewPanel.add(myDrawablePanel, DRAWABLE);
      }
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      myDrawablePanel.select(item);
      layout.show(myPreviewPanel, DRAWABLE);
    }

    private void showSampleItem(@NotNull ResourceChooserItem.SampleDataItem item) {
      if (mySampleImagePanel == null) {
        mySampleImagePanel = new SampleDrawablePanel(myFacet.getModule());
        myPreviewPanel.add(mySampleImagePanel, SAMPLE_DRAWABLE);
      }
      mySampleImagePanel.select(item);
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, SAMPLE_DRAWABLE);
    }

    private void showTableItem(ResourceChooserItem item) {
      if (myTablePanel == null) {
        myTablePanel = new ResourceTablePanel(getModule(), ChooseResourceDialog.this::close, TABLE_CELL_HEIGHT);
        myPreviewPanel.add(myTablePanel.getPanel(), TABLE);
      }
      else {
        // Without this, selecting different tables (e.g. keep arrow down pressed) causes the splitter
        // to keep recomputing the allocations based on the preferred sizes of the children instead
        // of sticking with the current proportion
        myComponent.skipNextLayout();
      }
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      myTablePanel.select(item);
      layout.show(myPreviewPanel, TABLE);
    }

    private void showEditorPanel() {
      if (myEditorPanel == null) {
        myReferenceComponent = new ResourceComponent(myModule.getProject(), true);
        myReferenceComponent.addSwatchListener(e -> {
          String attributeValue = myReferenceComponent.getValueText();
          ResourceUrl attributeValueUrl = ResourceUrl.parse(attributeValue);
          boolean isFrameworkValue = attributeValueUrl != null && attributeValueUrl.isFramework();
          String nameSuggestion = attributeValueUrl != null ? attributeValueUrl.name : null;

          ChooseResourceDialog dialog = builder()
            .setModule(myReferencePanel.getSelectedModule())
            .setTypes(getAllowedTypes(myType))
            .setCurrentValue(attributeValue)
            .setIsFrameworkValue(isFrameworkValue)
            .setResourceNameVisibility(ResourceNameVisibility.FORCE)
            .setResourceNameSuggestion(nameSuggestion)
            .setConfiguration(getConfiguration())
            .build();

          if (myResourcePickerListener != null) {
            dialog.setResourcePickerListener(myResourcePickerListener);
          }
          if (!myContrastColorsWithDescription.isEmpty()) {
            dialog.setContrastParameters(myContrastColorsWithDescription, myIsBackgroundColor, true);
          }
          dialog.show();

          if (dialog.isOK()) {
            String resourceName = dialog.getResourceName();
            myReferenceComponent.setValueText(resourceName);
            myReferenceComponent.repaint();
          }
          else {
            // reset live preview to original value
            notifyResourcePickerListeners(myReferenceComponent.getValueText());
          }
        });
        myReferenceComponent.addTextDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
            // This is run inside a WriteAction and updateIcon may need an APP_RESOURCES_LOCK from AndroidFacet.
            // To prevent a potential deadlock, we call updateIcon in another thread.
            ApplicationManager.getApplication().invokeLater(() -> {
              updateReferenceSwatchIcon();
              notifyResourcePickerListeners(myReferenceComponent.getValueText());
            }, ModalityState.any());
          }
        });
        // TODO, what if we change module in the resource editor, we should update the auto complete to match
        myReferenceComponent.setCompletionStrings(ResourceHelper.getCompletionFromTypes(myFacet, getAllowedTypes(myType)));

        Box referenceComponentPanel = new Box(BoxLayout.Y_AXIS);
        referenceComponentPanel.setName("ReferenceEditor"); // for UI tests
        referenceComponentPanel.add(myReferenceComponent);
        referenceComponentPanel.add(Box.createVerticalGlue());
        myReferencePanel = new ResourceEditorTab(myModule, "Reference", referenceComponentPanel, ResourceNameVisibility.FORCE,
                                                 ResourceFolderType.VALUES, true, myType) {
          @Override
          @Nullable
          public ValidationInfo doValidate() {
            ValidationInfo error = super.doValidate();
            if (error == null) {
              int minDirectoriesApi = ThemeEditorUtils.getMinFolderApi(getLocationSettings().getDirNames(), myModule);
              IAndroidTarget target = getConfiguration().getRealTarget();
              assert target != null;
              final AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myModule);
              assert androidTargetData != null;
              error = myReferenceComponent.doValidate(minDirectoriesApi, androidTargetData);
            }
            return error;
          }

          @NotNull
          @Override
          public String doSave() {
            return saveValuesResource(myEditorPanel.getResourceName(), myReferenceComponent.getValueText(), getLocationSettings());
          }
        };

        myEditorPanel = new EditResourcePanel(myResourceNameSuggestion);
        myEditorPanel.addVariantActionListener(e -> {
          // user has selected a different variant for the current resource, so we need to display it
          getSelectedPanel().editResourceItem(myEditorPanel.getSelectedVariant());
        });

        myEditorPanel.addTab(myReferencePanel);
        ensurePickersInitialized();
        if (myType == ResourceType.COLOR) {
          assert myColorPickerPanel != null;
          myEditorPanel.addTab(myColorPickerPanel);
        }
        if (myStateListPicker != null && myStateListPicker.getStateList() != null && myType == myStateListPicker.getStateList().getType()) {
          assert myStateListPickerPanel != null;
          myEditorPanel.addTab(myStateListPickerPanel);
        }
        myPreviewPanel.add(myEditorPanel, EDITOR);
      }
      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, EDITOR);
    }

    private void updateReferenceSwatchIcon() {
      ResourceSwatchComponent.SwatchIcon icon = getSwatchIcon(myReferenceComponent.getValueText());
      if (icon instanceof ResourceSwatchComponent.ColorIcon) {
        ResourceSwatchComponent.ColorIcon colorIcon = (ResourceSwatchComponent.ColorIcon)icon;
        myReferenceComponent.setWarning(
          ColorUtils.getContrastWarningMessage(myContrastColorsWithDescription, colorIcon.getColor(), myIsBackgroundColor));
      }
      else {
        myReferenceComponent.setWarning(null);
      }
      myReferenceComponent.setSwatchIcon(icon);
      myReferenceComponent.repaint();
    }

    private void showNoPreview() {
      if (myNoPreviewComponent == null) {
        myNoPreviewComponent = new JLabel("No Preview");
        myNoPreviewComponent.setHorizontalAlignment(SwingConstants.CENTER);
        myNoPreviewComponent.setVerticalAlignment(SwingConstants.CENTER);
        myPreviewPanel.add(myNoPreviewComponent, NONE);
      }

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, NONE);
    }

    private void showHtml(String doc) {
      if (myHtmlTextArea == null) {
        myHtmlTextArea = new JTextPane();
        myHtmlTextArea.setEditable(false);
        myHtmlTextArea.setContentType(UIUtil.HTML_MIME);
        myHtmlTextArea.setMargin(JBUI.insets(8, 8, 8, 8));
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myHtmlTextArea, true);
        myPreviewPanel.add(scrollPane, TEXT);
      }

      // b/80221739 Remove all Left-to-Right and Right-to-Left escape characters before displaying this documentation
      doc = DefaultIgnorable.removeDefaultIgnorable(doc);

      myHtmlTextArea.setText(doc);
      HtmlBuilderHelper.fixFontStyles(myHtmlTextArea);
      myHtmlTextArea.getCaret().setDot(0);

      CardLayout layout = (CardLayout)myPreviewPanel.getLayout();
      layout.show(myPreviewPanel, TEXT);
    }

    @NotNull
    public ResourceType getType() {
      return myType;
    }

    /**
     * Determines if the given item is something we can edit (vs just select)
     */
    private boolean allowEditing(@Nullable ResourceChooserItem item) {
      if (item == null) {
        return false;
      }

      // Determine whether we allow editing. We allow editing if
      //  (1) it's a project item, and
      //  (2) it's a "complex" type (e.g. a state list) or a color.
      //  (3) it's not an attribute
      //
      // You can't edit bitmaps, framework resources, etc.
      if (item.isFramework() || item.isAttr()) {
        return false;
      }

      // Checking for is-framework isn't enough: we don't let you edit resources
      // from libraries (such as appcompat) either
      LocalResourceRepository repository = ResourceRepositoryManager.getProjectResources(myModule);
      assert repository != null;
      if (!repository.hasResources(ResourceNamespace.TODO(), item.getType(), item.getName())) {
        return false;
      }

      ResourceType type = item.getType();
      if (type == ResourceType.COLOR) {
        // All (project) colors can be edited
        return true;
      }

      // Some drawables can be edited (e.g. state lists). No other resource types for now.
      if (type == ResourceType.DRAWABLE) {
        if (item.isReference()) {
          return true;
        }
        Project project = myModule.getProject();
        StateList stateList = ResourceHelper.resolveStateList(getResourceResolver(), item.getResourceValue(), project);
        if (stateList != null) { // if this is not a state list, it may be just a normal color
          return true;
        }
        else {
          return false;
        }
      }

      return false;
    }

    public void showPreview(@Nullable ResourceChooserItem item) {
      showPreview(item, allowEditing(item));
    }

    public void showPreview(@Nullable ResourceChooserItem element, boolean allowEditor) {
      // TODO maybe have a element of "new Color" and "new StateList"

      if (element != null && element.isAttr()) {
        ResourceUrl url = ResourceUrl.parse(element.getResourceUrl());
        assert url != null;
        String doc = AndroidJavaDocRenderer.render(myModule, getConfiguration(), url);
        showHtml(doc);
        return;
      }

      if (allowEditor) {
        if ((myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE || myType == ResourceType.MIPMAP) && element != null) {
          LocalResourceRepository repository = ResourceRepositoryManager.getProjectResources(myModule);
          assert repository != null;
          boolean inProject = repository.hasResources(ResourceNamespace.TODO(), element.getType(), element.getName());
          if (inProject) {
            showEditorPanel();
            myEditorPanel.setResourceName(element.getName());
            ResourceItem defaultValue = setupVariants();
            if (defaultValue != null) {
              editResourceItem(defaultValue);
              return;
            }
          }
        }

        ensurePickersInitialized();
        if (element == null && myStateListPicker != null && myStateListPicker.getStateList() != null
            && myStateListPicker.getStateList().getType() == myType) {
          assert myStateListPickerPanel != null;
          showEditorPanel();
          myEditorPanel.setSelectedTab(myStateListPickerPanel);
          return;
        }

        if (element == null && myType == ResourceType.COLOR) {
          assert myColorPickerPanel != null;
          showEditorPanel();
          myEditorPanel.setSelectedTab(myColorPickerPanel);
          return;
        }
      }

      if (element == null || element.getType() == ResourceType.ID) {
        showNoPreview();
        return;
      }

      if (element.getType() == ResourceType.SAMPLE_DATA
          && element instanceof ResourceChooserItem.SampleDataItem
          && ((ResourceChooserItem.SampleDataItem)element).getResourceItem().getContentType() == SampleDataResourceItem.ContentType.IMAGE) {
        showSampleItem((ResourceChooserItem.SampleDataItem)element);
        return;
      }

      switch (myType) {
        case DRAWABLE:
        case MIPMAP:
        case COLOR:
          showDrawableItem(element);
          return;
        case STRING:
        case DIMEN:
        case BOOL:
          // TODO which other ones?
          showTableItem(element);
          return;
        default:
          // fall through to just do plain doc-pane rendering
      }

      String doc = AndroidJavaDocRenderer.render(myModule, getConfiguration(), element.getType(), element.getName(),
                                                 element.isFramework());
      if (doc != null) {
        showHtml(doc);
      }
      else {
        showNoPreview();
      }
    }

    public void editResourceItem(@NotNull ResourceItem selected) {
      ResourceValue resourceValue = selected.getResourceValue();
      assert resourceValue != null;

      @NotNull ResourceEditorTab resourceEditorTab;
      String value = resourceValue.getValue();
      if (value != null && (value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF))) {
        myReferenceComponent.setValueText(value);
        updateReferenceSwatchIcon();
        resourceEditorTab = myReferencePanel;
      }
      else {
        StateList stateList = ResourceHelper.resolveStateList(getResourceResolver(), resourceValue, myModule.getProject());
        if (stateList != null) { // if this is not a state list, it may be just a normal color
          ensurePickersInitialized();
          assert myStateListPickerPanel != null;
          assert myStateListPicker != null;

          if (stateList.getType() != myStateListPickerPanel.getLocationSettings().getType()) {
            Logger.getInstance(ChooseResourceDialog.class)
                  .warn("StateList type mismatch " + stateList.getType() + " " + myStateListPickerPanel.getLocationSettings().getType());
            showPreview(getSelectedItem(), false);
            return;
          }
          myStateListPicker.setStateList(stateList);
          if (myStateListPickerPanel.getFullPanel().getParent() == null) {
            myEditorPanel.addTab(myStateListPickerPanel);
          }
          resourceEditorTab = myStateListPickerPanel;
        }
        else {
          Color color = ResourceHelper.parseColor(resourceValue.getValue());
          if (color != null) { // if invalid color because of user error or a reference to another color
            ensurePickersInitialized();
            assert myColorPickerPanel != null;
            assert myColorPicker != null;
            myColorPicker.setColor(color);
            resourceEditorTab = myColorPickerPanel;
          }
          else {
            // we are an actual image, so we need to just display it.
            showPreview(getSelectedItem(), false);
            return;
          }
        }
      }

      myEditorPanel.setSelectedTab(resourceEditorTab);

      setLocationFromResourceItem(selected);
    }

    @Nullable
    public ValidationInfo doValidate() {
      final boolean okActionEnabled;
      ValidationInfo error = null;

      ResourceEditorTab editor = getCurrentResourceEditor();
      if (editor != null) {
        String overwriteResource = "";

        // if name is hidden, then we allow any value
        if (editor.getResourceNameVisibility() == ResourceNameVisibility.FORCE ||
            (editor.getResourceNameVisibility() == ResourceNameVisibility.SHOW && !myEditorPanel.getResourceName().isEmpty())) {
          IdeResourceNameValidator validator = editor.getValidator();
          String enteredName = myEditorPanel.getResourceName();
          if (validator.doesResourceExist(enteredName)) {
            ResourceType type = getSelectedPanel().getType();
            overwriteResource = String.format("Saving this %1$s will override existing resource %2$s.",
                                              StringUtil.toLowerCase(type.getDisplayName()), enteredName);
          }
          else {
            String errorText = validator.getErrorText(enteredName);
            if (errorText != null) {
              error = new ValidationInfo(errorText, myEditorPanel.getResourceNameField());
            }
          }

          // the name of the resource must have changed, lets re-load the variants.
          if (!overwriteResource.equals(myEditorPanel.getResourceNameMessage())) {
            ResourceItem defaultResourceItem = setupVariants();
            if (defaultResourceItem != null) {
              setLocationFromResourceItem(defaultResourceItem);
            }
          }
        }

        if (!overwriteResource.equals(myEditorPanel.getResourceNameMessage())) {
          myEditorPanel.setResourceNameMessage(overwriteResource);
        }

        if (error == null) {
          error = editor.doValidate();
        }

        okActionEnabled = error == null;
      }
      else {
        okActionEnabled = getSelectedItem() != null;
      }

      // Need to always manually update the setOKActionEnabled as the DialogWrapper
      // only updates it if we go from having a error string to not having one
      // or the other way round, but not if the error string state has not changed.
      setOKActionEnabled(okActionEnabled);

      return error;
    }

    private void setLocationFromResourceItem(@NotNull ResourceItem item) {
      VirtualFile virtualFile = ResourceHelper.getSourceAsVirtualFile(item);
      if (virtualFile == null) {
        assert false : "Item's source can not be null when selecting a resource item";
        return;
      }
      // TODO as we only show variants that are specific to the folderType, and we have different folderTypes for different Editor tabs, reset does not always work.
      // TODO CreateXmlResourcePanel should show all variants irrespective of folderType and we should have just 1 CreateXmlResourcePanel per EditResourcePanel.
      for (ResourceEditorTab editor : myEditorPanel.getAllTabs()) {
        editor.getLocationSettings().resetFromFile(virtualFile, myModule.getProject());
      }
    }

    /**
     * @return the default value for this config, otherwise the first value.
     */
    @Nullable
    private ResourceItem setupVariants() {
      LocalResourceRepository repository = ResourceRepositoryManager.getAppResources(myFacet);
      List<ResourceItem> resources = repository.getResources(ResourceNamespace.TODO(), myType, myEditorPanel.getResourceName());
      ResourceItem defaultValue = getConfiguration().getFullConfig().findMatchingConfigurable(resources);
      if (defaultValue == null && !resources.isEmpty()) {
        // we may not have ANY value that works in current config, then just pick the first one
        defaultValue = resources.get(0);
      }
      myEditorPanel.setVariant(resources, defaultValue);
      return defaultValue;
    }

    @Nullable
    public ResourceEditorTab getCurrentResourceEditor() {
      return myEditorPanel != null && myEditorPanel.isVisible() ? myEditorPanel.getSelectedTab() : null;
    }

    private boolean supportsGridMode() {
      return myType == ResourceType.COLOR || myType == ResourceType.DRAWABLE || myType == ResourceType.MIPMAP;
    }

    private void configureList(boolean gridView) {
      if (myList == null) {
        return;
      }
      if (gridView && supportsGridMode()) {
        configureGridList(myList);
      }
      else {
        configureVerticalList(myList);
      }
    }

    private void configureVerticalList(@NotNull TreeGrid<ResourceChooserItem> list) {
      ColoredListCellRenderer<ResourceChooserItem> listRenderer = new ColoredListCellRenderer<ResourceChooserItem>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList list, ResourceChooserItem value, int index, boolean selected,
                                             boolean hasFocus) {
          if (!hasFocus) {
            setBorder(new AbstractBorder() {
              @Override
              public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Color oldColor = g.getColor();
                g.setColor(LIST_DIVIDER_COLOR);
                int thickness = 1;
                g.fillRect(x, y + height - thickness, width, thickness);
                g.setColor(oldColor);
              }
            });
          }
          setIpad(LIST_PADDING);

          // TODO: show deprecated resources with a strikeout
          // TODO: show private resources in a different way (and offer copy to project)
          setIcon(ChooseResourceDialog.this.getIcon(value, LIST_ICON_SIZE, LIST_CHECK_SIZE, list::repaint));

          String string = value.toString();
          String filter = mySearchField.getText();
          if (!filter.isEmpty()) {
            int match = StringUtil.indexOfIgnoreCase(string, filter, 0);
            if (match != -1) {
              append(string.substring(0, match));
              append(string.substring(match, match + filter.length()), SEARCH_MATCH_ATTRIBUTES);
              append(string.substring(match + filter.length()));
            }
            else {
              append(string);
            }
          }
          else {
            append(string);
          }
        }
      };
      // we use ANY fixed value here, as the width will stretch anyway, but we don't want the list to have to calculate it.
      list.setFixedCellWidth(10);
      list.setFixedCellHeight(LIST_CELL_HEIGHT);
      //noinspection unchecked
      list.setCellRenderer(listRenderer);
      list.setLayoutOrientation(JList.VERTICAL);
    }

    private void configureGridList(@NotNull TreeGrid<ResourceChooserItem> list) {
      // Using a DefaultListCellRenderer instead of a SimpleColoredComponent here because we want
      // to use HTML labels in order to handle line breaking with <nobr> and <br> tags
      ListCellRenderer gridRenderer = new DefaultListCellRenderer() {
        {
          setHorizontalTextPosition(SwingConstants.CENTER);
          setVerticalTextPosition(SwingConstants.BOTTOM);
          setHorizontalAlignment(SwingConstants.CENTER);
        }

        private final int CHAR_WIDTH = getFontMetrics(getFont()).charWidth('x'); // it's a monospace font;
        private final int CHARS_PER_CELL = GRID_CELL_SIZE / CHAR_WIDTH;

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, value, index, isSelected, false);

          if (!SystemInfoRt.isMac) {
            // Bug 63478794
            // On Linux and Windows, the backgrounds is paint on the whole row
            // and covers everything on left of the icon.
            // The workaround is to set the a transparent background and draw just a border instead
            // The list used is probably not the correct one and we need to find the correct grid view to use
            setBackground(UIUtil.TRANSPARENT_COLOR);
            if (isSelected) {
              setBorder(GRID_SELECTION_BORDER);
              setForeground(JBColor.foreground());
            }
          }

          // TODO show deprecated resources with a strikeout
          ResourceChooserItem rItem = (ResourceChooserItem)value;
          setIcon(ChooseResourceDialog.this.getIcon(rItem, GRID_ICON_SIZE, GRID_CHECK_SIZE, list::repaint));
          highlightSearchResult(rItem);
          return this;
        }

        private void highlightSearchResult(@NotNull ResourceChooserItem rItem) {
          String name = rItem.getName();

          String filter = mySearchField.getText();
          int match = -1;
          if (!filter.isEmpty()) {
            match = StringUtil.indexOfIgnoreCase(name, filter, 0);
          }

          int breakPoint = -1;
          if (name.length() > CHARS_PER_CELL) {
            breakPoint = name.indexOf('_', CHARS_PER_CELL / 2);
            if (breakPoint == -1 || breakPoint >= CHARS_PER_CELL || name.length() - breakPoint >= CHARS_PER_CELL) {
              breakPoint = CHARS_PER_CELL;
            }
            else {
              breakPoint++;
            }
          }

          if (match != -1 || breakPoint != -1) {
            HtmlBuilder builder = new HtmlBuilder();
            builder.openHtmlBody();
            builder.beginNoBr();
            if (match == -1) {
              // Just a breakpoint:
              builder.add(name, 0, breakPoint);
              builder.newline();
              builder.add(name, breakPoint, name.length());
            }
            else if (breakPoint == -1) {
              // Just a match
              builder.add(name, 0, match);
              builder.beginColor(JBColor.BLUE);
              builder.beginBold();
              builder.add(name, match, match + filter.length());
              builder.endBold();
              builder.endColor();
              builder.add(name, match + filter.length(), name.length());
            }
            else {
              // Both:
              if (breakPoint < match) {
                builder.add(name, 0, breakPoint);
                builder.newline();
                builder.add(name, breakPoint, match);
              }
              else {
                builder.add(name, 0, match);
              }
              builder.beginColor(JBColor.BLUE);
              builder.beginBold();
              builder.add(name, match, match + filter.length());
              builder.endBold();
              builder.endColor();
              // We don't show a breakpoint inside the matched region, we'll
              // put it right after if that's where it appeared
              if (breakPoint >= match && breakPoint < match + filter.length()) {
                builder.newline();
                builder.add(name, match + filter.length(), name.length());
              }
              else if (match < breakPoint) {
                builder.add(name, match + filter.length(), breakPoint);
                builder.newline();
                builder.add(name, breakPoint, name.length());
              }
            }
            builder.endNoBr();
            builder.closeHtmlBody();
            setText(builder.getHtml());
          }
        }
      };
      list.setFixedCellWidth(GRID_CELL_SIZE);
      list.setFixedCellHeight(GRID_CELL_SIZE);
      //noinspection unchecked
      list.setCellRenderer(gridRenderer);
      list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    }

    private void showNewResource(@NotNull ResourceEditorTab tab) {
      setSelectedItem(null);
      showEditorPanel();
      myEditorPanel.setSelectedTab(tab);
      myEditorPanel.setResourceName("");
      for (ResourceEditorTab editor : myEditorPanel.getAllTabs()) {
        editor.getLocationSettings().resetToDefault();
      }
    }

    /**
     * @param value can also be an instance of {@link StyleItemResourceValue} for ?attr/ values
     */
    private boolean select(@NotNull ResourceValue value) {
      mySelectedValue = value;
      boolean isAttr = value instanceof StyleItemResourceValue;
      for (ResourceChooserGroup group : myGroups) {
        for (ResourceChooserItem item : group.getItems()) {
          if (isAttr) {
            if (item.isAttr()) {
              ResourceReference attr = ((StyleItemResourceValue)value).getAttr();
              if (attr != null &&
                  // TODO: namespaces
                  ((attr.getNamespace() == ResourceNamespace.ANDROID) == item.isFramework()) &&
                  value.getName().equals(item.getName())) {
                setSelectedItem(item);
                return true;
              }
            }
          }
          else {
            if (!item.isAttr() && value.isFramework() == item.isFramework() && value.getName().equals(item.getName())) {
              setSelectedItem(item);
              return true;
            }
          }
        }
      }

      return false;
    }

    public void expandAll() {
      if (myList != null) {
        myList.expandAll();
      }
    }

    public ResourceChooserItem getSelectedItem() {
      if (myList != null) {
        return myList.getSelectedElement();
      }
      else if (myTable != null) {
        int index = myTable.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          Object selected = myTable.getValueAt(index, 0);
          if (selected instanceof ResourceChooserItem) {
            return (ResourceChooserItem)selected;
          }
        }
      }
      return null;
    }

    public void setSelectedItem(@Nullable ResourceChooserItem item) {
      if (myList != null) {
        myList.setSelectedElement(item);
      }
      else if (myTable != null) {
        TableModel model = myTable.getModel();
        for (int row = 0, rowCount = model.getRowCount(); row < rowCount; row++) {
          Object object = model.getValueAt(row, 0);
          if (object == item) {
            myTable.getSelectionModel().setSelectionInterval(row, row);
            Rectangle cellRect = myTable.getCellRect(row, 0, true);
            myTable.scrollRectToVisible(cellRect);
            break;
          }
        }
      }
    }

    @Nullable
    public String getValueForLivePreview() {
      if (myType == ResourceType.COLOR && myColorPicker != null && myColorPicker.isShowing()) {
        return ResourceHelper.colorToString(myColorPicker.getColor());
      }
      ResourceChooserItem item = getSelectedItem();
      return item != null ? item.getResourceUrl() : null;
    }

    public void setShowFiltered(boolean showFiltered) {
      if (myList != null) {
        myList.setExpandFiltered(showFiltered);
        if (!showFiltered) {
          myList.expandOnly(0);
        }
      }
    }
  }

  private class MyAddNewResourceLabelListener extends MouseAdapter implements KeyListener {
    @Override
    public void mouseClicked(MouseEvent e) {
      handle(e);
    }

    public void handle(InputEvent e) {
      DefaultActionGroup group = new DefaultActionGroup();
      Component source = (Component)e.getSource();
      DataContext context = SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT.getName(), source, null);

      Action[] actions = getCreateActions();
      for (final Action action : actions) {
        final AnAction anAction = new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            action.actionPerformed(new ActionEvent(source, 0, ""));
          }

          @Override
          public void update(AnActionEvent e) {
            Presentation presentation = e.getPresentation();
            String name = (String)action.getValue(Action.NAME);
            if (name != null) {
              presentation.setText(name);
            }
          }
        };

        if (actions.length == 1) {
          // One action: perform it immediately
          AnActionEvent actionEvent =
            AnActionEvent.createFromInputEvent(e, ChooseResourceDialog.class.getSimpleName(), new Presentation(), context);
          anAction.actionPerformed(actionEvent);
          return;
        }
        else {
          group.add(anAction);
        }
      }

      // Post menu
      JBPopupFactory factory = JBPopupFactory.getInstance();
      ListPopup popup = factory.createActionGroupPopup(null, group, context, true, null, 10);
      popup.showUnderneathOf(source);
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
        handle(e);
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
  }
}
