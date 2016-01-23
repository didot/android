/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.sdklib.repositoryv2.targets.AndroidTargetManager;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioProgressRunner;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.avdmanager.AvdManagerConnection.GOOGLE_APIS_TAG;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.TV_TAG;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.WEAR_TAG;

/**
 * Displays a list of system images currently installed and allows selection of one
 */
public class SystemImageList extends JPanel implements ListSelectionListener {
  private final JButton myRefreshButton = new JButton(AllIcons.Actions.Refresh);
  private final JBCheckBox myShowRemoteCheckbox = new JBCheckBox("Show downloadable system images", true);
  private final JBCheckBox myShowRecommendedOnlyCheckbox = new JBCheckBox("Recommended images only", true);
  private final JButton myInstallLatestVersionButton = new JButton("Install Latest Version...");
  private final Project myProject;
  private final JPanel myRemoteStatusPanel = new JPanel(new CardLayout());
  private final AndroidSdkHandler mySdkHandler;
  private TableView<SystemImageDescription> myTable = new TableView<SystemImageDescription>();
  private ListTableModel<SystemImageDescription> myModel = new ListTableModel<SystemImageDescription>();
  private Set<SystemImageSelectionListener> myListeners = Sets.newHashSet();
  private Predicate<SystemImageDescription> myFilter;
  private static final String ERROR_KEY = "error";
  private static final String LOADING_KEY = "loading";
  private static final ProgressIndicator LOGGER = new StudioLoggerProgressIndicator(SystemImageList.class);

  private static final Map<Abi, Integer> DEFAULT_ABI_SORT_ORDER = new ContainerUtil.ImmutableMapBuilder<Abi, Integer>()
    .put(Abi.MIPS64, 0)
    .put(Abi.MIPS, 1)
    .put(Abi.ARM64_V8A, 2)
    .put(Abi.ARMEABI, 3)
    .put(Abi.ARMEABI_V7A, 4)
    .put(Abi.X86_64, 5)
    .put(Abi.X86, 6)
    .build();

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through {@link #addSelectionListener(SystemImageSelectionListener)}
   */
  public interface SystemImageSelectionListener {
    void onSystemImageSelected(@Nullable SystemImageDescription systemImage);
  }

  public SystemImageList(@Nullable Project project) {
    myProject = project;
    mySdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    myModel.setColumnInfos(ourColumnInfos);
    myModel.setSortable(true);
    myTable.setModelAndUpdateColumns(myModel);
    ListSelectionModel selectionModel =
      new DefaultListSelectionModel() {
        @Override
        public void setSelectionInterval(int index0, int index1) {
          super.setSelectionInterval(index0, index1);
          TableCellEditor editor = myTable.getCellEditor();
          if (editor != null) {
            editor.cancelCellEditing();
          }
          myTable.repaint();
          possiblySwitchEditors(index0, 0);
        }
      };
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setSelectionModel(selectionModel);

    myTable.setRowSelectionAllowed(true);
    myTable.addMouseListener(editorListener);
    myTable.addMouseMotionListener(editorListener);
    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    JPanel southPanel = new JPanel(new BorderLayout());
    JPanel refreshMessageAndButton = new JPanel(new FlowLayout());
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon("refresh images");
    JLabel refreshingLabel = new JLabel("Refreshing...");
    refreshingLabel.setForeground(JBColor.GRAY);
    JPanel refreshPanel = new JPanel(new FlowLayout());
    refreshPanel.add(refreshIcon);
    refreshPanel.add(refreshingLabel);
    refreshPanel.setVisible(false);
    myRemoteStatusPanel.add(refreshPanel, LOADING_KEY);
    JLabel errorLabel = new JLabel("Error loading remote images");
    errorLabel.setForeground(JBColor.GRAY);
    JPanel errorPanel = new JPanel(new FlowLayout());
    errorPanel.add(errorLabel);
    myRemoteStatusPanel.add(errorPanel, ERROR_KEY);
    refreshMessageAndButton.add(myRemoteStatusPanel);
    refreshMessageAndButton.add(myRefreshButton);
    southPanel.add(refreshMessageAndButton, BorderLayout.EAST);
    JPanel buttonPanel = new JPanel(new BorderLayout());
    buttonPanel.add(myShowRemoteCheckbox, BorderLayout.NORTH);
    buttonPanel.add(myShowRecommendedOnlyCheckbox, BorderLayout.SOUTH);
    southPanel.add(buttonPanel, BorderLayout.WEST);
    ActionListener dataChanged = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myModel.fireTableDataChanged();
      }
    };
    myShowRemoteCheckbox.addActionListener(dataChanged);
    myShowRecommendedOnlyCheckbox.addActionListener(dataChanged);
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refreshImages(true);
      }
    });
    myInstallLatestVersionButton.addActionListener(new ActionListener() {  // TODO(jbakermalone): actually show this button in the ui
      @Override
      public void actionPerformed(ActionEvent e) {
        installForDevice();
      }
    });
    add(southPanel, BorderLayout.SOUTH);
    myTable.getSelectionModel().addListSelectionListener(this);
    TableRowSorter<ListTableModel<SystemImageDescription>> sorter =
      (TableRowSorter<ListTableModel<SystemImageDescription>>)myTable.getRowSorter();
    sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
    sorter.setRowFilter(new RowFilter<ListTableModel<SystemImageDescription>, Integer>() {
      @Override
      public boolean include(Entry<? extends ListTableModel<SystemImageDescription>, ? extends Integer> entry) {
        SystemImageDescription image = myModel.getRowValue(entry.getIdentifier());
        Abi abi = Abi.getEnum(image.getAbiType());
        boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;
        return (!image.isRemote() || myShowRemoteCheckbox.isSelected()) &&
               ((isAvdIntel && GOOGLE_APIS_TAG.equals(image.getTag())) || !myShowRecommendedOnlyCheckbox.isSelected());
      }
    });
    myTable.setRowSorter(sorter);
  }

  private final MouseAdapter editorListener = new MouseAdapter() {
    @Override
    public void mouseMoved(MouseEvent e) {
      possiblySwitchEditors(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      possiblySwitchEditors(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      possiblySwitchEditors(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      possiblySwitchEditors(e);
    }
  };

  private void possiblySwitchEditors(MouseEvent e) {
    Point p = e.getPoint();
    int row = myTable.rowAtPoint(p);
    int col = myTable.columnAtPoint(p);
    possiblySwitchEditors(row, col);
  }

  private void possiblySwitchEditors(int row, int col) {
    if (row != myTable.getEditingRow() || col != myTable.getEditingColumn()) {
      if (row != -1 && col != -1 && myTable.isCellEditable(row, col)) {
        myTable.editCellAt(row, col);
      }
    }
  }

  public void refreshImages(final boolean forceRefresh) {
    ((CardLayout)myRemoteStatusPanel.getLayout()).show(myRemoteStatusPanel, LOADING_KEY);
    myRemoteStatusPanel.setVisible(true);
    myRefreshButton.setEnabled(false);
    final List<SystemImageDescription> items = Lists.newArrayList();
    RepoManager.RepoLoadedCallback localComplete = new RepoManager.RepoLoadedCallback() {
      @Override
      public void doRun(@NotNull RepositoryPackages packages) {
        // getLocalImages() doesn't use SdkPackages, so it's ok that we're not using what's passed in.
        items.addAll(getLocalImages());
        // Update list in the UI immediately with the locally available system images
        updateListModel(items);
      }
    };
    RepoManager.RepoLoadedCallback remoteComplete = new RepoManager.RepoLoadedCallback() {
      @Override
      public void doRun(@NotNull RepositoryPackages packages) {
        List<SystemImageDescription> remotes = getRemoteImages(packages);
        if (remotes != null) {
          items.addAll(remotes);
          updateListModel(items);
          myShowRemoteCheckbox.setEnabled(true);
        }
        else {
          myShowRemoteCheckbox.setEnabled(false);
          myShowRemoteCheckbox.setSelected(false);
        }
        myRemoteStatusPanel.setVisible(false);
        myRefreshButton.setEnabled(true);
        if (!forceRefresh && myTable.getRowCount() == 0) {
          // If there are still no visible rows, show the non recommended system images.
          myShowRecommendedOnlyCheckbox.setSelected(false);
          myModel.fireTableDataChanged();
        }
      }
    };
    Runnable error = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {

            ((CardLayout)myRemoteStatusPanel.getLayout()).show(myRemoteStatusPanel, ERROR_KEY);
            myRefreshButton.setEnabled(true);
          }
        });
      }
    };

    StudioProgressRunner runner = new StudioProgressRunner(false, true, false, "Loading Images", true, myProject);
    mySdkHandler.getSdkManager(LOGGER)
      .load(forceRefresh ? 0 : RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, ImmutableList.of(localComplete), ImmutableList.of(remoteComplete),
            ImmutableList.of(error), runner, new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  @Nullable
  private List<SystemImageDescription> getRemoteImages(@NotNull RepositoryPackages packages) {
    List<SystemImageDescription> items = Lists.newArrayList();
    Set<RemotePackage> infos = packages.getNewPkgs();

    if (infos.isEmpty()) {
      return null;
    }
    else {
      for (RemotePackage info : infos) {
        if (SystemImageDescription.hasSystemImage(info)) {
          SystemImageDescription image = new SystemImageDescription(info, null);
          if (filter(image) && (myFilter == null || myFilter.apply(image))) {
            items.add(image);
          }
        }
      }
    }
    return items;
  }

  private static boolean filter(@NotNull SystemImageDescription img) {
    // https://code.google.com/p/android/issues/detail?id=187938
    return img.getVersion().getApiLevel() != 15;
  }

  public void refreshLocalImagesSynchronously() {
    myModel.setItems(getLocalImages());
  }

  private List<SystemImageDescription> getLocalImages() {
    AndroidTargetManager targetManager = mySdkHandler.getAndroidTargetManager(LOGGER);
    List<SystemImageDescription> items = Lists.newArrayList();

    for (IAndroidTarget target : targetManager.getTargets(true, LOGGER)) {
      ISystemImage[] systemImages = target.getSystemImages();
      if (systemImages != null) {
        for (ISystemImage image : systemImages) {
          // If we don't have a filter or this image passes the filter
          SystemImageDescription desc = new SystemImageDescription(target, image);
          if (filter(desc) && (myFilter == null || myFilter.apply(desc))) {
            items.add(desc);
          }
        }
      }
    }
    return items;
  }

  /**
   * Shows the given items. May be called from the background thread but will ensure
   * that the updates are applied in the UI thread.
   */
  private void updateListModel(@NotNull final List<SystemImageDescription> items) {
    SystemImageDescription selected = myTable.getSelectedObject();
    myModel.setItems(items);
    if (selected == null || !items.contains(selected)) {
      selectDefaultImage();
    }
    else {
      setSelectedImage(selected);
    }
  }

  public void setFilter(Predicate<SystemImageDescription> filter) {
    myFilter = filter;
  }

  public void addSelectionListener(SystemImageSelectionListener listener) {
    myListeners.add(listener);
  }


  public void selectDefaultImage() {
    AndroidVersion maxVersion = null;
    int maxAbi = -1;
    SystemImageDescription best = null;

    for (SystemImageDescription desc : myModel.getItems()) {
      if (!desc.isRemote()) {
        Abi abi = Abi.getEnum(desc.getAbiType());
        int abiRank = -1;
        if (abi != null && DEFAULT_ABI_SORT_ORDER.containsKey(abi)) {
          abiRank = DEFAULT_ABI_SORT_ORDER.get(abi);
        }

        AndroidVersion version = desc.getVersion();

        if (isBestDefault(abiRank, version, desc.getTag(), maxAbi, maxVersion)) {
          best = desc;
          maxAbi = abiRank;
          maxVersion = version;
        }
      }
    }
    setSelectedImage(best);
  }

  private static boolean isBestDefault(int abiRank,
                                       AndroidVersion version,
                                       IdDisplay tag,
                                       int maxAbi,
                                       AndroidVersion maxVersion) {
    int res = ComparisonChain.start().compare(abiRank, maxAbi).compare(version, maxVersion).result();
    if (res != 0) {
      return res > 0;
    }
    if (tag != null && tag.equals(GOOGLE_APIS_TAG)) {
      return true;
    }
    return false;
  }


  public void setSelectedImage(@Nullable SystemImageDescription selectedImage) {
    if (selectedImage != null) {
      myTable.setSelection(ImmutableSet.of(selectedImage));
    } else {
      myTable.clearSelection();
    }
  }

  private void installForDevice() {
    int apiLevel = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    List<String> requestedPackages = Lists.newArrayListWithCapacity(3);
    requestedPackages.add(DetailsTypes.getSysImgPath(null, new AndroidVersion(apiLevel, null),
                                                     SystemImage.DEFAULT_TAG, Abi.X86.toString()));
    requestedPackages.add(DetailsTypes.getSysImgPath(null, new AndroidVersion(apiLevel, null), WEAR_TAG, Abi.X86.toString()));
    requestedPackages.add(DetailsTypes.getSysImgPath(null, new AndroidVersion(apiLevel, null), TV_TAG, Abi.X86.toString()));
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(this, requestedPackages);
    if (dialog != null) {
      dialog.show();
      refreshImages(true);
    }
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   * @param e
   */
  @Override
  public void valueChanged(ListSelectionEvent e) {
    SystemImageDescription selected = myTable.getSelectedObject();
    for (SystemImageSelectionListener listener : myListeners) {
      listener.onSystemImageSelected(selected);
    }
  }

  /**
   * List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
   * the cell value in that column for a given row item.
   */
  private final ColumnInfo[] ourColumnInfos = new ColumnInfo[] {
    new SystemImageColumnInfo("Release Name") {
      @Nullable
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        AndroidVersion version = systemImage.getVersion();
        if (version == null) {
          return "Unknown";
        }
        String codeName = version.isPreview() ? version.getCodename()
                                              : SdkVersionInfo.getCodeName(version.getApiLevel());
        String maybeDeprecated = version.getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API ? " (Deprecated)" : "";
        return codeName + maybeDeprecated;
      }
    },
    new SystemImageColumnInfo("API Level", JBUI.scale(100)) {
      @Nullable
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        AndroidVersion version = systemImage.getVersion();
        if (version != null) {
          return version.getApiString();
        }
        return "Unknown";
      }
    },
    new SystemImageColumnInfo("ABI", JBUI.scale(100)) {
      @Nullable
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        return systemImage.getAbiType();
      }
    },
    new SystemImageColumnInfo("Target") {
      @Nullable
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        IdDisplay tag = systemImage.getTag();
        String name = systemImage.getName();
        return String.format("%1$s %2$s", name, tag.equals(SystemImage.DEFAULT_TAG) ? "" :
                                                  String.format("(with %s)", tag.getDisplay()));
      }
    },
  };

  /**
   * This class extends {@link com.intellij.util.ui.ColumnInfo} in order to pull a string value from a given
   * {@link SystemImageDescription}.
   * This is the column info used for most of our table, including the Name, Resolution, and API level columns.
   * It uses the text field renderer ({@link #myRenderer}) and allows for sorting by the lexicographical value
   * of the string displayed by the {@link com.intellij.ui.components.JBLabel} rendered as the cell component. An explicit width may be used
   * by calling the overloaded constructor, otherwise the column will auto-scale to fill available space.
   */
  public abstract class SystemImageColumnInfo extends ColumnInfo<SystemImageDescription, String> {
    private final Border myBorder = IdeBorderFactory.createEmptyBorder(10, 10, 10, 10);

    private final int myWidth;

    public SystemImageColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    public SystemImageColumnInfo(@NotNull String name) {
      this(name, -1);
    }

    @Override
    public boolean isCellEditable(SystemImageDescription systemImageDescription) {
      return systemImageDescription.isRemote();
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(SystemImageDescription o) {
      return new SystemImageDescriptionRenderer(o);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(final SystemImageDescription o) {
      return new SystemImageDescriptionRenderer(o);
    }

    private class SystemImageDescriptionRenderer extends AbstractTableCellEditor implements TableCellRenderer {
      private SystemImageDescription image;

      SystemImageDescriptionRenderer(SystemImageDescription o) {
        image = o;
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (isSelected) {
          if (image.isRemote()) {
            panel.setBackground(UIUtil.getListUnfocusedSelectionBackground());
          } else {
            panel.setBackground(table.getSelectionBackground());
          }
          panel.setForeground(table.getSelectionForeground());
          panel.setOpaque(true);
        }
        else {
          panel.setBackground(table.getBackground());
          panel.setForeground(table.getForeground());
          panel.setOpaque(true);
        }
        JBLabel label = new JBLabel((String)value);
        Font labelFont = UIUtil.getLabelFont();
        if (column == 0) {
          label.setFont(labelFont.deriveFont(Font.BOLD));
        }
        if (image.isRemote()) {
          Font font = labelFont.deriveFont(label.getFont().getStyle() | Font.ITALIC);
          label.setFont(font);
          label.setForeground(UIUtil.getLabelDisabledForeground());
          // on OS X the actual text width isn't computed correctly. Compensating for that..
          if (!label.getText().isEmpty()) {
            int fontMetricsWidth = label.getFontMetrics(label.getFont()).stringWidth(label.getText());
            TextLayout l = new TextLayout(label.getText(), label.getFont(), label.getFontMetrics(label.getFont()).getFontRenderContext());
            int offset = (int)Math.ceil(l.getBounds().getWidth()) - fontMetricsWidth;
            if (offset > 0) {
              label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, offset));
            }
          }
          panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
              if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
                downloadImage(image);
              }
            }
          });
        }
        panel.add(label);
        if (image.isRemote() && column == 0) {
          final JBLabel link = new JBLabel("Download");
          link.setBackground(table.getBackground());
          link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          link.setForeground(JBColor.BLUE);
          Font font = link.getFont();
          if (isSelected) {
            Map<TextAttribute, Integer> attrs = Maps.newHashMap();
            attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            font = font.deriveFont(attrs);
          }
          link.setFont(font);
          link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              downloadImage(image);
            }
          });
          panel.add(link);
        }
        return panel;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        return getTableCellRendererComponent(table, value, isSelected, false, row, column);
      }

      @Override
      public Object getCellEditorValue() {
        return null;
      }

      @Override
      public boolean isCellEditable(EventObject e) {
        return true;
      }

    }

    private void downloadImage(SystemImageDescription image) {
      List<String> requestedPackages = Lists.newArrayList(image.getRemotePackage().getPath());
      ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(SystemImageList.this, requestedPackages);
      if (dialog != null) {
        dialog.show();
        refreshImages(true);
      }
    }

    @Nullable
    @Override
    public Comparator<SystemImageDescription> getComparator() {
      return new Comparator<SystemImageDescription>() {
        ApiLevelComparator myComparator = new ApiLevelComparator();
        @Override
        public int compare(SystemImageDescription o1, SystemImageDescription o2) {
          int res = myComparator.compare(valueOf(o1), valueOf(o2));
          if (res == 0) {
            return o1.getTag().compareTo(o2.getTag());
          }
          return res;

        }
      };
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }
  }

}
