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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.idea.common.surface.DesignSurface;
import com.google.wireless.android.sdk.stats.MotionLayoutEditorEvent.MotionLayoutEditorEventType;

/**
 * This is the place holder class for Analytics
 */
public class Track {

  // TODO: Make sure to clean this so we don't leak surface.
  private static DesignSurface ourSurface;

  // TODO: We need to know when editor is open / closed so I can track that too.

  public static void init(DesignSurface surface) {
    ourSurface = surface;
  }

  private static void track(MotionLayoutEditorEventType motionType) {
    InternalMotionTrackerFactory.Companion.getInstance(ourSurface).track(motionType);
  }

  public static void createConstraintSet() {
    track(MotionLayoutEditorEventType.CREATE_CONSTRAINT);
  }

  public static void motionEditorEdit() {
    track(MotionLayoutEditorEventType.UNKNOWN_EVENT_TYPE); // TODO: Better type
  }

  public static void createTransition() {
    track(MotionLayoutEditorEventType.CREATE_TRANSITION);
  }

  public static void createOnClick() {
    track(MotionLayoutEditorEventType.CREATE_ONCLICK);
  }

  public static void createOnSwipe() {
    track(MotionLayoutEditorEventType.CREATE_ONCLICK);
  }

  public static void createKeyCycle() {
    track(MotionLayoutEditorEventType.CREATE_KEY_CYCLE);
  }

  public static void createKeyTimeCycle() {
    track(MotionLayoutEditorEventType.CREATE_KEY_TIME_CYCLE);
  }

  public static void createKeyTrigger() {
    track(MotionLayoutEditorEventType.CREATE_KEY_TRIGGER);
  }

  public static void createKeyPosition() {
    track(MotionLayoutEditorEventType.CREATE_KEY_POSITION);
  }

  public static void createKeyAttribute() {
    track(MotionLayoutEditorEventType.CREATE_KEY_ATTRIBUTES);
  }

  public static void playAnimation() {
    track(MotionLayoutEditorEventType.MOTION_PLAY);
  }

  public static void animationSpeed() {
    track(MotionLayoutEditorEventType.MOTION_SPEED);

  }

  public static void animationEnd() {
    track(MotionLayoutEditorEventType.MOTION_PAUSE);
  }

  public static void animationStart() {
    track(MotionLayoutEditorEventType.MOTION_PAUSE);
  }

  public static void animationDirectionToggle() {
    track(MotionLayoutEditorEventType.MOTION_DIRETION_TOGGLE);
  }

  public static void createConstraint() {
    track(MotionLayoutEditorEventType.CONVERT_TO_CONSTRAINT);
  }

  public static void clearConstraint() {
    track(MotionLayoutEditorEventType.CLEAR_CONSTRAINT);
  }

  public static void selectConstraint() {
    track(MotionLayoutEditorEventType.CONSTRAINT_TABLE_SELECT);
  }

  public static void changeLayout() {
    track(MotionLayoutEditorEventType.TOGGLE_LAYOUT);
  }

  public static void showConstraintSetTable() {
    track(MotionLayoutEditorEventType.SHOW_CONSTRAINTSET);
  }

  public static void transitionSelection() {
    track(MotionLayoutEditorEventType.SHOW_TIMELINE);
  }

  public static void showLayoutTable() {
    track(MotionLayoutEditorEventType.SHOW_LAYOUT);
  }
  public static void layoutTableSelect() {
    track(MotionLayoutEditorEventType.LAYOUT_TABLE_SELECT);
  }
  public static void timelineTableSelect() {
    track(MotionLayoutEditorEventType.TIMELINE_TABLE_SELECT);
  }
}



