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
package com.android.tools.idea.editors.navigation.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.Transient;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class NavigationModel {
  public static class Event {
    public enum Operation {INSERT, UPDATE, DELETE}

    public final Operation operation;
    public final Class<?> operandType;

    public Event(@NonNull Operation operation, @NonNull Class operandType) {
      this.operation = operation;
      this.operandType = operandType;
    }

    public static Event of(@NonNull Operation operation, @NonNull Class operandType) {
      return new Event(operation, operandType);
    }

    public static Event insert(@NonNull Class operandType) {
      return of(Operation.INSERT, operandType);
    }

    public static Event update(@NonNull Class operandType) {
      return of(Operation.UPDATE, operandType);
    }

    public static Event delete(@NonNull Class operandType) {
      return of(Operation.DELETE, operandType);
    }
  }

  private final EventDispatcher<Event> listeners = new EventDispatcher<Event>();

  private final ArrayList<State> states = new ArrayList<State>();
  private final ArrayList<Transition> transitions = new ArrayList<Transition>();
  private final Map<State, ModelPoint> stateToLocation = new HashMap<State, ModelPoint>();

  // todo change return type to List<State>
  @Transient
  public ArrayList<State> getStates() {
    return states;
  }

  @Transient
  public ArrayList<Transition> getTransitions() {
    return transitions;
  }

  @Transient
  public Map<State, ModelPoint> getStateToLocation() {
    return stateToLocation;
  }

  private static StatePointEntry toEntry(Map.Entry<State, ModelPoint> entry) {
    return new StatePointEntry(entry.getKey(), entry.getValue());
  }

  public Collection<StatePointEntry> getLocations() {
    Collection<StatePointEntry> result = new ArrayList<StatePointEntry>();
    for (Map.Entry<State, ModelPoint> entry : stateToLocation.entrySet()) {
      result.add(toEntry(entry));
    }
    return result;
  }

  public void setLocations(Collection<StatePointEntry> locations) {
    for (StatePointEntry entry : locations) {
      stateToLocation.put(entry.state, entry.point);
    }
  }

  public void addState(State state) {
    if (states.contains(state)) {
      return;
    }
    states.add(state);
    listeners.notify(Event.insert(State.class));
  }

  @SuppressWarnings("UnusedDeclaration")
  public void removeState(State state) {
    states.remove(state);
    for (Transition t : new ArrayList<Transition>(transitions)) {
      if (t.getSource().getState() == state || t.getDestination().getState() == state) {
        remove(t);
      }
    }
    listeners.notify(Event.delete(State.class));
  }

  private void updateStates(State state) {
    if (!states.contains(state)) {
      states.add(state);
    }
  }

  public boolean add(Transition transition) {
    boolean result = transitions.add(transition);
    // todo remove this
    updateStates(transition.getSource().getState());
    updateStates(transition.getDestination().getState());
    listeners.notify(Event.insert(Transition.class));
    return result;
  }

  public boolean remove(Transition transition) {
    boolean result = transitions.remove(transition);
    listeners.notify(Event.delete(Transition.class));
    return result;
  }

  public void accept(State.Visitor visitor) {
    for (State state : states) {
      state.accept(visitor);
    }
  }

  @Transient
  public Map<String, ActivityState> getActivities() {
    final Map<String, ActivityState> activities = new HashMap<String, ActivityState>();
    accept(new State.BaseVisitor() {
      @Override
      public void visit(ActivityState activityState) {
        activities.put(activityState.getClassName(), activityState);
      }
    });
    return activities;
  }

  @Transient
  public Map<String, MenuState> getMenus() {
    final Map<String, MenuState> menus = new HashMap<String, MenuState>();
    accept(new State.BaseVisitor() {
      @Override
      public void visit(MenuState menuState) {
        menus.put(menuState.getXmlResourceName(), menuState);
      }
    });
    return menus;
  }

  @Nullable
  public Transition findTransitionWithSource(@NonNull Locator source) {
    for (Transition transition : getTransitions()) {
      if (source.equals(transition.getSource())) {
        return transition;
      }
    }
    return null;
  }

  @Nullable
  public Transition findTransition(@NonNull Condition<Transition> condition) {
    for (Transition transition : getTransitions()) {
      if (condition.value(transition)) {
        return transition;
      }
    }
    return null;
  }

  @Nullable
  public MenuState findAssociatedMenuState(State state) {
    final Locator locator = Locator.of(state, null);
    Transition transition = findTransition(new Condition<Transition>() {
      @Override
      public boolean value(Transition transition) {
        return locator.equals(transition.getSource()) && transition.getDestination().getState() instanceof MenuState;
      }
    });
    if (transition != null) {
      return (MenuState)transition.getDestination().getState();
    }
    return null;
  }

  public List<State> findDestinationsFor(@NotNull State source) {
    List<State> result = new ArrayList<State>();
    for (Transition transition : getTransitions()) {
      if (source.equals(transition.getSource().getState())) {
        result.add(transition.getDestination().getState());
      }
    }
    return result;
  }

  public void clear() {
    states.clear();
    transitions.clear();
  }

  public void copyAllStatesAndTransitionsFrom(NavigationModel source) {
    states.addAll(source.getStates());
    transitions.addAll(source.getTransitions());
  }

  @Transient
  public EventDispatcher<Event> getListeners() {
    return listeners;
  }

  // todo either bury the superclass's API or re-implement all of its destructive methods to post an update event
}
