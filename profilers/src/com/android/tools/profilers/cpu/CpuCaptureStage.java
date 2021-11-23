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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.BoxSelectionModel;
import com.android.tools.adtui.model.DefaultTimeline;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.adtui.model.trackgroup.TrackGroupActionListener;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.EventStreamServer;
import com.android.tools.profiler.perfetto.proto.TraceProcessor;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.analysis.AndroidFrameTimelineAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.analysis.CpuFullTraceAnalysisModel;
import com.android.tools.profilers.cpu.analysis.FramesAnalysisModel;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTooltip;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTrackModel;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineModel;
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineTooltip;
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTooltip;
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTrackModel;
import com.android.tools.profilers.cpu.systemtrace.CpuCoreTrackModel;
import com.android.tools.profilers.cpu.systemtrace.CpuFrameTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuFrequencyTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuFrequencyTrackModel;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData;
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo;
import com.android.tools.profilers.cpu.systemtrace.DeadlineTextModel;
import com.android.tools.profilers.cpu.systemtrace.FrameState;
import com.android.tools.profilers.cpu.systemtrace.RssMemoryTooltip;
import com.android.tools.profilers.cpu.systemtrace.RssMemoryTrackModel;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTooltip;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTrackModel;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame;
import com.android.tools.profilers.cpu.systemtrace.VsyncTooltip;
import com.android.tools.profilers.cpu.systemtrace.VsyncTrackModel;
import com.android.tools.profilers.event.LifecycleEventDataSeries;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.UserEventDataSeries;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorModelKt;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class holds the models and capture data for the {@code com.android.tools.profilers.cpu.CpuCaptureStageView}.
 * This stage is set when a capture is selected from the {@link CpuProfilerStage}, or when a capture is imported.
 */
public class CpuCaptureStage extends Stage<Timeline> {
  public enum Aspect {
    /**
     * Triggered when the stage changes state from parsing to analyzing. This can also be viewed as capture parsing completed.
     * If the capture parsing fails the stage will transfer back to the {@link CpuProfilerStage}
     */
    STATE,
    /**
     * Triggered when a new analysis model is added / removed.
     */
    ANALYSIS_MODEL_UPDATED,
  }

  public enum State {
    /**
     * Initial state set when creating this stage. Parsing happens when {@link #enter} is called
     */
    PARSING,
    /**
     * When parsing has completed the state transitions to analyzing, we remain in this state while viewing data of the capture.
     */
    ANALYZING,
  }

  /**
   * Helper function to save trace data to disk. The file is put in to the users temp directory with the format cpu_trace_[traceid].trace.
   * If the file exists FileUtil will append numbers to the end making it unique.
   */
  @NotNull
  public static File saveCapture(long traceId, ByteString data) {
    try {
      File trace = FileUtil.createTempFile(String.format(Locale.US, "cpu_trace_%d", traceId), ".trace", true);
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(data.toByteArray());
      }
      return trace;
    }
    catch (IOException io) {
      throw new IllegalStateException("Unable to save trace to disk");
    }
  }

  @Nullable
  private static File getAndSaveCapture(@NotNull StudioProfilers profilers, long traceId) {
    Transport.BytesRequest traceRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(profilers.getSession().getStreamId())
      .setId(String.valueOf(traceId))
      .build();
    Transport.BytesResponse traceResponse = profilers.getClient().getTransportClient().getBytes(traceRequest);
    if (!traceResponse.getContents().isEmpty()) {
      return saveCapture(traceId, traceResponse.getContents());
    }
    return null;
  }

  /**
   * Responsible for parsing trace files into {@link CpuCapture}.
   * Parsed captures should be obtained from this object.
   */
  private final CpuCaptureHandler myCpuCaptureHandler;
  private final AspectModel<Aspect> myAspect = new AspectModel<>();
  private final List<CpuAnalysisModel<?>> myPinnedAnalysisModels = new ArrayList<>();
  private final List<TrackGroupModel> myTrackGroupModels = new ArrayList<>();
  private final MultiSelectionModel<CpuAnalyzable<?>> myMultiSelectionModel = new MultiSelectionModel<>();

  private CpuCaptureMinimapModel myMinimapModel;
  private State myState = State.PARSING;

  // Accessible only when in state analyzing
  private CpuCapture myCapture;

  /**
   * The track groups share a timeline based on the minimap selection.
   * <p>
   * Data range: capture range;
   * View range: minimap selection;
   * Tooltip range: all track groups share the same mouse-over range, different from the minimap or profilers;
   * Selection range: union of all selected trace events;
   */
  private final Timeline myTrackGroupTimeline = new DefaultTimeline();

  /**
   * Create a capture stage that loads a given trace id. If a trace id is not found null will be returned.
   */
  @Nullable
  public static CpuCaptureStage create(@NotNull StudioProfilers profilers, @NotNull ProfilingConfiguration configuration, long traceId) {
    File captureFile = getAndSaveCapture(profilers, traceId);
    if (captureFile == null) {
      return null;
    }
    String captureProcessNameHint = CpuProfiler.getTraceInfoFromId(profilers, traceId).getConfiguration().getAppName();
    return new CpuCaptureStage(profilers, configuration, captureFile, traceId, captureProcessNameHint, profilers.getSession().getPid());
  }

  /**
   * Create a capture stage based on a file, this is used for importing traces. In the absence of a trace ID, the session ID is used.
   */
  @NotNull
  public static CpuCaptureStage create(@NotNull StudioProfilers profilers,
                                       @NotNull ProfilingConfiguration configuration,
                                       @NotNull File captureFile,
                                       long sessionId) {
    return new CpuCaptureStage(profilers, configuration, captureFile, sessionId, null, 0);
  }

  /**
   * Create a capture stage that loads a given file.
   */
  @VisibleForTesting
  public CpuCaptureStage(@NotNull StudioProfilers profilers,
                  @NotNull ProfilingConfiguration configuration,
                  @NotNull File captureFile,
                  long traceId,
                  @Nullable String captureProcessNameHint,
                  int captureProcessIdHint) {
    super(profilers);
    myCpuCaptureHandler = new CpuCaptureHandler(
      profilers.getIdeServices(), captureFile, traceId, configuration, captureProcessNameHint, captureProcessIdHint);
  }

  public State getState() {
    return myState;
  }

  @NotNull
  public AspectModel<Aspect> getAspect() {
    return myAspect;
  }

  @NotNull
  public CpuCaptureHandler getCaptureHandler() {
    return myCpuCaptureHandler;
  }

  @NotNull
  public List<TrackGroupModel> getTrackGroupModels() {
    return myTrackGroupModels;
  }

  @NotNull
  public MultiSelectionModel<CpuAnalyzable<?>> getMultiSelectionModel() {
    return myMultiSelectionModel;
  }

  @NotNull
  public CpuCaptureMinimapModel getMinimapModel() {
    assert myState == State.ANALYZING;
    return myMinimapModel;
  }

  @NotNull
  public List<CpuAnalysisModel<?>> getPinnedAnalysisModels() {
    return myPinnedAnalysisModels;
  }

  /**
   * @return the capture timeline, different from the profiler's session timeline.
   */
  @NotNull
  public Timeline getCaptureTimeline() {
    return getCapture().getTimeline();
  }

  /**
   * @return the track group timeline specific to the capture stage.
   */
  @NotNull
  @Override
  public Timeline getTimeline() {
    return myTrackGroupTimeline;
  }

  private void setState(State state) {
    myState = state;
    myAspect.changed(Aspect.STATE);
  }

  @NotNull
  public CpuCapture getCapture() {
    assert myState == State.ANALYZING;
    return myCapture;
  }

  @Override
  public void enter() {
    getStudioProfilers().getUpdater().register(myCpuCaptureHandler);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());
    myCpuCaptureHandler.parse(capture -> {
      try {
        if (capture == null) {
          // Generic catch all for capture failing to load, this happens for both import and live captures.
          if (getStudioProfilers().getSessionsManager().isSessionAlive()) {
            // User will get a notification then sent back to the CpuProfilerStage
            getStudioProfilers().getIdeServices().getMainExecutor()
              .execute(() -> getStudioProfilers().setStage(getParentStage()));
          }
          else {
            // If the user was importing a trace the user will be sent to the null stage with an error + notification.
            getStudioProfilers().getIdeServices().getMainExecutor().execute(
              () -> getStudioProfilers().setStage(new NullMonitorStage(
                getStudioProfilers(),
                "The profiler was unable to parse the trace file. Please make sure the file selected is a valid trace.")));
          }
        }
        else {
          myCapture = capture;
          onCaptureParsed(capture);
          setState(State.ANALYZING);
        }
      }
      catch (Exception ex) {
        // Logging if an exception happens since setState may trigger various callbacks.
        Logger.getInstance(CpuCaptureStage.class).error(ex);
      }
    });
  }

  @Override
  public void exit() {
    getStudioProfilers().getUpdater().unregister(myCpuCaptureHandler);
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.CPU_CAPTURE_STAGE;
  }

  private void addPinnedCpuAnalysisModel(@NotNull CpuAnalysisModel<?> model) {
    myPinnedAnalysisModels.add(model);
    myAspect.changed(Aspect.ANALYSIS_MODEL_UPDATED);
  }

  @NotNull
  @Override
  public Stage<?> getParentStage() {
    return new CpuProfilerStage(getStudioProfilers());
  }

  @NotNull
  @Override
  public Class<? extends Stage<?>> getHomeStageClass() {
    return CpuProfilerStage.class;
  }

  private void onCaptureParsed(@NotNull CpuCapture capture) {
    myTrackGroupTimeline.getDataRange().set(capture.getRange());
    myMinimapModel = new CpuCaptureMinimapModel(getStudioProfilers(), capture, getTimeline().getViewRange());
    if (!capture.getRange().isEmpty()) {
      initTrackGroupList(capture);
      addPinnedCpuAnalysisModel(new CpuFullTraceAnalysisModel(capture, getTimeline().getViewRange()));

      CpuAnalysisModel<?> jankModel = AndroidFrameTimelineAnalysisModel.of(capture);
      CpuAnalysisModel<?> framesModel = FramesAnalysisModel.of(capture);

      if (getStudioProfilers().getIdeServices().getFeatureConfig().isJankDetectionUiEnabled()) {
        if (jankModel != null) {
          addPinnedCpuAnalysisModel(jankModel);
        } else if (framesModel != null) {
          addPinnedCpuAnalysisModel(framesModel);
        }
      } else {
        if (framesModel != null) {
          addPinnedCpuAnalysisModel(framesModel);
        }
      }
    }
    if (getStudioProfilers().getSession().getPid() == 0) {
      // For an imported traces we need to insert a CPU_TRACE event into the database. This is used by the Sessions' panel to display the
      // correct trace type associated with the imported file.
      insertImportedTraceEvent(capture);
    }
  }

  private void insertImportedTraceEvent(@NotNull CpuCapture capture) {
    Cpu.CpuTraceInfo importedTraceInfo = Cpu.CpuTraceInfo.newBuilder()
      // Use session ID as trace ID for imported traces.
      .setTraceId(getStudioProfilers().getSession().getSessionId())
      .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMin()))
      .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)capture.getRange().getMax()))
      .setConfiguration(
        Cpu.CpuTraceConfiguration
          .newBuilder()
          .setUserOptions(Cpu.CpuTraceConfiguration.UserOptions.newBuilder().setTraceType(capture.getType())))
      .build();
    // TODO(b/141560550): add test when we can mock TransportService#registerStreamServer.
    EventStreamServer streamServer =
      getStudioProfilers().getSessionsManager().getEventStreamServer(getStudioProfilers().getSession().getStreamId());
    if (streamServer != null) {
      streamServer.getEventDeque().offer(
        Common.Event.newBuilder()
          .setGroupId(importedTraceInfo.getTraceId())
          .setTimestamp(importedTraceInfo.getToTimestamp())
          .setIsEnded(true)
          .setKind(Common.Event.Kind.CPU_TRACE)
          .setCpuTrace(
            Cpu.CpuTraceData.newBuilder().setTraceEnded(Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(importedTraceInfo)))
          .build());
    }
  }

  /**
   * The order of track groups dictates their default order in the UI.
   */
  private void initTrackGroupList(@NotNull CpuCapture capture) {
    myTrackGroupModels.clear();

    FeatureTracker featureTracker = getStudioProfilers().getIdeServices().getFeatureTracker();
    // Interaction events, e.g. user interaction, app lifecycle. Recorded trace only.
    if (getStudioProfilers().getSession().getPid() != 0) {
      myTrackGroupModels.add(createInteractionTrackGroup(getStudioProfilers(), getTimeline()));
    }

    if (capture instanceof SystemTraceCpuCapture && capture.getSystemTraceData() != null) {
      createDisplayPipelineTrackGroups(getStudioProfilers(), (SystemTraceCpuCapture)capture, getTimeline(), myMultiSelectionModel)
        .forEach(myTrackGroupModels::add);
    }

    // In S with both timeline and lifecycle data, we move threads closer to display
    if (capture instanceof SystemTraceCpuCapture && capture.getSystemTraceData() != null &&
        !capture.getSystemTraceData().getAndroidFrameTimelineEvents().isEmpty() &&
        getStudioProfilers().getIdeServices().getFeatureConfig().isJankDetectionUiEnabled()) {
      // Thread states and trace events.
      myTrackGroupModels.add(createThreadsTrackGroup(capture, getTimeline(), getMultiSelectionModel(), featureTracker));
      // CPU per-core usage and event etc. Systrace only.
      myTrackGroupModels.add(createCpuCoresTrackGroup(capture.getMainThreadId(), capture.getSystemTraceData(), getTimeline()));
      // RSS memory counters.
      myTrackGroupModels.add(createRssMemoryTrackGroup(capture.getSystemTraceData(), getTimeline()));
    } else {
      if(capture instanceof SystemTraceCpuCapture && capture.getSystemTraceData() != null) {
        // CPU per-core usage and event etc. Systrace only.
        myTrackGroupModels.add(createCpuCoresTrackGroup(capture.getMainThreadId(), capture.getSystemTraceData(), getTimeline()));
        // RSS memory counters.
        myTrackGroupModels.add(createRssMemoryTrackGroup(capture.getSystemTraceData(), getTimeline()));
      }
      // Thread states and trace events.
      myTrackGroupModels.add(createThreadsTrackGroup(capture, getTimeline(), getMultiSelectionModel(), featureTracker));
    }

    // Add action listener for tracking all track group actions.
    myTrackGroupModels.forEach(model -> model.addActionListener(new TrackGroupActionListener() {
      @Override
      public void onGroupMovedUp(@NotNull String title) {
        featureTracker.trackMoveTrackGroupUp(title);
      }

      @Override
      public void onGroupMovedDown(@NotNull String title) {
        featureTracker.trackMoveTrackGroupDown(title);
      }

      @Override
      public void onGroupCollapsed(@NotNull String title) {
        featureTracker.trackCollapseTrackGroup(title);
      }

      @Override
      public void onGroupExpanded(@NotNull String title) {
        featureTracker.trackExpandTrackGroup(title);
      }
    }));
  }

  private static TrackGroupModel createInteractionTrackGroup(@NotNull StudioProfilers studioProfilers, @NotNull Timeline timeline) {
    TrackGroupModel interaction = TrackGroupModel.newBuilder().setTitle("Interaction").build();
    EventModel<UserEvent> userEventEventModel =
      new EventModel<>(new RangedSeries<>(timeline.getViewRange(), new UserEventDataSeries(studioProfilers)));
    LifecycleEventModel lifecycleEventModel =
      new LifecycleEventModel(
        new RangedSeries<>(timeline.getViewRange(), new LifecycleEventDataSeries(studioProfilers, false)),
        new RangedSeries<>(timeline.getViewRange(), new LifecycleEventDataSeries(studioProfilers, true)));
    interaction.addTrackModel(
      TrackModel.newBuilder(userEventEventModel, ProfilerTrackRendererType.USER_INTERACTION, "User")
        .setDefaultTooltipModel(new UserEventTooltip(timeline, userEventEventModel)));
    interaction.addTrackModel(
      TrackModel.newBuilder(lifecycleEventModel, ProfilerTrackRendererType.APP_LIFECYCLE, "Lifecycle")
        .setDefaultTooltipModel(new LifecycleTooltip(timeline, lifecycleEventModel)));
    return interaction;
  }

  private static Stream<TrackGroupModel> createDisplayPipelineTrackGroups
        (@NotNull StudioProfilers profilers,
         @NotNull SystemTraceCpuCapture capture,
         @NotNull Timeline timeline,
         @NotNull MultiSelectionModel<CpuAnalyzable<?>> multiSelectionModel) {
    CpuSystemTraceData data = capture.getSystemTraceData();
    final boolean isJankDetectionOn =
      profilers.getIdeServices().getFeatureConfig().isJankDetectionUiEnabled() &&
      !data.getAndroidFrameTimelineEvents().isEmpty();

    return isJankDetectionOn
           ? Stream.of(createJankDetectionTrackGroup(capture, data, timeline, multiSelectionModel))
           : Stream.concat(
             // Display pipeline events, e.g. frames, surfaceflinger. Systrace only.
             Stream.of(createDisplayTrackGroup(data, timeline)),
             // Android frame lifecycle. Systrace only.
             IntStream.range(0, data.getAndroidFrameLayers().size())
               .mapToObj(layerIndex ->
                           createFrameLifecycleTrackGroup(data.getAndroidFrameLayers().get(layerIndex), timeline,
                                                          // Collapse all but the first layer.
                                                          layerIndex > 0,
                                                          data,
                                                          multiSelectionModel)));
  }

  private static TrackGroupModel createDisplayTrackGroup(
    @NotNull CpuSystemTraceData systemTraceData, @NotNull Timeline timeline) {

    TrackGroupModel display = TrackGroupModel.newBuilder()
      .setTitle("Display")
      .setTitleHelpText("This section contains display info. " +
                        "<p><b>Frames</b>: when a frame is being drawn. Long frames are colored red.</p>" +
                        "<p><b>SurfaceFlinger</b>: system process responsible for sending buffers to display.</p>" +
                        "<p><b>VSYNC</b>: a signal that synchronizes the display pipeline.</p>" +
                        "<p><b>BufferQueue</b>: how many frame buffers are queued up, waiting for SurfaceFlinger to consume.</p>")
      // TODO(b/202330841)
      .setTitleHelpLink("Learn more", "https://source.android.com/devices/graphics")
      .build();

    // Frame
    if (systemTraceData.getAndroidFrameLayers().isEmpty()) {
      // Hide frames if we have Frame Lifecycle data.
      FrameState mainFrames = new FrameState(SystemTraceFrame.FrameThread.MAIN, systemTraceData, timeline.getViewRange());
      CpuFrameTooltip mainFrameTooltip = new CpuFrameTooltip(timeline);
      mainFrameTooltip.setFrameSeries(mainFrames.getSeries());
      display.addTrackModel(
        TrackModel.newBuilder(mainFrames, ProfilerTrackRendererType.FRAMES, "Frames").setDefaultTooltipModel(mainFrameTooltip));
    }

    // Surfaceflinger
    SurfaceflingerTrackModel sfModel = new SurfaceflingerTrackModel(systemTraceData, timeline.getViewRange());
    SurfaceflingerTooltip sfTooltip = new SurfaceflingerTooltip(timeline, sfModel.getSurfaceflingerEvents());
    display.addTrackModel(
      TrackModel.newBuilder(sfModel, ProfilerTrackRendererType.SURFACEFLINGER, "SurfaceFlinger").setDefaultTooltipModel(sfTooltip));

    // VSYNC
    VsyncTrackModel vsyncModel = new VsyncTrackModel(systemTraceData, timeline.getViewRange());
    VsyncTooltip vsyncTooltip = new VsyncTooltip(timeline, vsyncModel.getVsyncCounterSeries());
    display.addTrackModel(TrackModel.newBuilder(vsyncModel, ProfilerTrackRendererType.VSYNC, "VSYNC").setDefaultTooltipModel(vsyncTooltip));

    // Buffer Queue
    BufferQueueTrackModel bufferQueueTrackModel = new BufferQueueTrackModel(systemTraceData, timeline.getViewRange());
    BufferQueueTooltip bufferQueueTooltip = new BufferQueueTooltip(timeline, bufferQueueTrackModel.getBufferQueueSeries());
    display.addTrackModel(TrackModel.newBuilder(bufferQueueTrackModel, ProfilerTrackRendererType.BUFFER_QUEUE, "BufferQueue")
                            .setDefaultTooltipModel(bufferQueueTooltip));
    return display;
  }

  private static TrackGroupModel createFrameLifecycleTrackGroup(@NotNull TraceProcessor.AndroidFrameEventsResult.Layer layer,
                                                                @NotNull Timeline timeline,
                                                                boolean collapseInitially,
                                                                @NotNull CpuSystemTraceData data,
                                                                @NotNull MultiSelectionModel<CpuAnalyzable<?>> multiSelectionModel) {
    // Layer name takes the form of "app_name/surface_name", shorten it by omitting the app_name.
    String title = "Frame Lifecycle (" + layer.getLayerName().substring(layer.getLayerName().lastIndexOf('/') + 1) + ")";
    TrackGroupModel frameLayer = TrackGroupModel.newBuilder()
      .setTitle(title)
      .setTitleHelpText("<p><b>Application</b>: Duration of the app drawing the frame. " +
                        "<p><b>Wait for GPU</b>: Time it takes the GPU to complete the frame.</p>" +
                        "<p><b>Composition</b>: Composition time by SurfaceFlinger (not controlled by your app process).</p>" +
                        "<p><b>Frames on display</b>: Duration of the frame presented on screen display.</p>")
      .setTitleHelpLink("Learn more", "https://d.android.com/r/studio-ui/profiler/frame-lifecycle")
      .setCollapsedInitially(collapseInitially)
      .build();

    layer.getPhaseList().stream()
      .map(phase -> new AndroidFrameEventTrackModel(phase, timeline.getViewRange(), data.getVsyncCounterValues(),
                                                    multiSelectionModel))
      .sorted(Comparator.comparingInt(trackModel -> trackModel.getAndroidFramePhase().ordinal()))
      .forEach(
        trackModel -> {
          AndroidFrameEventTooltip tooltip = new AndroidFrameEventTooltip(timeline, trackModel);
          frameLayer.addTrackModel(TrackModel.newBuilder(trackModel, ProfilerTrackRendererType.ANDROID_FRAME_EVENT,
                                                         trackModel.getAndroidFramePhase().getDisplayName())
                                     .setDefaultTooltipModel(tooltip));
        }
      );
    return frameLayer;
  }

  private static TrackGroupModel createJankDetectionTrackGroup(@NotNull SystemTraceCpuCapture capture,
                                                               @NotNull CpuSystemTraceData systemTraceData,
                                                               @NotNull Timeline timeline,
                                                               @NotNull MultiSelectionModel<CpuAnalyzable<?>> multiSelectionModel) {
    String toggleAllFrames = "All Frames";
    String toggleLifeCycle = "Lifecycle";
    TrackGroupModel.Builder displayBuilder = TrackGroupModel.newBuilder()
      .setTitle("Display")
      .setTitleHelpText("<p><b>Janky frames</b>: Frames that lead to unstable frame rate or increased UI latency. " +
                        "Frames in red are janky due to app process running longer than expected. " +
                        "Frames in yellow are janky due to stuffed buffer queue.</p>" +
                        "<p><b>All frames</b>: All frames being rendered by your app. " +
                        "Frames in green are on-time.</p>" +
                        "<p><b>Application</b>: Duration of the app drawing the frame. " +
                        "Look for threads and events associated with the frame to fix jank.</p>" +
                        "<p><b>Wait for GPU</b>: Time it takes the GPU to complete the frame.</p>" +
                        "<p><b>Composition</b>: Composition time by SurfaceFlinger (not controlled by your app process).</p>" +
                        "<p><b>Frames on display</b>: Duration of the frame presented on screen display.</p>")
      .setTitleHelpLink("Learn more", "https://source.android.com/devices/graphics")
      .addDisplayToggle(toggleAllFrames, false);
    if (!systemTraceData.getAndroidFrameLayers().isEmpty()) {
      displayBuilder.addDisplayToggle(toggleLifeCycle, false);
    }
    TrackGroupModel display = displayBuilder.build();

    // Jank
    List<AndroidFrameTimelineEvent> events = systemTraceData.getAndroidFrameTimelineEvents();
    List<AndroidFrameTimelineEvent> jankEvents = events.stream()
      .filter(AndroidFrameTimelineEvent::isJank)
      .collect(Collectors.toList());
    List<SeriesData<Long>> vsyncs = systemTraceData.getVsyncCounterValues();
    AndroidFrameTimelineModel jankyFrameModel =
      new AndroidFrameTimelineModel(jankEvents, vsyncs, timeline.getViewRange(), multiSelectionModel, capture);
    AndroidFrameTimelineModel allFramesModel =
      new AndroidFrameTimelineModel(events, vsyncs, timeline.getViewRange(), multiSelectionModel, capture);
    display.addTrackModel(TrackModel.newBuilder(jankyFrameModel, ProfilerTrackRendererType.ANDROID_FRAME_TIMELINE_EVENT, "Janky frames")
                            .setDefaultTooltipModel(new AndroidFrameTimelineTooltip(timeline, jankyFrameModel)),
                          toggles -> !toggles.contains(toggleAllFrames));
    display.addTrackModel(TrackModel.newBuilder(allFramesModel, ProfilerTrackRendererType.ANDROID_FRAME_TIMELINE_EVENT, "All frames")
                            .setDefaultTooltipModel(new AndroidFrameTimelineTooltip(timeline, allFramesModel)),
                          toggles -> toggles.contains(toggleAllFrames));

    // Track displaying lifecycle data corresponding to frames in above track
    Map<Long, AndroidFrameTimelineEvent> timelineEventIndex =
      CollectionsKt.associateBy(systemTraceData.getAndroidFrameTimelineEvents(), AndroidFrameTimelineEvent::getSurfaceFrameToken);
    BiConsumer<Function1<TraceProcessor.AndroidFrameEventsResult.FrameEvent, Boolean>,
                         Function1<Set<String>, Boolean>> adder = (frameFilter, displayingCondition) ->
      TraceProcessorModelKt.groupedByPhase(systemTraceData.getAndroidFrameLayers()).stream()
        .map(phase -> new AndroidFrameEventTrackModel(phase, timeline.getViewRange(), systemTraceData.getVsyncCounterValues(),
                                                      multiSelectionModel, frameFilter, timelineEventIndex))
        .sorted(Comparator.comparingInt(model -> model.getAndroidFramePhase().ordinal()))
        .forEach(model -> {
          AndroidFrameEventTooltip tooltip = new AndroidFrameEventTooltip(timeline, model);
          display.addTrackModel(TrackModel.newBuilder(model, ProfilerTrackRendererType.ANDROID_FRAME_EVENT,
                                                      model.getAndroidFramePhase().getDisplayName())
                                  .setDefaultTooltipModel(tooltip),
                                displayingCondition);
        });
    // lifecycle data for all frames
    adder.accept(frame -> true,
                 toggles -> toggles.contains(toggleLifeCycle) && toggles.contains(toggleAllFrames));
    // lifecycle data for janky frames only
    adder.accept(frame -> {
                   AndroidFrameTimelineEvent event = timelineEventIndex.get((long)frame.getFrameNumber());
                   return event != null && event.isJank();
                 },
                 toggles -> toggles.contains(toggleLifeCycle) && !toggles.contains(toggleAllFrames));
    // Deadline text
    display.addTrackModel(TrackModel.newBuilder(new DeadlineTextModel(multiSelectionModel,
                                                                      systemTraceData.getVsyncCounterValues(),
                                                                      timeline.getViewRange()),
                                                ProfilerTrackRendererType.ANDROID_FRAME_DEADLINE_TEXT, "")
                            .setDragEnabled(false));
    return display;
  }

  private static TrackGroupModel createThreadsTrackGroup(@NotNull CpuCapture capture,
                                                         @NotNull Timeline timeline,
                                                         @NotNull MultiSelectionModel<CpuAnalyzable<?>> multiSelectionModel,
                                                         @NotNull FeatureTracker featureTracker) {
    // Collapse threads for ART and SimplePerf traces.
    boolean collapseThreads = !(capture instanceof SystemTraceCpuCapture);
    List<CpuThreadInfo> threadInfos =
      capture.getThreads().stream().sorted(CpuThreadComparator.withCaptureInfo(capture)).collect(Collectors.toList());
    String threadsTitle = String.format(Locale.getDefault(), "Threads (%d)", threadInfos.size());
    BoxSelectionModel boxSelectionModel = new BoxSelectionModel(timeline.getSelectionRange(), timeline.getViewRange());
    boxSelectionModel.addBoxSelectionListener(featureTracker::trackSelectBox);
    TrackGroupModel threads = TrackGroupModel.newBuilder()
      .setTitle(threadsTitle)
      .setTitleHelpText("This section contains thread info. Double-click on the thread name to expand/collapse. " +
                        "Shift+click to select multiple threads.")
      .setSelector(TrackGroupModel.makeBatchSelector(CpuThreadTrackModel.class.getName()))
      // For box selection
      .setBoxSelectionModel(boxSelectionModel)
      .build();
    for (CpuThreadInfo threadInfo : threadInfos) {
      String title = threadInfo.getName();
      // Since thread tracks display multiple elements with different tooltip we don't set a default tooltip model here but defer to the
      // track renderer to switch between its various tooltip models.
      threads.addTrackModel(
        TrackModel.newBuilder(
          new CpuThreadTrackModel(capture, threadInfo, timeline, multiSelectionModel), ProfilerTrackRendererType.CPU_THREAD, title)
          .setCollapsible(true)
          .setCollapsed(collapseThreads));
    }
    return threads;
  }

  private static TrackGroupModel createCpuCoresTrackGroup(int mainThreadId,
                                                          @NotNull CpuSystemTraceData systemTraceData,
                                                          @NotNull Timeline timeline) {
    int cpuCount = systemTraceData.getCpuCount();
    String coresTitle = String.format(Locale.getDefault(), "CPU cores (%d)", cpuCount);
    TrackGroupModel cores = TrackGroupModel.newBuilder().setTitle(coresTitle).setCollapsedInitially(true).build();
    for (int cpuId = 0; cpuId < cpuCount; ++cpuId) {
      // CPU Core scheduling.
      final int coreId = cpuId;
      LazyDataSeries<CpuThreadSliceInfo> coreSchedSeries = new LazyDataSeries<>(() -> systemTraceData.getCpuThreadSliceInfoStates(coreId));
      CpuKernelTooltip kernelTooltip = new CpuKernelTooltip(timeline, mainThreadId);
      kernelTooltip.setCpuSeries(cpuId, coreSchedSeries);
      cores.addTrackModel(TrackModel.newBuilder(new CpuCoreTrackModel(coreSchedSeries, timeline.getViewRange(), mainThreadId),
                                                ProfilerTrackRendererType.CPU_CORE, "CPU " + cpuId).setDefaultTooltipModel(kernelTooltip));

      // CPU Core frequency.
      String cpuFrequencyTitle = "CPU " + cpuId + " Frequency";
      List<SeriesData<Long>> cpuFreqCounters = systemTraceData.getCpuCounters().get(cpuId).getOrDefault("cpufreq", Collections.emptyList());
      CpuFrequencyTrackModel cpuFreqTrackModel = new CpuFrequencyTrackModel(cpuFreqCounters, timeline.getViewRange());
      CpuFrequencyTooltip cpuFreqTooltip = new CpuFrequencyTooltip(timeline, cpuId, cpuFreqTrackModel.getCpuFrequencySeries());
      cores.addTrackModel(TrackModel.newBuilder(cpuFreqTrackModel, ProfilerTrackRendererType.CPU_FREQUENCY, cpuFrequencyTitle)
                            .setDefaultTooltipModel(cpuFreqTooltip));
    }
    return cores;
  }

  private static TrackGroupModel createRssMemoryTrackGroup(@NotNull CpuSystemTraceData systemTraceData, @NotNull Timeline timeline) {
    TrackGroupModel memory = TrackGroupModel.newBuilder()
      .setTitle("Process Memory (RSS)")
      .setTitleHelpText("This section shows the memory footprint of the app." +
                        "<p><b>Resident Set Size (RSS)</b> is the portion of memory the app occupies in RAM, " +
                        "combining both shared and non-shared pages.</p>")
      .setTitleHelpLink("Learn more", "https://d.android.com/r/studio-ui/profiler/rss-memory")
      .setCollapsedInitially(true)
      .build();
    RssMemoryTrackModel.Companion.getIncludedCountersNameMap().forEach(
      (counterName, displayName) -> {
        if (systemTraceData.getMemoryCounters().containsKey(counterName)) {
          RssMemoryTrackModel trackModel =
            new RssMemoryTrackModel(systemTraceData.getMemoryCounters().get(counterName), timeline.getViewRange());
          RssMemoryTooltip tooltip = new RssMemoryTooltip(timeline, counterName, trackModel.getMemoryCounterSeries());
          memory.addTrackModel(
            TrackModel.newBuilder(trackModel, ProfilerTrackRendererType.RSS_MEMORY, displayName).setDefaultTooltipModel(tooltip));
        }
      }
    );
    return memory;
  }
}