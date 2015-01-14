/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.editors.navigation.macros.FragmentEntry;
import com.android.tools.idea.editors.navigation.model.*;
import com.android.tools.idea.editors.navigation.model.NavigationModel.Event;
import com.android.tools.idea.rendering.RenderedView;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

import static com.android.tools.idea.editors.navigation.NavigationView.Line;
import static com.android.tools.idea.editors.navigation.Utilities.diff;
import static com.android.tools.idea.editors.navigation.Utilities.sum;

class Selections {
  private static final Color SELECTION_COLOR = JBColor.BLUE;
  private static final int SELECTION_RECTANGLE_LINE_WIDTH = 4;

  public static Selection NULL = new EmptySelection();

  abstract static class Selection {

    protected abstract void moveTo(Point location);

    protected abstract Selection finaliseSelectionLocation(Point location);

    protected abstract void paint(Graphics g, boolean hasFocus);

    protected abstract void paintOver(Graphics g);

    protected abstract void remove();

    protected void configureInspector(Inspector inspector) {
    }
  }

  private static class EmptySelection extends Selection {
    @Override
    protected void moveTo(Point location) {
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
    }

    @Override
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      return this;
    }

    @Override
    protected void remove() {
    }
  }

  private static void configureHyperLinkLabelForClassName(final RenderingParameters renderingParameters,
                                                          HyperlinkLabel link,
                                                          @Nullable final String className) {
    link.setOpaque(false);
    if (className == null) {
      return;
    }
    link.setHyperlinkText(className.substring(1 + className.lastIndexOf('.')));
    link.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent hyperlinkEvent) {
        PsiClass psiClass = Utilities.getPsiClass(renderingParameters.myConfiguration.getModule(), className);
        if (psiClass != null) {
          AndroidRootComponent.launchEditor(renderingParameters, psiClass.getContainingFile(), false);
        }
      }
    });
  }

  private static void configureHyperlinkForXMLFile(final RenderingParameters renderingParameters,
                                                   HyperlinkLabel link,
                                                   @Nullable final String linkText,
                                                   @Nullable final String xmlFileName,
                                                   final boolean isMenu) {
    link.setOpaque(false);
    link.setHyperlinkText(linkText == null ? "" : linkText);
    link.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent hyperlinkEvent) {
        PsiFile layoutXmlFile =
          NavigationView.getLayoutXmlFile(isMenu, xmlFileName, renderingParameters.myConfiguration, renderingParameters.myProject);
        AndroidRootComponent.launchEditor(renderingParameters, layoutXmlFile, false);
      }
    });
  }

  private static void configureHyperlinkForXMLFile(final RenderingParameters renderingParameters,
                                                   HyperlinkLabel link,
                                                   @Nullable final String xmlFileName,
                                                   final boolean isMenu) {
    configureHyperlinkForXMLFile(renderingParameters, link, xmlFileName, xmlFileName, isMenu);
  }

  static class ComponentSelection<T extends Component> extends Selection {
    protected final RenderingParameters myRenderingParameters;
    protected final T myComponent;
    protected final Transition myTransition;
    protected final NavigationModel myNavigationModel;

    ComponentSelection(RenderingParameters renderingParameters, NavigationModel navigationModel, T component, Transition transition) {
      myRenderingParameters = renderingParameters;
      myNavigationModel = navigationModel;
      myComponent = component;
      myTransition = transition;
    }

    @Override
    protected void moveTo(Point location) {
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
      if (hasFocus) {
        Graphics2D g2D = (Graphics2D)g.create();
        g2D.setStroke(new BasicStroke(SELECTION_RECTANGLE_LINE_WIDTH));
        g2D.setColor(SELECTION_COLOR);
        Rectangle selection = myComponent.getBounds();
        int l = SELECTION_RECTANGLE_LINE_WIDTH / 2;
        selection.grow(l, l);
        g2D.drawRect(selection.x, selection.y, selection.width, selection.height);
      }
    }

    @Override
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      return this;
    }

    @Override
    protected void remove() {
    }

    @Override
    protected void configureInspector(Inspector inspector) {
      final Module module = myRenderingParameters.myConfiguration.getModule();
      TransitionInspector transitionInspector = new TransitionInspector();
      Locator source = myTransition.getSource();
      State sourceState = source.getState();
      configureHyperLinkLabelForClassName(myRenderingParameters, transitionInspector.sourceActivity, sourceState.getClassName());
      configureHyperLinkLabelForClassName(myRenderingParameters, transitionInspector.sourceFragment, source.fragmentClassName);
      boolean isFragment = source.fragmentClassName != null;
      String hostClassName = isFragment ? source.fragmentClassName : sourceState.getClassName();
      String xmlFileName = Analyser.getXMLFileName(module, hostClassName, !isFragment);
      configureHyperlinkForXMLFile(myRenderingParameters, transitionInspector.sourceViewId, source.viewName, xmlFileName, false);
      {
        JComboBox comboBox = transitionInspector.gesture;
        comboBox.addItem(Transition.PRESS);
        comboBox.setSelectedItem(myTransition.getType());
        comboBox.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent itemEvent) {
            myTransition.setType((String)itemEvent.getItem());
            myNavigationModel.getListeners().notify(Event.update(Transition.class));
          }
        });
      }
      configureHyperLinkLabelForClassName(myRenderingParameters, transitionInspector.destination,
                                          myTransition.getDestination().getState().getClassName());
      inspector.setInspectorComponent(transitionInspector.container);
    }
  }

  static class AndroidRootComponentSelection extends ComponentSelection<AndroidRootComponent> {
    protected final Point myMouseDownLocation;
    protected final Point myOrigComponentLocation;
    @NotNull private final State myState;
    private final Transform myTransform;

    AndroidRootComponentSelection(NavigationModel navigationModel,
                                  AndroidRootComponent component,
                                  Transition transition,
                                  RenderingParameters renderingParameters,
                                  Point mouseDownLocation,
                                  @NotNull State state,
                                  Transform transform) {
      super(renderingParameters, navigationModel, component, transition);
      myMouseDownLocation = mouseDownLocation;
      myOrigComponentLocation = myComponent.getLocation();
      myState = state;
      myTransform = transform;
    }

    private void moveTo(Point location, boolean snap) {
      Point newLocation = sum(diff(location, myMouseDownLocation), myOrigComponentLocation);
      if (snap) {
        newLocation = Utilities.snap(newLocation, myTransform.modelToView(ModelDimension.create(NavigationView.MIDDLE_SNAP_GRID)));
      }
      Map<State, ModelPoint> stateToLocation = myNavigationModel.getStateToLocation();
      Point oldLocation = myTransform.modelToView(stateToLocation.get(myState));
      stateToLocation.put(myState, myTransform.viewToModel(newLocation));
      MenuState menuState = myNavigationModel.findAssociatedMenuState(myState);
      if (menuState != null) {
        Point delta = diff(newLocation, oldLocation);
        stateToLocation.put(menuState, myTransform.viewToModel(sum(delta, myTransform.modelToView(stateToLocation.get(menuState)))));
      }
      myNavigationModel.getListeners().notify(Event.update(Map.class)); // just avoid State.class, which would trigger reload
    }

    @Override
    protected void moveTo(Point location) {
      moveTo(location, false);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
      moveTo(location, true);
      return this;
    }

    @Override
    protected void configureInspector(final Inspector inspector) {
      myState.accept(new State.Visitor() {
        @Override
        public void visit(ActivityState activity) {
          final Module module = myRenderingParameters.myConfiguration.getModule();
          ActivityInspector activityInspector = new ActivityInspector();
          {
            HyperlinkLabel link = activityInspector.classNameLabel;
            final String className = activity.getClassName();
            configureHyperLinkLabelForClassName(myRenderingParameters, link, className);
          }
          {
            HyperlinkLabel link = activityInspector.xmlFileNameLabel;
            String xmlFileName = Analyser.getXMLFileName(module, activity.getClassName(), true);
            configureHyperlinkForXMLFile(myRenderingParameters, link, xmlFileName, false);
          }
          {
            JPanel fragmentList = activityInspector.fragmentList;
            fragmentList.removeAll();
            fragmentList.setLayout(new BoxLayout(fragmentList, BoxLayout.Y_AXIS));
            for (FragmentEntry entry : activity.getFragments()) {
              HyperlinkLabel hyperlinkLabel = new HyperlinkLabel();
              configureHyperLinkLabelForClassName(myRenderingParameters, hyperlinkLabel, entry.className);
              fragmentList.add(hyperlinkLabel);
            }
          }
          inspector.setInspectorComponent(activityInspector.container);
        }

        @Override
        public void visit(MenuState menu) {
          MenuInspector menuInspector = new MenuInspector();
          configureHyperLinkLabelForClassName(myRenderingParameters, menuInspector.classNameLabel, menu.getClassName());
          configureHyperlinkForXMLFile(myRenderingParameters, menuInspector.xmlFileNameLabel, menu.getXmlResourceName(), true);
          inspector.setInspectorComponent(menuInspector.container);
        }
      });
    }
  }

  static class RelationSelection extends Selection {
    private final AndroidRootComponent mySourceComponent;
    private final NavigationView myNavigationView;
    private final RenderedView myNamedLeaf;
    @NotNull private Point myMouseLocation;

    RelationSelection(@NotNull AndroidRootComponent sourceComponent,
                      @NotNull Point mouseDownLocation,
                      @Nullable RenderedView namedLeaf,
                      @NotNull NavigationView navigationView) {
      mySourceComponent = sourceComponent;
      myMouseLocation = mouseDownLocation;
      myNamedLeaf = namedLeaf;
      myNavigationView = navigationView;
    }

    @Override
    protected void moveTo(Point location) {
      myMouseLocation = location;
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
    }

    @Override
    protected void paintOver(Graphics g) {
      int lineWidth = mySourceComponent.transform.modelToViewW(NavigationView.LINE_WIDTH);
      Graphics2D lineGraphics = NavigationView.createLineGraphics(g, lineWidth);
      Rectangle sourceBounds = NavigationView.getBounds(mySourceComponent, myNamedLeaf);
      Rectangle destBounds = myNavigationView.getNamedLeafBoundsAt(mySourceComponent, myMouseLocation);
      Rectangle sourceComponentBounds = mySourceComponent.getBounds();
      // if the mouse hasn't left the bounds of the originating component yet, use leaf bounds instead for the midLine calculation
      Rectangle startBounds = sourceComponentBounds.contains(myMouseLocation) ? sourceBounds : sourceComponentBounds;
      Line midLine = NavigationView.getMidLine(startBounds, new Rectangle(myMouseLocation));
      Point[] controlPoints = NavigationView.getControlPoints(sourceBounds, destBounds, midLine);
      myNavigationView.drawTransition(lineGraphics, sourceBounds, destBounds, controlPoints);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point mouseUpLocation) {
      myNavigationView.createTransition(mySourceComponent, myNamedLeaf, mouseUpLocation);
      return NULL;
    }

    @Override
    protected void remove() {
    }
  }
}
