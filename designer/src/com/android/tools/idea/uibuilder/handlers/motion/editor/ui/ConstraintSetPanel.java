/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag.Attribute;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This displays the constraint panel
 */
class ConstraintSetPanel extends JPanel {

  private MTag mSelectedTag; // the Primary selection
  private MTag[] mMultiSelectedTag; // the list if you are supporting multi-select
  MotionEditorSelector mListeners;
  private static boolean DEBUG = false;
  ArrayList<MTag> mParent; // mParent.get(0) is the direct parent
  MTag mConstraintSet;
  ArrayList<MTag> mDisplayedRows = new ArrayList<>();
  boolean building = false;
  Icon[] types = {MEIcons.LIST_STATE, MEIcons.LIST_GRAY_STATE, MEIcons.LIST_LAYOUT};
  String[] mask = {"Value", "Layout", "Motion", "Transform", "PropertySet"};
  DefaultTableModel mConstraintSetModel = new DefaultTableModel(
    new String[]{"Included", "ID", "Defined In"}, 0) {

    public Class getColumnClass(int column) {
      return (column == 0) ? Icon.class : String.class;
    }
  };

  JTable mConstraintSetTable = new JTable(mConstraintSetModel);
  private String mDerived;
  boolean showAll = true;
  private MeModel mMeModel;
  private JLabel mTitle;
  JButton mModifyMenu;

  AbstractAction createConstraint = new AbstractAction("Create Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.createConstraint(mSelectedTag, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction createSectionedConstraint = new AbstractAction("Create Sectioned Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      System.out.println(mSelectedTag == null ? "null" : mSelectedTag.getTagName());
      ConstraintSetPanelCommands.createSectionedConstraint(mMultiSelectedTag, mConstraintSet);
      buildTable();
    }
  };

  AbstractAction clearConstraint = new AbstractAction("Clear Constraint") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.clearConstraint(mSelectedTag, mConstraintSet);
      buildTable();
    }
  };
  AbstractAction moveConstraint = new AbstractAction("Move Constraints to layout") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.moveConstraint(mSelectedTag, mConstraintSet);

    }
  };

  AbstractAction overrideConstraint = new AbstractAction("Override all constraints") {
    @Override
    public void actionPerformed(ActionEvent e) {
      ConstraintSetPanelCommands.overrideConstraint(mSelectedTag, mConstraintSet);

    }
  };
  AbstractAction limitConstraint = new AbstractAction("Limit constraints to sections") {
    @Override
    public void actionPerformed(ActionEvent e) {

    }
  };

  ConstraintSetPanel() {
    super(new BorderLayout());
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.WEST);
    top.add(right, BorderLayout.EAST);
    mConstraintSetTable.getColumnModel().getColumn(0).setPreferredWidth(MEUI.scale(16));
    JCheckBox cbox = new JCheckBox("All");
    cbox.setSelected(true);
    cbox.addActionListener(e -> {
        showAll = cbox.isSelected();
        buildTable();
      }
    );
    JLabel label;
    left.add(label = new JLabel("ConstraintSet (", MEIcons.LIST_STATE, SwingConstants.LEFT));
    Font planeFont = label.getFont().deriveFont(Font.PLAIN);
    label.setFont(planeFont);
    left.add(mTitle = new JLabel("", SwingConstants.LEFT));
    left.add(label = new JLabel(")", SwingConstants.LEFT));
    label.setFont(planeFont);
    mTitle.setFont(mTitle.getFont().deriveFont(Font.BOLD));
    makeRightMenu(right);
    right.add(cbox);

    mConstraintSetTable.getSelectionModel().addListSelectionListener(e -> {
        int index = mConstraintSetTable.getSelectedRow();
        int[] allSelect = mConstraintSetTable.getSelectedRows();

        mModifyMenu.setEnabled(index != -1);
        mSelectedTag = null;

        if (index == -1) {
          mSelectedTag = null;
          mListeners
            .notifyListeners(MotionEditorSelector.Type.CONSTRAINT_SET, new MTag[]{mConstraintSet});
          return;
        }
        mMultiSelectedTag = new MTag[allSelect.length];
        for (int i = 0; i < allSelect.length; i++) {
          int k = allSelect[i];
          mMultiSelectedTag[i] = mDisplayedRows.get(k);
        }
        MTag[] tag =
          (mDisplayedRows.size() == 0) ? new MTag[0] : new MTag[]{mSelectedTag = mDisplayedRows.get(index)};
        mListeners.notifyListeners(MotionEditorSelector.Type.CONSTRAINT, tag);
        enableMenuItems(tag);

      }
    );
    JScrollPane transitionProperties = new JScrollPane(mConstraintSetTable);
    transitionProperties.setBorder(BorderFactory.createEmptyBorder());
    add(transitionProperties, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
  }

  private void enableMenuItems(MTag[] selected) {
    boolean hasSelection = selected.length > 0;
    mModifyMenu.setEnabled(hasSelection);
    if (!hasSelection) {
      return;
    }
    boolean inCurrentSelection = false;

    MTag[] tags = mConstraintSet.getChildTags();
    for (int i = 0; i < tags.length; i++) {
      if (tags[i].equals(selected[0])) {
        inCurrentSelection = true;
        break;
      }
    }
    if (inCurrentSelection) {
      createConstraint.setEnabled(false);
      createSectionedConstraint.setEnabled(false);
      clearConstraint.setEnabled(true);
      moveConstraint.setEnabled(true);
      overrideConstraint.setEnabled(true);
      limitConstraint.setEnabled(true);
    } else {
      createConstraint.setEnabled(true);
      createSectionedConstraint.setEnabled(true);
      clearConstraint.setEnabled(false);
      moveConstraint.setEnabled(false);
      overrideConstraint.setEnabled(false);
      limitConstraint.setEnabled(false);
    }
  }

  private void makeRightMenu(JPanel right) {
    mModifyMenu = MEUI.createToolBarButton(MEIcons.EDIT_MENU, MEIcons.EDIT_MENU_DISABLED, "modify constraint set");
    right.add(mModifyMenu);
    JPopupMenu popupMenu = new JPopupMenu();
    mModifyMenu.setEnabled(false);
    popupMenu.add(createConstraint);
    popupMenu.add(createSectionedConstraint);
    popupMenu.add(moveConstraint);
    popupMenu.add(clearConstraint);
    popupMenu.add(overrideConstraint);
    mModifyMenu.addActionListener(e -> {
      popupMenu.show(mModifyMenu, 0, 0);
    });
  }

  String buildListString(MTag tag) {
    String cid = tag.getAttributeValue("id");
    int noc = tag.getChildTags().length;
    String end = tag.getAttributeValue("constraintSetEnd");
    return "<html> <b> " + cid + " </b><br>" + noc + " Constraint" + ((noc == 1) ? "" : "s")
      + "</html>";
  }

  public void buildTable() {
    HashSet<String> found = new HashSet<>();
    mConstraintSetModel.setNumRows(0);
    mDisplayedRows.clear();
    if (mConstraintSet == null) {
      return;
    } else {
      MTag[] sets = mConstraintSet.getChildTags("Constraint");
      String derived = mConstraintSet.getAttributeValue("deriveConstraintsFrom");

      for (int i = 0; i < sets.length; i++) {
        MTag constraint = sets[i];
        Object[] row = new Object[4];
        String id = Utils.stripID(constraint.getAttributeValue("id"));
        found.add(id);
        row[1] = id;
        ArrayList<MTag> children = constraint.getChildren();
        HashMap<String, Attribute> attrs = constraint.getAttrList();
        // row[2] = getMask(children, attrs, id);
        row[2] = (derived == null) ? "layout" : findFirstDefOfView(id, mConstraintSet);
        row[0] = MEIcons.LIST_STATE;

        mDisplayedRows.add(constraint);
        mConstraintSetModel.addRow(row);
      }

      if (showAll && mMeModel.layout != null) {
        MTag[] allViews = mMeModel.layout.getChildTags();
        for (int j = 0; j < allViews.length; j++) {
          Object[] row = new Object[4];
          MTag view = allViews[j];
          String layoutId = view.getAttributeValue("id");

          if (layoutId == null) {
            row[0] = view.getTagName().substring(1 + view.getTagName().lastIndexOf("/"));
            continue;
          }

          layoutId = Utils.stripID(layoutId);
          if (found.contains(layoutId)) {
            continue;
          }

          row[1] = layoutId;
          //row[2] = "";
          row[2] = row[3] = (derived == null) ? "layout" : findFirstDefOfView(layoutId, mConstraintSet);
          row[0] = ("layout".equals(row[3])) ? null : MEIcons.LIST_GRAY_STATE;
          mDisplayedRows.add(view);
          mConstraintSetModel.addRow(row);
        }
      }
    }
    mConstraintSetModel.fireTableDataChanged();
  }

  private String findFirstDefOfView(String viewId, MTag constraintSet) {

    MTag[] sets = constraintSet.getChildTags("Constraint");
    for (int i = 0; i < sets.length; i++) {
      String cid = Utils.stripID(sets[i].getAttributeValue("id"));
      if (viewId.equals(cid)) {
        return Utils.stripID(constraintSet.getAttributeValue("id"));
      }
    }
    String derive = constraintSet.getAttributeValue("deriveConstraintsFrom");
    if (derive == null) {
      return "layout";
    }
    derive = Utils.stripID(derive);
    for (MTag child : mMeModel.motionScene.getChildren()) {
      if (child.getTagName().equals("ConstraintSet")) {
        String cid = Utils.stripID(child.getAttributeValue("id"));
        if (derive.equals(cid)) {
          return findFirstDefOfView(viewId, child);
        }
      }
    }
    return "???";
  }

  private String getMask(ArrayList<MTag> children, HashMap<String, Attribute> attrs, String id) {
    if (children.size() == 0 || attrs.size() > 1 && id != null) {
      return "all";
    } else {
      String mask = "";
      for (MTag child : children) {
        mask += (mask.equals("") ? "" : "|") + child.getTagName();
      }
      return mask;
    }
  }

  public void setMTag(MTag constraintSet, MeModel meModel) {
    if (DEBUG) {
      Debug.log("constraintSet = " + constraintSet);
      Debug.log("motionScene = " + meModel.motionScene);
      Debug.log("layout = " + meModel.layout);
    }
    int[] row = mConstraintSetTable.getSelectedRows();
    String[] selected = new String[row.length];
    for (int i = 0; i < row.length; i++) {
      selected[i] = (String) mConstraintSetModel.getValueAt(row[i], 1);
    }
    mMeModel = meModel;
    mConstraintSet = constraintSet;
    mDerived = null;
    if (mConstraintSet != null) {
      String derived = mConstraintSet.getAttributeValue("deriveConstraintsFrom");
      if (derived != null) {
        mDerived = Utils.stripID(derived);
        MTag[] constraintSets = meModel.motionScene.getChildTags("ConstraintSet");
        mParent = getDerived(constraintSets, mDerived);
      }
    }
    mTitle.setText(Utils.stripID(constraintSet.getAttributeValue("id")));
    buildTable();

    HashSet<String> selectedSet = new HashSet<>(Arrays.asList(selected));
    for (int i = 0; i < mConstraintSetModel.getRowCount(); i++) {
      String id = (String) mConstraintSetModel.getValueAt(i, 1);
      if (selectedSet.contains(id)) {
        mConstraintSetTable.addRowSelectionInterval(i, i);
      }
    }
  }

  ArrayList<MTag> getDerived(MTag[] constraintSets, String derived) {
    for (int i = 0; i < constraintSets.length; i++) {
      String id = Utils.stripID(constraintSets[i].getAttributeValue("id"));
      if (derived.equals(id)) {
        String also = constraintSets[i].getAttributeValue("deriveConstraintsFrom");
        if (also != null) {
          also = Utils.stripID(also);
          ArrayList<MTag> ret = getDerived(constraintSets, also);
          ret.add(0, constraintSets[i]);
          return ret;
        } else {
          ArrayList<MTag> ret = new ArrayList<>();
          ret.add(constraintSets[i]);
          return ret;
        }
      }
    }
    return new ArrayList<MTag>();
  }

  public void setListeners(MotionEditorSelector listeners) {
    mListeners = listeners;
  }
}
