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
package com.android.tools.idea.run.tasks;

import com.intellij.notification.NotificationListener;

public class LaunchResult {
  private boolean mySuccess;
  private String myError;
  private String myErrorId;
  private String myConsoleError;
  private NotificationListener myNotificationListener;

  public LaunchResult() {
    mySuccess = true;
    myError = "";
    myErrorId = "";
    myConsoleError = "";
    myNotificationListener = null;
  }

  public void setSuccess(boolean success) {
    mySuccess = success;
  }

  public boolean getSuccess() {
    return mySuccess;
  }

  public void setError(String error) {
    myError = error;
  }

  public String getError() {
    return myError;
  }

  public void setErrorId(String id) {
    myErrorId = id;
  }

  public String getErrorId() {
    return myErrorId;
  }

  public void setConsoleError(String error) {
    myConsoleError = error;
  }

  public String getConsoleError() {
    return myConsoleError;
  }

  public NotificationListener getNotificationListener() {
    return myNotificationListener;
  }

  public void setNotificationListener(NotificationListener listener) {
    myNotificationListener = listener;
  }
}
