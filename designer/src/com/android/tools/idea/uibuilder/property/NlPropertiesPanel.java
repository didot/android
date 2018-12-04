/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.uibuilder.property.ToggleXmlPropertyEditor.NL_XML_PROPERTY_EDITOR;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.adtui.workbench.ToolWindowCallback;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.common.property.PropertiesPanel;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.uibuilder.property.inspector.NlInspectorPanel;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.awt.CausedFocusEvent;

public class NlPropertiesPanel extends PropertiesPanel<NlPropertiesManager> implements ViewAllPropertiesAction.Model {
  static final String PROPERTY_MODE = "properties.mode";
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 50;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 25;

  private final NlPropertiesManager myPropertiesManager;
  private final TableRowSorter<PTableModel> myRowSorter;
  private final MyFilter myFilter;
  private final MyFilterKeyListener myFilterKeyListener;
  private final PTable myTable;
  private final JPanel myTablePanel;
  private final PTableModel myModel;
  private final InspectorPanel<NlPropertiesManager> myInspectorPanel;
  private final JBCardLayout myCardLayout;
  private final JPanel myCardPanel;
  private final PropertyChangeListener myPropertyChangeListener = this::scrollIntoView;

  private List<NlComponent> myComponents;
  private List<NlPropertyItem> myProperties;
  @NotNull
  private PropertiesViewMode myPropertiesViewMode;
  private ToolWindowCallback myToolWindow;

  private AccessoryPanel myAccessoryPanel = new AccessoryPanel(AccessoryPanel.Type.EAST_PANEL, false);


  public NlPropertiesPanel(@NotNull NlPropertiesManager propertiesManager) {
    this(propertiesManager, new NlPTable(new PTableModel()), null);
  }

  @VisibleForTesting
  NlPropertiesPanel(@NotNull NlPropertiesManager propertiesManager,
                    @NotNull PTable table,
                    @Nullable InspectorPanel inspectorPanel) {
    super(new BorderLayout());
    setOpaque(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myPropertiesManager = propertiesManager;
    myRowSorter = new TableRowSorter<>();
    myFilter = new MyFilter();
    myFilterKeyListener = new MyFilterKeyListener();
    myModel = table.getModel();
    myTable = table;
    myTable.getEmptyText().setText("No selected component");
    JComponent fewerPropertiesLink = createViewAllPropertiesLinkPanel(false);
    fewerPropertiesLink.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
    myTablePanel = new JPanel(new BorderLayout());
    myTablePanel.setVisible(false);
    myTablePanel.setBackground(myTable.getBackground());
    myTablePanel.add(myTable, BorderLayout.NORTH);
    myTablePanel.add(fewerPropertiesLink, BorderLayout.SOUTH);
    myTablePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        myTable.editingStopped(new ChangeEvent(myTablePanel));
      }
    });

    myInspectorPanel = inspectorPanel != null
                       ? inspectorPanel
                       : new NlInspectorPanel(myPropertiesManager, createViewAllPropertiesLinkPanel(true));

    Disposer.register(myPropertiesManager, this);

    myCardLayout = new JBCardLayout();
    myCardPanel = new JPanel(myCardLayout);

    myCardPanel.add(PropertiesViewMode.ACCESSORY.name(), myAccessoryPanel);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myInspectorPanel,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    myCardPanel.add(PropertiesViewMode.INSPECTOR.name(), scrollPane);
    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTablePanel);
    tableScrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    tableScrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);
    tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myCardPanel.add(PropertiesViewMode.TABLE.name(), tableScrollPane);

    myPropertiesViewMode = getPropertiesViewModeInitially();
    myCardLayout.show(myCardPanel, myPropertiesViewMode.name());
    myComponents = Collections.emptyList();
    myProperties = Collections.emptyList();
    add(myCardPanel, BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
    JBCardLayout layout = (JBCardLayout)myCardPanel.getLayout();
    // This will stop the timer started in JBCardLayout:
    layout.first(myCardPanel);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(myPropertyChangeListener);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(myPropertyChangeListener);
  }

  @NotNull
  public NlPropertiesManager getPropertiesManager() {
    return myPropertiesManager;
  }

  public void registerToolWindow(@NotNull ToolWindowCallback toolWindow) {
    myToolWindow = toolWindow;
  }

  public int getFilterMatchCount() {
    if (myTable.getRowSorter() == null) {
      return -1;
    }
    return myTable.getRowCount();
  }

  public void setFilter(@NotNull String filter) {
    int selectedRow = myTable.getSelectedRow();
    PTableItem selectedItem = myTable.getSelectedItem();
    if (filter.isEmpty()) {
      myTable.setRowSorter(null);
    }
    else {
      myFilter.setPattern(filter);
      myRowSorter.setModel(myModel);
      myRowSorter.setRowFilter(myFilter);
      myRowSorter.setSortKeys(null);
      myTable.setRowSorter(myRowSorter);
    }
    myTable.restoreSelection(selectedRow, selectedItem);

    myInspectorPanel.setFilter(filter);
  }

  @NotNull
  public KeyListener getFilterKeyListener() {
    return myFilterKeyListener;
  }

  private void enterInFilter(@NotNull KeyEvent event) {
    if (myTable.getRowCount() != 1) {
      PTableItem item = (PTableItem)myTable.getValueAt(0, 1);
      if (!(item.isExpanded() && myTable.getRowCount() == item.getChildren().size() + 1)) {
        return;
      }
    }
    if (myTable.isCellEditable(0, 1)) {
      myTable.editCellAt(0, 1);
      myTable.transferFocus();
    }
    else {
      myModel.expand(myTable.convertRowIndexToModel(0));
      myTable.requestFocus();
      myTable.setRowSelectionInterval(0, 0);
    }
    event.consume();
  }

  public void activatePropertySheet() {
    setAllPropertiesPanelVisible(true);
  }

  public void activateInspector() {
    setAllPropertiesPanelVisible(false);
  }

  @Override
  public void setItems(@NotNull List<NlComponent> components,
                       @NotNull Table<String, String, NlPropertyItem> properties) {
    myComponents = components;
    myProperties = extractPropertiesForTable(properties);
    Project project = myPropertiesManager.getProject();

    if (PropertiesComponent.getInstance().getBoolean(NL_XML_PROPERTY_EDITOR)) {
      myTablePanel.setVisible(new NlXmlPropertyBuilder(myPropertiesManager, myTable, components, properties).build());
    }
    else {
      myTablePanel.setVisible(new NlPropertyTableBuilder(project, myTable, components, myProperties).build());
    }

    updateDefaultProperties(myPropertiesManager);
    myInspectorPanel.setComponent(components, properties, myPropertiesManager);
  }

  @NotNull
  private static List<NlPropertyItem> extractPropertiesForTable(@NotNull Table<String, String, NlPropertyItem> properties) {
    Map<String, NlPropertyItem> androidProperties = properties.row(ANDROID_URI);
    Map<String, NlPropertyItem> autoProperties = properties.row(AUTO_URI);
    Map<String, NlPropertyItem> designProperties = properties.row(TOOLS_URI);
    Map<String, NlPropertyItem> bareProperties = properties.row("");

    // Include all auto (app) properties and all android properties that are not also auto properties.
    List<NlPropertyItem> result = new ArrayList<>(properties.size());
    result.addAll(autoProperties.values());
    for (Map.Entry<String, NlPropertyItem> entry : androidProperties.entrySet()) {
      if (!autoProperties.containsKey(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    result.addAll(designProperties.values());
    result.addAll(bareProperties.values());
    return result;
  }

  @Override
  public void modelRendered() {
    UIUtil.invokeLaterIfNeeded(() -> {
      // Bug:219552 : Make sure updateDefaultProperties is always called from the same thread (the UI thread)
      if (PropertiesComponent.getInstance().getBoolean(NL_XML_PROPERTY_EDITOR)) {
        myPropertiesManager.updateSelection();
      }
      else {
        updateDefaultProperties(myPropertiesManager);
      }
      myInspectorPanel.refresh();
    });
  }

  private void updateDefaultProperties(@NotNull PropertiesManager<?> propertiesManager) {
    if (myComponents.isEmpty() || myProperties.isEmpty()) {
      return;
    }
    Map<ResourceReference, ResourceValue> defaultValues = propertiesManager.getDefaultProperties(myComponents);
    String style = propertiesManager.getDefaultStyle(myComponents);
    if (style == null && defaultValues.isEmpty()) {
      return;
    }
    for (NlPropertyItem property : myProperties) {
      // TODO: Change the API of RenderResult.getDefaultStyles to return ResourceValues instead of Strings.
      if (property.getName().equals(ATTR_STYLE) && StringUtil.isEmpty(property.getNamespace()) && !StringUtil.isEmpty(style)) {
        style = style.startsWith("android:") ? "?android:attr/" + StringUtil.trimStart(style, "android:") : "?attr/" + style;
        property.setDefaultValue(style);
      }
      else {
        property.setDefaultValue(getDefaultProperty(defaultValues, property));
      }
    }
  }

  @Nullable
  private String getDefaultProperty(@NotNull Map<ResourceReference, ResourceValue> defaultValues, @NotNull NlProperty property) {
    String namespaceUri = property.getNamespace();
    if (namespaceUri == null) {
      return null;
    }
    ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
    if (namespace == null) {
      return null;
    }
    XmlTag tag = property.getTag();
    if (tag == null || !tag.isValid()) {
      return null;
    }
    ResourceValue value = defaultValues.get(ResourceReference.attr(namespace, property.getName()));
    if (value == null) {
      return null;
    }
    ResourceNamespace.Resolver resolver = ResourceHelper.getNamespaceResolver(tag);
    ResourceNamespace defaultNamespace = ResourceRepositoryManager.getOrCreateInstance(myPropertiesManager.getFacet()).getNamespace();
    if (value.getResourceType() == ResourceType.STYLE_ITEM) {
      ResourceReference reference = value.getReference();
      if (reference == null) {
        return null;
      }
      ResourceUrl url = reference.getRelativeResourceUrl(defaultNamespace, resolver);
      return (url.type != ResourceType.ATTR) ?
             url.toString() : ResourceUrl.createThemeReference(url.namespace, url.type, url.name).toString();
    }
    else {
      return value.asReference().getRelativeResourceUrl(defaultNamespace, resolver).toString();
    }
  }

  @NotNull
  private JComponent createViewAllPropertiesLinkPanel(boolean viewAllProperties) {
    HyperlinkLabel textLink = new HyperlinkLabel();
    textLink.setHyperlinkText(
      viewAllProperties ? ViewAllPropertiesAction.VIEW_ALL_ATTRIBUTES : ViewAllPropertiesAction.VIEW_FEWER_ATTRIBUTES);
    textLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    textLink.setFocusable(false);
    HyperlinkLabel iconLink = new HyperlinkLabel();
    iconLink.setIcon(StudioIcons.LayoutEditor.Properties.TOGGLE_PROPERTIES);
    iconLink.setFocusable(false);
    iconLink.setUseIconAsLink(true);
    iconLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    linkPanel.setOpaque(false);
    linkPanel.add(textLink);
    linkPanel.add(iconLink);
    return linkPanel;
  }

  private void setAllPropertiesPanelVisible(@NotNull HyperlinkEvent event, boolean viewAllProperties) {
    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      setAllPropertiesPanelVisible(viewAllProperties);
    }
  }

  @Override
  public boolean isAllPropertiesPanelMode() {
    return myPropertiesViewMode == PropertiesViewMode.TABLE;
  }

  public void setToolContext(@Nullable DesignSurface surface) {
    myAccessoryPanel.setSurface((NlDesignSurface) surface);
  }

  @Override
  public void setAllPropertiesPanelVisible(boolean viewAllProperties) {
    Component next = viewAllProperties ? myTable : myInspectorPanel;
    setAllPropertiesPanelVisibleInternal(viewAllProperties, next::requestFocus);
  }

  @Override
  public boolean isAccessoryPanelVisible() {
    return myPropertiesViewMode == PropertiesViewMode.ACCESSORY;
  }

  private void setAllPropertiesPanelVisibleInternal(boolean viewAllProperties, @Nullable Runnable onDone) {
    myPropertiesViewMode = viewAllProperties ? PropertiesViewMode.TABLE : PropertiesViewMode.INSPECTOR;
    myCardLayout.swipe(myCardPanel, myPropertiesViewMode.name(), JBCardLayout.SwipeDirection.AUTO, onDone);
    PropertiesComponent.getInstance().setValue(PROPERTY_MODE, myPropertiesViewMode.name());
  }

  public void showAccessoryPanel(boolean show) {
    if (!show && myPropertiesViewMode != PropertiesViewMode.ACCESSORY) {
      return;
    }
    myPropertiesViewMode = show ? PropertiesViewMode.ACCESSORY : PropertiesViewMode.INSPECTOR;
    Component next = show ? myAccessoryPanel : myInspectorPanel;
    myCardLayout.swipe(myCardPanel, myPropertiesViewMode.name(), JBCardLayout.SwipeDirection.AUTO, next::requestFocus);
  }

  @NotNull
  public PropertiesViewMode getPropertiesViewMode() {
    return myPropertiesViewMode;
  }

  @NotNull
  private static PropertiesViewMode getPropertiesViewModeInitially() {
    String name = PropertiesComponent.getInstance().getValue(PROPERTY_MODE, PropertiesViewMode.INSPECTOR.name());

    PropertiesViewMode mode;
    try {
      mode = PropertiesViewMode.valueOf(name);
    }
    catch (IllegalArgumentException e) {
      mode = PropertiesViewMode.INSPECTOR;
      Logger.getInstance(NlPropertiesPanel.class)
        .warn("There is no PropertiesViewMode called " + name + ", uses " + mode.name() + " instead", e);
      // store the new property mode as preference
      PropertiesComponent.getInstance().setValue(PROPERTY_MODE, mode.name());
    }
    return mode;
  }

  @Override
  public void activatePreferredEditor(@NotNull String propertyName, boolean afterload) {
    Runnable selectEditor = () -> {
      // Restore a possibly minimized tool window
      if (myToolWindow != null) {
        myToolWindow.restore();
      }
      // Set focus on the editor of preferred property
      myInspectorPanel.activatePreferredEditor(propertyName, afterload);
    };
    if (!isAllPropertiesPanelMode()) {
      selectEditor.run();
    }
    else {
      // Switch to the inspector. The switch is animated, so we need to delay the editor selection.
      setAllPropertiesPanelVisibleInternal(false, selectEditor);
    }
  }

  private void scrollIntoView(@NotNull PropertyChangeEvent event) {
    if (needToScrollInView(event)) {
      Component newFocusedComponent = (Component)event.getNewValue();
      JComponent parent = (JComponent)newFocusedComponent.getParent();
      Rectangle bounds = newFocusedComponent.getBounds();
      if (newFocusedComponent == myTable) {
        bounds = myTable.getCellRect(myTable.getSelectedRow(), 1, true);
        bounds.x = 0;
      }
      parent.scrollRectToVisible(bounds);
    }
  }

  private boolean needToScrollInView(@NotNull PropertyChangeEvent event) {
    AWTEvent awtEvent = EventQueue.getCurrentEvent();
    if (!"focusOwner".equals(event.getPropertyName()) ||
        !(event.getNewValue() instanceof Component)) {
      return false;
    }
    Component newFocusedComponent = (Component)event.getNewValue();
    if (!isAncestorOf(newFocusedComponent) ||
        !(newFocusedComponent.getParent() instanceof JComponent) ||
        !(awtEvent instanceof CausedFocusEvent)) {
      return false;
    }
    CausedFocusEvent focusEvent = (CausedFocusEvent)awtEvent;
    switch (focusEvent.getCause()) {
      case TRAVERSAL:
      case TRAVERSAL_UP:
      case TRAVERSAL_DOWN:
      case TRAVERSAL_FORWARD:
      case TRAVERSAL_BACKWARD:
        break;
      default:
        return false;
    }
    return true;
  }

  @NotNull
  public InspectorPanel getInspector() {
    return myInspectorPanel;
  }

  // ---- Implements DataProvider ----

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  // ---- Implements CopyProvider ----
  // Avoid the copying of components while editing the properties.

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
  }

  // ---- Implements CutProvider ----
  // Avoid the deletion of components while editing the properties.

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
  }

  // ---- Implements DeleteProvider ----
  // Avoid the deletion of components while editing the properties.

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
  }

  // ---- Implements PasteProvider ----
  // Avoid the paste of components while editing the properties.

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
  }

  @TestOnly
  public PTable getTable() {
    return myTable;
  }

  public enum PropertiesViewMode {
    TABLE,
    INSPECTOR,
    ACCESSORY
  }

  @VisibleForTesting
  static class MyFilter extends RowFilter<PTableModel, Integer> {
    private final SpeedSearchComparator myComparator = new SpeedSearchComparator(false);
    private String myPattern = "";

    @VisibleForTesting
    void setPattern(@NotNull String pattern) {
      myPattern = pattern;
    }

    @Override
    public boolean include(Entry<? extends PTableModel, ? extends Integer> entry) {
      PTableItem item = (PTableItem)entry.getValue(0);
      if (isMatch(item.getName())) {
        return true;
      }
      if (item.getParent() != null && isMatch(item.getParent().getName())) {
        return true;
      }
      if (!(item instanceof PTableGroupItem)) {
        return false;
      }
      PTableGroupItem group = (PTableGroupItem)item;
      for (PTableItem child : group.getChildren()) {
        if (isMatch(child.getName())) {
          return true;
        }
      }
      return false;
    }

    private boolean isMatch(@NotNull String text) {
      return myComparator.matchingFragments(myPattern, text) != null;
    }
  }

  private class MyFilterKeyListener extends KeyAdapter {

    @Override
    public void keyPressed(@NotNull KeyEvent event) {
      if (!myFilter.myPattern.isEmpty() && event.getKeyCode() == KeyEvent.VK_ENTER && event.getModifiers() == 0) {
        if (myPropertiesViewMode == PropertiesViewMode.TABLE) {
          enterInFilter(event);
        }
        else {
          myInspectorPanel.enterInFilter(event);
        }
      }
    }
  }
}
