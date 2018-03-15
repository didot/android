/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An artifact representation of a CPU capture.
 */
public class CpuCaptureSessionArtifact implements SessionArtifact {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  @NotNull private final TraceInfo myInfo;
  private final boolean myIsOngoingCapture;

  public CpuCaptureSessionArtifact(@NotNull StudioProfilers profilers,
                                   @NotNull Common.Session session,
                                   @NotNull Common.SessionMetaData sessionMetaData,
                                   @NotNull TraceInfo info,
                                   boolean isOngoingCapture) {
    myProfilers = profilers;
    mySession = session;
    mySessionMetaData = sessionMetaData;
    myInfo = info;
    myIsOngoingCapture = isOngoingCapture;
  }

  @NotNull
  @Override
  public StudioProfilers getProfilers() {
    return myProfilers;
  }

  @Override
  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  @NotNull
  @Override
  public Common.SessionMetaData getSessionMetaData() {
    return mySessionMetaData;
  }

  @Override
  @NotNull
  public String getName() {
    return "CPU Trace";
  }

  @Override
  public long getTimestampNs() {
    return myInfo.getFromTimestamp() - mySession.getStartTimestamp();
  }

  @Override
  public void onSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    boolean needsToChangeSession = mySession != myProfilers.getSession();
    if (needsToChangeSession) {
      myProfilers.getSessionsManager().setSession(mySession);
    }

    // If CPU profiler is not yet open, we need to do it.
    boolean needsToOpenCpuProfiler = !(myProfilers.getStage() instanceof CpuProfilerStage);
    if (needsToOpenCpuProfiler) {
      myProfilers.setStage(new CpuProfilerStage(myProfilers));
    }

    // If the capture is in progress we jump to its start range
    if (myIsOngoingCapture) {
      // Jump to the ongoing capture. We don't jump to live immediately because the ongoing capture might not fit the current zoom level.
      // So first we adjust the zoom level to fit the current size of the ongoing capture + 10% of the view range, so the user can see the
      // capture animating for a while before it takes the entire view range.
      ProfilerTimeline timeline = myProfilers.getTimeline();
      double viewRange90PercentLength = 0.9 * timeline.getViewRange().getLength();
      double currentOngoingCaptureLength = timeline.getDataRange().getMax() - TimeUnit.NANOSECONDS.toMicros(myInfo.getFromTimestamp());
      if (currentOngoingCaptureLength > viewRange90PercentLength) {
        timeline.zoomOutBy(currentOngoingCaptureLength - viewRange90PercentLength);
      }

      // Then jump to live.
      timeline.setStreaming(true);
      timeline.setIsPaused(false);
    }
    // Otherwise, we set and select the capture in the CpuProfilerStage
    else {
      assert myProfilers.getStage() instanceof CpuProfilerStage;
      ((CpuProfilerStage)myProfilers.getStage()).setAndSelectCapture(myInfo.getTraceId());
    }
  }

  public boolean isOngoingCapture() {
    return myIsOngoingCapture;
  }

  public static List<SessionArtifact> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                          @NotNull Common.Session session,
                                                          @NotNull Common.SessionMetaData sessionMetaData) {
    GetTraceInfoResponse response = profilers.getClient().getCpuClient().getTraceInfo(
      GetTraceInfoRequest.newBuilder().setSession(session).setFromTimestamp(session.getStartTimestamp())
        .setToTimestamp(session.getEndTimestamp()).build());

    List<SessionArtifact> artifacts = new ArrayList<>();
    for (TraceInfo info : response.getTraceInfoList()) {
      artifacts.add(new CpuCaptureSessionArtifact(profilers, session, sessionMetaData, info, false));
    }

    // If there is an ongoing capture, add an artifact to represent it. If the session is not alive, there is not a capture in progress, so
    // we don't need to bother calling the service.
    boolean isSessionAlive = session.getEndTimestamp() == Long.MAX_VALUE;
    if (isSessionAlive) {
      ProfilingStateResponse profilingStateResponse =
        profilers.getClient().getCpuClient().checkAppProfilingState(ProfilingStateRequest.newBuilder().setSession(session).build());

      if (profilingStateResponse.getBeingProfiled()) {
        TraceInfo ongoingTraceInfo =
          TraceInfo.newBuilder().setFromTimestamp(profilingStateResponse.getStartTimestamp()).build();
        artifacts.add(new CpuCaptureSessionArtifact(profilers, session, sessionMetaData, ongoingTraceInfo, true));
      }
    }
    return artifacts;
  }
}