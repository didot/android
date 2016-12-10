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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class NetworkProfilerStage extends Stage {
  // If null, means no connection to show in the details pane.
  @Nullable
  private HttpData mySelectedConnection;

  private AspectModel<NetworkProfilerAspect> myAspect = new AspectModel<>();

  private final NetworkConnectionsModel myConnectionsModel =
    new RpcNetworkConnectionsModel(getStudioProfilers().getClient().getNetworkClient(), getStudioProfilers().getProcessId());

  private final NetworkRadioDataSeries myRadioDataSeries =
    new NetworkRadioDataSeries(getStudioProfilers().getClient().getNetworkClient(), getStudioProfilers().getProcessId());

  public NetworkProfilerStage(StudioProfilers profiler) {
    super(profiler);
  }

  @Override
  public ProfilerMode getProfilerMode() {
    boolean noSelection = getStudioProfilers().getTimeline().getSelectionRange().isEmpty();
    return mySelectedConnection == null && noSelection ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
  }

  @NotNull
  public NetworkConnectionsModel getConnectionsModel() {
    return myConnectionsModel;
  }

  @NotNull
  public NetworkRadioDataSeries getRadioDataSeries() {
    return myRadioDataSeries;
  }

  /**
   * Sets the active connection, or clears the previously selected active connection if given data is null.
   */
  public void setSelectedConnection(@Nullable HttpData data) {
    if (data != null && data.getResponsePayloadId() != null && data.getResponsePayloadFile() == null) {
      ByteString payload = myConnectionsModel.requestResponsePayload(data);
      File file = null;
      try {
        String contentType = data.getResponseField(HttpData.FIELD_CONTENT_TYPE);
        String extension = contentType == null ? "" : HttpData.guessFileExtensionFromContentType(contentType);
        file = FileUtil.createTempFile(data.getResponsePayloadId(), extension);
        FileOutputStream outputStream = new FileOutputStream(file);
        payload.writeTo(outputStream);
      } catch (IOException e) {
        return;
      } finally {
        if (file != null) {
          file.deleteOnExit();
        }
      }
      data.setResponsePayloadFile(file);
    }

    mySelectedConnection = data;
    getStudioProfilers().modeChanged();
    getAspect().changed(NetworkProfilerAspect.ACTIVE_CONNECTION);
  }

  /**
   * Returns the active connection, or {@code null} if no request is currently selected.
   */
  @Nullable
  public HttpData getSelectedConnection() {
    return mySelectedConnection;
  }

  @NotNull
  public AspectModel<NetworkProfilerAspect> getAspect() {
    return myAspect;
  }
}
