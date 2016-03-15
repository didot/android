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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.Selection;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;

import java.util.ArrayList;

/**
 * Implements a mouse interaction started on a ConstraintLayout view group handler
 */
public class ConstraintInteraction extends Interaction {

  /**
   * The surface associated with this interaction.
   */
  private final ScreenView myScreenView;

  /**
   * Base constructor
   *
   * @param screenView the ScreenView we belong to
   * @param component the component we belong to
   */
  public ConstraintInteraction(@NonNull ScreenView screenView,
                               @NonNull NlComponent component) {
    myScreenView = screenView;
  }

  /**
   * Start the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param startMask The initial AWT mask for the interaction
   */
  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, int startMask) {
    super.begin(x, y, startMask);
    int androidX = Coordinates.getAndroidX(myScreenView, myStartX);
    int androidY = Coordinates.getAndroidY(myScreenView, myStartY);

    final NlModel nlModel = myScreenView.getModel();
    ConstraintModel.useNewModel(nlModel);
    ConstraintModel model = ConstraintModel.getModel();

    model.updateModifiers(startMask);
    model.mousePressed(androidX, androidY);
  }

  /**
   * Update the mouse interaction
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiers current modifier key mask
   */
  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, int modifiers) {
    super.update(x, y, modifiers);
    ConstraintModel model = ConstraintModel.getModel();
    model.updateModifiers(modifiers);
    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);
    model.mouseDragged(androidX, androidY);
  }

  /**
   * Ends the mouse interaction and commit the modifications if any
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiers current modifier key mask
   * @param canceled  True if the interaction was canceled, and false otherwise.
   */
  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    if (canceled) {
      return;
    }
    Project project = myScreenView.getModel().getProject();
    final NlModel nlModel = myScreenView.getModel();
    final int ax = Coordinates.getAndroidX(myScreenView, x);
    final int ay = Coordinates.getAndroidY(myScreenView, y);
    final int theModifiers = modifiers;

    XmlFile file = nlModel.getFile();

    String label = "Constraint";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NonNull Result result) throws Throwable {
        ConstraintModel model = ConstraintModel.getModel();
        model.updateModifiers(theModifiers);
        model.mouseReleased(ax, ay);

        Selection selection = model.getSelection();
        if (selection != null) {
          for (ConstraintWidget widget : selection.getModifiedWidgets()) {
            commitElement(widget, nlModel);
          }
        }
        selection.clearModifiedWidgets();
        SelectionModel selectionModel = myScreenView.getSelectionModel();
        selectionModel.clear();
        ArrayList<NlComponent> components = new ArrayList<NlComponent>();
        for (Selection.Element selectedElement : selection.getElements()) {
          WidgetDecorator decorator = (WidgetDecorator)selectedElement.widget.getCompanionWidget();
          NlComponent component = (NlComponent)decorator.getCompanionObject();
          components.add(component);
        }
        selectionModel.setSelection(components);
      }
    };
    action.execute();
    nlModel.notifyModified();
    myScreenView.getSurface().repaint();
  }

  /**
   * Utility function to commit to the NlModel the current state of the given widget
   *
   * @param widget the widget we want to save to the model
   * @param model the model to save to
   */
  private void commitElement(@NonNull ConstraintWidget widget, @NonNull NlModel model) {
    WidgetDecorator decorator = (WidgetDecorator)widget.getCompanionWidget();
    NlComponent component = (NlComponent) decorator.getCompanionObject();
    for (NlComponent c : model.getComponents()) {
      if (c.getId() != null && c.getId().equalsIgnoreCase(component.getId())) {
        component = c;
        break;
      }
    }
    ConstraintUtilities.setEditorPosition(component, widget.getX(), widget.getY());
    ConstraintUtilities.setDimension(component, widget);
    for (ConstraintAnchor anchor : widget.getAnchors()) {
      ConstraintUtilities.setConnection(component, anchor);
    }
  }
}
