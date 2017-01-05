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

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.IdeProfilerServicesStub;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.FakeGrpcChannel;
import com.google.common.collect.ImmutableList;
import com.google.protobuf3jarjar.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RpcNetworkConnectionsModelTest {
  private static final ImmutableList<HttpData> FAKE_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(FakeNetworkService.newHttpData(0, 0, 7, 14))
      .add(FakeNetworkService.newHttpData(1, 2, 3, 6))
      .add(FakeNetworkService.newHttpData(2, 4, 0, 0))
      .add(FakeNetworkService.newHttpData(3, 8, 10, 12))
      .build();

  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("RpcNetworkConnectionsModelTest", FakeNetworkService.newBuilder().setHttpDataList(FAKE_DATA).build());
  private NetworkConnectionsModel myModel;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());
    myModel = new RpcNetworkConnectionsModel(profilers.getClient().getNetworkClient(), 12);
  }

  @Test
  public void requestResponsePayload() {
    HttpData data = new HttpData.Builder(0, 0, 0, 0).setResponsePayloadId("payloadId").build();
    assertEquals(FakeNetworkService.FAKE_PAYLOAD, myModel.requestResponsePayload(data).toStringUtf8());
  }

  @Test
  public void emptyRequestResponsePayload() {
    HttpData data = new HttpData.Builder(0, 0, 0, 0).build();
    assertEquals(ByteString.EMPTY, myModel.requestResponsePayload(data));
    data = new HttpData.Builder(0, 0, 0, 0).setResponsePayloadId("").build();
    assertEquals(ByteString.EMPTY, myModel.requestResponsePayload(data));
  }

  @Test
  public void rangeCanIncludeAllRequests() {
    checkGetData(0, 10, 0, 1, 2, 3);
  }

  @Test
  public void rangeCanExcludeTailRequests() {
    checkGetData(0, 6, 0, 1, 2);
  }

  @Test
  public void rangeCanExcludeHeadRequests() {
    checkGetData(8, 12, 0, 2, 3);
  }

  @Test
  public void rangeCanIncludeRequestsThatAreStillDownloading() {
    checkGetData(1000, 1002, 2);
  }

  @Test
  public void testRequestStartAndEndAreInclusive() {
    checkGetData(6, 8, 0, 1, 2, 3);
  }

  private void checkGetData(long startTimeS, long endTimeS, long... expectedIds) {
    Range range = new Range(TimeUnit.SECONDS.toMicros(startTimeS), TimeUnit.SECONDS.toMicros(endTimeS));
    List<HttpData> actualData = myModel.getData(range);
    assertEquals(expectedIds.length, actualData.size());

    for (int i = 0; i < actualData.size(); ++i) {
      HttpData data = actualData.get(i);
      long id = expectedIds[i];
      assertEquals(id, data.getId());
      assertEquals(FAKE_DATA.get((int)id).getStartTimeUs(), data.getStartTimeUs());
      assertEquals(FAKE_DATA.get((int)id).getDownloadingTimeUs(), data.getDownloadingTimeUs());
      assertEquals(FAKE_DATA.get((int)id).getEndTimeUs(), data.getEndTimeUs());
      assertEquals(FAKE_DATA.get((int)id).getMethod(), data.getMethod());
      assertEquals(FAKE_DATA.get((int)id).getUrl(), data.getUrl());
      assertEquals(FAKE_DATA.get((int)id).getTrace(), data.getTrace());
      assertEquals(FAKE_DATA.get((int)id).getResponsePayloadId(), data.getResponsePayloadId());
      assertEquals(FAKE_DATA.get((int)id).getResponseField("connId"), data.getResponseField("connId"));
    }
  }
}