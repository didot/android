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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.components.labels.ActionLink;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class ActionLinkFixture extends JComponentFixture<ActionLinkFixture, ActionLink> {
  @NotNull
  public static ActionLinkFixture findByActionId(@NotNull final String actionId,
                                                 @NotNull final Robot robot,
                                                 @NotNull final Container container) {
    final Ref<ActionLink> actionLinkRef = new Ref<>();
    Wait.minutes(2).expecting("ActionLink with ID '" + actionId + "' to be visible")
      .until(() -> {
        Collection<ActionLink> found = robot.finder().findAll(container, new GenericTypeMatcher<ActionLink>(ActionLink.class) {
          @Override
          protected boolean isMatching(@NotNull ActionLink actionLink) {
            if (actionLink.isVisible()) {
              AnAction action = actionLink.getAction();
              String id = ActionManager.getInstance().getId(action);
              return actionId.equals(id);
            }
            return false;
          }
        });
        if (found.size() == 1) {
          actionLinkRef.set(getFirstItem(found));
          return true;
        }
        return false;
      });

    ActionLink actionLink = actionLinkRef.get();
    if (actionLink == null) {
      throw new ComponentLookupException("Failed to find ActionLink with ID '" + actionId + "'");
    }
    return new ActionLinkFixture(robot, actionLink);
  }

  private ActionLinkFixture(@NotNull Robot robot, @NotNull ActionLink target) {
    super(ActionLinkFixture.class, robot, target);
  }
}
