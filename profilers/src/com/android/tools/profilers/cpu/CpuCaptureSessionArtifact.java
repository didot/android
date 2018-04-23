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

import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An artifact representation of a CPU capture.
 */
public class CpuCaptureSessionArtifact implements SessionArtifact {

  @VisibleForTesting
  public static final String CAPTURING_SUBTITLE = "Capturing...";

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
    return ProfilingConfiguration.getDefaultConfigName(myInfo.getProfilerType());
  }

  public String getSubtitle() {
    if (myIsOngoingCapture) {
      return CAPTURING_SUBTITLE;
    }
    else if (isImportedSession()) {
      // For imported sessions, we show the time the file was imported, as it doesn't make sense to show the capture start time within the
      // session, which is always going to be 00:00:00
      DateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy, hh:mm a");
      return timeFormat.format(new Date(TimeUnit.NANOSECONDS.toMillis(mySession.getStartTimestamp())));
    }
    else {
      // Otherwise, we show the formatted timestamp of the capture relative to the session start time.
      return TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(getTimestampNs()));
    }
  }

  @Override
  public long getTimestampNs() {
    // For imported traces, we only have an artifact and it should be aligned with session's start time.
    if (isImportedSession()) {
      return 0;
    }
    // Otherwise, calculate the relative timestamp of the capture
    return myInfo.getFromTimestamp() - mySession.getStartTimestamp();
  }

  @Override
  public void onSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    boolean needsToChangeSession = mySession != myProfilers.getSession();
    if (needsToChangeSession) {
      myProfilers.getSessionsManager().setSession(mySession);
    }

    if (isImportedSession()) {
      // Sessions created from imported traces handle its selection callback via a session change listener, so we just return early here.
      return;
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

    myProfilers.getIdeServices().getFeatureTracker()
      .trackSessionArtifactSelected(this, myProfilers.getSessionsManager().isSessionAlive());
  }

  public boolean isOngoingCapture() {
    return myIsOngoingCapture;
  }

  private boolean isImportedSession() {
    return mySessionMetaData.getType() == Common.SessionMetaData.SessionType.CPU_CAPTURE;
  }

  public static List<SessionArtifact> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                          @NotNull Common.Session session,
                                                          @NotNull Common.SessionMetaData sessionMetaData) {
    GetTraceInfoResponse response = profilers.getClient().getCpuClient().getTraceInfo(
      GetTraceInfoRequest.newBuilder()
        .setSession(session)
        // We need to list imported traces and their timestamps might not be within the session range, so we search for max range.
        .setFromTimestamp(Long.MIN_VALUE)
        .setToTimestamp(Long.MAX_VALUE)
        .build());

    List<SessionArtifact> artifacts = new ArrayList<>();
    for (TraceInfo info : response.getTraceInfoList()) {
      artifacts.add(new CpuCaptureSessionArtifact(profilers, session, sessionMetaData, info, false));
    }

    // If there is an ongoing capture, add an artifact to represent it. If the session is not alive, there is not a capture in progress, so
    // we don't need to bother calling the service.
    if (SessionsManager.isSessionAlive(session)) {
      ProfilingStateResponse profilingStateResponse =
        profilers.getClient().getCpuClient().checkAppProfilingState(ProfilingStateRequest.newBuilder().setSession(session).build());

      if (profilingStateResponse.getBeingProfiled()) {
        TraceInfo ongoingTraceInfo = TraceInfo.newBuilder()
          .setProfilerType(profilingStateResponse.getConfiguration().getProfilerType())
          .setFromTimestamp(profilingStateResponse.getStartTimestamp())
          .build();
        artifacts.add(new CpuCaptureSessionArtifact(profilers, session, sessionMetaData, ongoingTraceInfo, true));
      }
    }
    return artifacts;
  }
}