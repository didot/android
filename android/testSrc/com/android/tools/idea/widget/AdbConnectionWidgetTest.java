/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.widget;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.wm.StatusBar;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class AdbConnectionWidgetTest {
  private final AtomicBoolean myIsConnected = new AtomicBoolean();
  private final AtomicBoolean myIsUserManaged = new AtomicBoolean();
  private final StatusBar myStatusBar = Mockito.mock(StatusBar.class);
  private final AtomicReference<Icon> myLastIcon = new AtomicReference<>();
  private AdbConnectionWidget myWidget;
  private TimerListener myTimerListener;

  public void setup(boolean isUserManaged) {
    myIsUserManaged.set(isUserManaged);

    AdbConnectionWidget.StudioAdapter adapter = new AdbConnectionWidget.StudioAdapter() {
      @Override
      public boolean isBridgeConnected() {
        return myIsConnected.get();
      }

      @Override
      public boolean isBridgeUserManagedMode() {
        return myIsUserManaged.get();
      }

      @NotNull
      @Override
      public ModalityState getModalityState() {
        return ModalityState.defaultModalityState();
      }

      @Nullable
      @Override
      public StatusBar getVisibleStatusBar() {
        return myStatusBar;
      }

      @Override
      public void addTimerListener(@NotNull TimerListener timerListener) {
        myTimerListener = timerListener;
      }

      @Override
      public void removeTimerListener(@NotNull TimerListener timerListener) {
        assertThat(timerListener).isSameAs(myTimerListener);
      }
    };

    myWidget = new AdbConnectionWidget(adapter);

    Mockito.doAnswer(new Answer<Void>() {
      @Override
      @Nullable
      public Void answer(@NotNull InvocationOnMock invocation) {
        myLastIcon.set(myWidget.getIcon());
        return null;
      }
    }).when(myStatusBar).updateWidget(AdbConnectionWidget.ID);
  }

  @Test
  public void testStudioManaged() {
    setup(false);

    // Ensure we're in the correct initial state.
    assert !myIsUserManaged.get();
    assert !myIsConnected.get();
    assert myTimerListener != null;
    assertThat(myWidget.getIcon()).isSameAs(AdbConnectionWidget.ConnectionState.STUDIO_MANAGED_DISCONNECTED.myIcon);

    // Simulate ADB is connected.
    myIsConnected.set(true);
    myTimerListener.run(); // "Advance" the clock.
    assertThat(myWidget.getIcon()).isSameAs(AdbConnectionWidget.ConnectionState.STUDIO_MANAGED_CONNECTED.myIcon);

    // Simulate disconnecting ADB.
    myIsConnected.set(false);
    myTimerListener.run(); // "Advance" the clock.
    assertThat(myWidget.getIcon()).isSameAs(AdbConnectionWidget.ConnectionState.STUDIO_MANAGED_DISCONNECTED.myIcon);
  }

  @Test
  public void testUserManaged() {
    setup(true);

    // Ensure we're in the correct initial state.
    assert !myIsConnected.get();
    assert myTimerListener != null;
    assertThat(myWidget.getIcon()).isSameAs(AdbConnectionWidget.ConnectionState.USER_MANAGED_DISCONNECTED.myIcon);

    // Simulate ADB is connected.
    myIsConnected.set(true);
    myTimerListener.run(); // "Advance" the clock.
    assertThat(myWidget.getIcon()).isSameAs(AdbConnectionWidget.ConnectionState.USER_MANAGED_CONNECTED.myIcon);

    // Simulate disconnecting ADB.
    myIsConnected.set(false);
    myTimerListener.run(); // "Advance" the clock.
    assertThat(myWidget.getIcon()).isSameAs(AdbConnectionWidget.ConnectionState.USER_MANAGED_DISCONNECTED.myIcon);
  }
}
