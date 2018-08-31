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
package com.android.tools.profilers.event;

import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.profilers.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class EventMonitor extends ProfilerMonitor {

  @NotNull
  private final EventModel<UserEvent> myUserEvents;

  @NotNull
  private final EventModel<LifecycleEvent> myActivityEvents;

  @NotNull
  private final EventModel<LifecycleEvent> myFragmentEvents;

  private boolean myEnabled;

  private Supplier<ProfilerMonitorTooltip<EventMonitor>> myTooltipBuilder;

  public EventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    assert myProfilers.getClient() != null;

    UserEventDataSeries events = new UserEventDataSeries(myProfilers.getClient(), myProfilers.getSession());
    myUserEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), events));

    ActivityEventDataSeries activities = new ActivityEventDataSeries(myProfilers.getClient(), myProfilers.getSession(), false);
    myActivityEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), activities));

    ActivityEventDataSeries fragments = new ActivityEventDataSeries(myProfilers.getClient(), myProfilers.getSession(), true);
    myFragmentEvents = new EventModel<>(new RangedSeries<>(getTimeline().getViewRange(), fragments));

    myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::onAgentStatusChanged);
    onAgentStatusChanged();
  }

  @Override
  public void enter() {
  }

  @Override
  public void exit() {
  }

  @NotNull
  public EventModel<UserEvent> getUserEvents() {
    return myUserEvents;
  }

  @NotNull
  public EventModel<LifecycleEvent> getActivityEvents() {
    return myActivityEvents;
  }

  @NotNull
  public EventModel<LifecycleEvent> getFragmentEvents() {
    return myFragmentEvents;
  }

  @Override
  public String getName() {
    return "EVENTS";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    if (myTooltipBuilder != null) {
      return myTooltipBuilder.get();
    }
    return new EventActivityTooltip(this);
  }

  public void setTooltipBuilder(Supplier<ProfilerMonitorTooltip<EventMonitor>> tooltip) {
    myTooltipBuilder = tooltip;
  }

  @Override
  public boolean canExpand() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  private void onAgentStatusChanged() {
    boolean agentAttached = myProfilers.isAgentAttached();
    if (myEnabled != agentAttached) {
      myEnabled = agentAttached;
      changed(Aspect.ENABLE);
    }
  }
}
