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
package com.android.tools.idea.project;

import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_NOTIFICATION_GROUP;
import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_WARNING_MSG;
import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_WARNING_TITLE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.notification.NotificationType.WARNING;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.project.AndroidKtsSupportNotification.DisableAndroidKtsNotificationHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.IdeaTestCase;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidKtsSupportNotification}
 */
public class AndroidKtsSupportNotificationTest extends IdeaTestCase {
  @Mock private AndroidNotification myAndroidNotification;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(myProject).replaceProjectService(AndroidNotification.class, myAndroidNotification);
  }

  /**
   * Verify an AndroidNotification is created with the expected parameters
   */
  public void testShowWarningIfNotShown() {
    AndroidKtsSupportNotification myKtsNotification = new AndroidKtsSupportNotification(myProject);
    myKtsNotification.showWarningIfNotShown();
    verifyBalloon();
  }

  /**
   * Verify that warning is generated only once even when called multiple times
   */
  public void testShowWarningIfNotShownTwice() {
    AndroidKtsSupportNotification myKtsNotification = new AndroidKtsSupportNotification(myProject);
    myKtsNotification.showWarningIfNotShown();
    myKtsNotification.showWarningIfNotShown();
    verifyBalloon();
  }

  private void verifyBalloon() {
    ArgumentCaptor<NotificationHyperlink> hyperlinkCaptor = ArgumentCaptor.forClass(NotificationHyperlink.class);
    verify(myAndroidNotification, times(1)).showBalloon(same(KTS_WARNING_TITLE), same(KTS_WARNING_MSG), same(WARNING), same(KTS_NOTIFICATION_GROUP), hyperlinkCaptor.capture());
    List<NotificationHyperlink> hyperlinks = hyperlinkCaptor.getAllValues();
    assertThat(hyperlinks).hasSize(1);
    assertThat(hyperlinks.get(0)).isInstanceOf(DisableAndroidKtsNotificationHyperlink.class);
  }
}
