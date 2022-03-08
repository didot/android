/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.Nullable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceManagerWelcomeScreenActionTest {
  private @Nullable DeviceManagerWelcomeScreenAction myAction;
  private final @NotNull AnActionEvent myEvent;

  public DeviceManagerWelcomeScreenActionTest() {
    Presentation presentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(presentation);
  }

  @Test
  public void deviceManagerEnabled() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> false, () -> true);

    myAction.update(myEvent);

    assertTrue(myEvent.getPresentation().isVisible());
    assertTrue(myEvent.getPresentation().isEnabled());
  }

  @Test
  public void isChromeOsAndNotHWAccelerated() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> true, () -> true);

    myAction.update(myEvent);

    assertFalse(myEvent.getPresentation().isVisible());
  }

  @Test
  public void androidSdkNotAvailable() {
    myAction = new DeviceManagerWelcomeScreenAction(() -> false, () -> false);

    myAction.update(myEvent);

    assertFalse(myEvent.getPresentation().isEnabled());
  }
}
