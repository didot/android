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
package com.android.tools.idea.profilers;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.profilers.ProfilerClient;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Paths;

public class ProfilerService implements Disposable {

  public static ProfilerService getInstance(@NotNull Project project) {
    ProfilerService service = ServiceManager.getService(ProfilerService.class);
    service.myManager.initialize(project);
    return service;
  }

  private static final String DATASTORE_NAME = "DataStoreService";

  @NotNull
  private final StudioProfilerDeviceManager myManager;
  @NotNull
  private final ProfilerClient myClient;

  private ProfilerService() {
    String directory = Paths.get(System.getProperty("user.home"), ".android").toString() + File.separator;
    DataStoreService dataStoreService = new DataStoreService(DATASTORE_NAME,
                                                             directory + DATASTORE_NAME,
                                                             ApplicationManager.getApplication()::executeOnPooledThread);
    myManager = new StudioProfilerDeviceManager(dataStoreService);
    myClient = new ProfilerClient(DATASTORE_NAME);
    IdeSdks.subscribe(myManager, this);
  }

  @Override
  public void dispose() {
    myManager.dispose();
  }

  @NotNull
  public ProfilerClient getProfilerClient() {
    return myClient;
  }
}
