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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.assetstudiolib.GeneratedIcon;
import com.android.assetstudiolib.GeneratedImageIcon;
import com.android.assetstudiolib.GeneratedXmlResource;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.ui.FileTreeCellRenderer;
import com.android.tools.idea.ui.FileTreeModel;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.value.AsValueExpression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.primitives.Ints;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This step allows the user to select a build variant and provides a preview of the assets that
 * are about to be created.
 */
public final class ConfirmGenerateImagesStep extends ModelWizardStep<GenerateIconsModel> {

  private static final DefaultTreeModel EMPTY_MODEL = new DefaultTreeModel(null);

  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();
  private final JBLabel myPreviewIcon;

  private JPanel myRootPanel;
  private JComboBox<AndroidSourceSet> myPathsComboBox;
  private Tree myOutputPreviewTree;
  private CheckeredBackgroundPanel myPreviewPanel;
  private JTextField mySizeDpTextField;
  private JTextField myDensityTextField;
  private JTextField myFileTypeTextField;
  private JTextField mySizePxTextField;
  private JSplitPane mySplitPane;
  private Map<FileTreeModel.Node, GeneratedIcon> myNodeToPreviewImage = new HashMap<>();

  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myLeftPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myRightPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewFillPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private TitledSeparator myDetailsHeaderPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myDetailsGridContainer;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel mySizeDetailsRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myDetailsPanel;
  private JPanel myImagePreviewPanel;
  private JPanel myXmlPreviewPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JTextPane myXmlTextPane;
  private EditorEx myFilePreviewEditor;

  private EditorFactory myEditorFactory;
  private Document myXmlPreviewDocument;

  private ObservableValue<AndroidSourceSet> mySelectedSourceSet;
  private BoolProperty myFilesAlreadyExist = new BoolValueProperty();

  public ConfirmGenerateImagesStep(@NotNull GenerateIconsModel model, @NotNull List<AndroidSourceSet> sourceSets) {
    super(model, "Confirm Icon Path");
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    DefaultComboBoxModel<AndroidSourceSet> sourceSetsModel = new DefaultComboBoxModel<>();
    for (AndroidSourceSet sourceSet : sourceSets) {
      sourceSetsModel.addElement(sourceSet);
    }
    myPathsComboBox.setRenderer(new ListCellRendererWrapper<AndroidSourceSet>() {
      @Override
      public void customize(JList list, AndroidSourceSet sourceSet, int index, boolean selected, boolean hasFocus) {
        setText(sourceSet.getName());
      }
    });
    myPathsComboBox.setModel(sourceSetsModel);

    myOutputPreviewTree.setModel(EMPTY_MODEL);
    myOutputPreviewTree.setCellRenderer(new FileTreeCellRenderer());
    myOutputPreviewTree.setBorder(BorderFactory.createLineBorder(UIUtil.getBoundsColor()));
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
    myOutputPreviewTree.getEmptyText().setText("No resource folder defined in project");
    myOutputPreviewTree.addTreeSelectionListener(e -> {
      TreePath newPath = e.getNewLeadSelectionPath();
      showSelectedNodeDetails(newPath);
    });

    String alreadyExistsError = WizardUtils.toHtmlString(
      "Some existing files will be overwritten by this operation.<br>" +
      "Files which replace existing files are marked red in the preview above.");
    myValidatorPanel.registerValidator(myFilesAlreadyExist, new FalseValidator(Validator.Severity.WARNING, alreadyExistsError));


    myPreviewIcon = new JBLabel();
    myPreviewIcon.setVisible(false);
    myPreviewIcon.setHorizontalAlignment(SwingConstants.CENTER);
    myPreviewIcon.setVerticalAlignment(SwingConstants.CENTER);

    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(myPreviewIcon, BorderLayout.CENTER);

    // Replace the JSplitPane component with a Splitter (IntelliJ look & feel).
    //
    // Note: We set the divider location on the JSplitPane from the left component preferred size to override the
    //       default divider location of the new Splitter (the default is to put the divider in the middle).
    //mySplitPane.setDividerLocation(mySplitPane.getLeftComponent().getPreferredSize().width);
    GuiUtils.replaceJSplitPaneWithIDEASplitter(mySplitPane);
  }

  private void showSelectedNodeDetails(TreePath newPath) {
    if (newPath != null && newPath.getLastPathComponent() instanceof FileTreeModel.Node) {
      FileTreeModel.Node node = (FileTreeModel.Node)newPath.getLastPathComponent();

      GeneratedIcon generatedIcon = myNodeToPreviewImage.get(node);
      if (generatedIcon instanceof GeneratedImageIcon) {
        BufferedImage image = ((GeneratedImageIcon)generatedIcon).getImage();
        ImageIcon icon = new ImageIcon(image);
        myPreviewIcon.setIcon(icon);
        myPreviewIcon.setVisible(true);

        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale  // file names are not locale sensitive
        String extension = FileUtilRt.getExtension(node.name).toUpperCase();
        if (StringUtil.isEmpty(extension)) {
          myFileTypeTextField.setText("N/A");
        }
        else {
          myFileTypeTextField.setText(String.format("%s File", extension));
        }

        mySizePxTextField.setText(String.format("%dx%d", icon.getIconWidth(), icon.getIconHeight()));

        Density density = ((GeneratedImageIcon)generatedIcon).getDensity();
        myDensityTextField.setText(density.getResourceValue());

        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(density);
        mySizeDpTextField.setText(
          String.format("%dx%d", Math.round(icon.getIconWidth() / scaleFactor), Math.round(icon.getIconHeight() / scaleFactor)));

        myImagePreviewPanel.setVisible(true);
        myXmlPreviewPanel.setVisible(false);
        return;
      }
      else if (generatedIcon instanceof GeneratedXmlResource) {
        GeneratedXmlResource xml = (GeneratedXmlResource)generatedIcon;

        ApplicationManager.getApplication().runWriteAction(() -> {
          if (myEditorFactory == null) {
            myEditorFactory = EditorFactory.getInstance();
          }

          if (myXmlPreviewDocument == null) {
            myXmlPreviewDocument = myEditorFactory.createDocument("");
          }
          myXmlPreviewDocument.setReadOnly(false);
          myXmlPreviewDocument.setText(xml.getXmlText());
          myXmlPreviewDocument.setReadOnly(true);

          if (myFilePreviewEditor == null) {
            myFilePreviewEditor = (EditorEx)myEditorFactory.createViewer(myXmlPreviewDocument);
            myFilePreviewEditor.setCaretVisible(false);
            myFilePreviewEditor.getSettings().setLineNumbersShown(false);
            myFilePreviewEditor.getSettings().setLineMarkerAreaShown(false);
            myFilePreviewEditor.getSettings().setFoldingOutlineShown(false);
            myFilePreviewEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(null, StdFileTypes.XML));
            myXmlPreviewPanel.removeAll();
            myXmlPreviewPanel.add(myFilePreviewEditor.getComponent());
          }
        });

        myImagePreviewPanel.setVisible(false);
        myXmlPreviewPanel.setVisible(true);
        return;
      }
    }

    // Reset properties of both preview panels
    myPreviewIcon.setVisible(false);
    myPreviewIcon.setIcon(null);
    myFileTypeTextField.setText("");
    mySizeDpTextField.setText("");
    mySizePxTextField.setText("");
    myDensityTextField.setText("");
    // Activate the image preview by default: this is somewhat arbitrary, but the image preview
    // is the most common one, so it alleviates flickering when changing the selection fast in
    // the tree.
    myImagePreviewPanel.setVisible(true);
    myXmlPreviewPanel.setVisible(false);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    mySelectedSourceSet = new AsValueExpression<>(new SelectedItemProperty<>(myPathsComboBox));
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setPaths(mySelectedSourceSet.get().getPaths());
  }

  @Override
  protected void onEntering() {
    myListeners.release(mySelectedSourceSet); // Just in case we're entering this step a second time
    myListeners.receiveAndFire(mySelectedSourceSet, (AndroidSourceSet sourceSet) -> {
      AndroidIconGenerator iconGenerator = getModel().getIconGenerator();
      File resDir = sourceSet.getPaths().getResDirectory();
      if (iconGenerator == null || resDir == null || resDir.getParentFile() == null) {
        return;
      }

      myNodeToPreviewImage.clear();
      final Map<File, GeneratedIcon> pathIconMap = iconGenerator.generateIntoIconMap(sourceSet.getPaths());
      myFilesAlreadyExist.set(false);

      // Create a FileTreeModel containing all generated files
      FileTreeModel treeModel = new FileTreeModel(resDir.getParentFile(), true);
      for (File path : pathIconMap.keySet()) {
        GeneratedIcon icon = pathIconMap.get(path);

        if (path.exists()) {
          myFilesAlreadyExist.set(true);
        }

        FileTreeModel.Node newNode = treeModel.forceAddFile(path, null);
        myNodeToPreviewImage.put(newNode, icon);
      }

      // Collect all directory names from all generated file names for sorting purposes.
      // We use this map instead of looking at the file system when sorting, since
      // not all files/directories exist on disk at this point.
      Set<File> outputDirectories = pathIconMap.keySet()
        .stream()
        .flatMap(x -> {
          File root = resDir.getParentFile();
          List<File> directories = new ArrayList<>();
          x = x.getParentFile();
          while (x != null && !Objects.equals(x, root)) {
            directories.add(x);
            x = x.getParentFile();
          }
          return directories.stream();
        })
        .distinct()
        .collect(Collectors.toSet());

      // Sort the FileTreeModel so that the preview tree entries are sorted
      treeModel.sort(getFileComparator(outputDirectories));

      myOutputPreviewTree.setModel(treeModel);

      // The tree should be totally expanded by default
      // Note: There is subtle behavior here: even though we merely expand "rows", we
      //       actually end up expanding all entries in the tree, because
      //       "getRowCount()" keeps increasing as we expand each row.
      for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
        myOutputPreviewTree.expandRow(i);
      }

      // Select first file entry by default so that the preview panel shows something
      for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
        TreePath rowPath = myOutputPreviewTree.getPathForRow(i);
        if (rowPath != null) {
          if (treeModel.isLeaf(rowPath.getLastPathComponent())) {
            myOutputPreviewTree.setSelectionRow(i);
            break;
          }
        }
      }
    });
  }

  @NotNull
  private static Comparator<File> getFileComparator(Set<File> outputDirectories) {
    return (file1, file2) -> {
      // Sort by "directory vs file" first, then by density, then by name
      boolean isDirectory1 = outputDirectories.contains(file1);
      boolean isDirectory2 = outputDirectories.contains(file2);
      if (isDirectory1 == isDirectory2) {
        String path1 = file1.getAbsolutePath();
        String path2 = file2.getAbsolutePath();
        Density density1 = CategoryIconMap.pathToDensity(path1 + File.separator);
        Density density2 = CategoryIconMap.pathToDensity(path2 + File.separator);

        if (density1 != null && density2 != null && density1 != density2) {
          // Sort least dense to most dense
          return Ints.compare(density2.ordinal(), density1.ordinal());
        }
        else {
          return path1.compareTo(path2);
        }
      }
      else if (isDirectory1) {
        return -1;
      }
      else {
        return 1;
      }
    };
  }

  @Override
  public void dispose() {
    if (myEditorFactory != null && myFilePreviewEditor != null) {
      myEditorFactory.releaseEditor(myFilePreviewEditor);
    }
    myListeners.releaseAll();
  }
}
