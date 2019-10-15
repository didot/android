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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.DragAndDropListModel;
import com.android.tools.adtui.model.DragAndDropModelListElement;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DragAndDropListModel<CpuThreadsModel.RangedCpuThread> {

  @NotNull private final StudioProfilers myProfilers;

  @NotNull private final Common.Session mySession;

  @NotNull private final Range myRange;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull private final AspectObserver myAspectObserver;

  private final boolean myIsImportedTrace;

  @VisibleForTesting
  protected final HashMap<Integer, RangedCpuThread> myThreadIdToCpuThread;

  public CpuThreadsModel(@NotNull Range range,
                         @NotNull StudioProfilers profilers,
                         @NotNull Common.Session session,
                         boolean isImportedTrace) {
    myRange = range;
    myProfilers = profilers;
    mySession = session;
    myAspectObserver = new AspectObserver();
    myThreadIdToCpuThread = new HashMap<>();
    myIsImportedTrace = isImportedTrace;
    myRange.addDependency(myAspectObserver)
      .onChange(Range.Aspect.RANGE, myIsImportedTrace ? this::importRangeChanged : this::nonImportRangeChanged);

    // Initialize first set of elements.
    nonImportRangeChanged();
    sortElements();
  }

  /**
   * In import trace mode, we always list all the capture threads, as we don't have the concept of thread states. In regular profiling,
   * if a thread is dead, it means we won't see more state changes from it at a later point, so it's OK to remove it from the list.
   * Threads in import trace mode, for example, can have 5 seconds of activity, stay inactive for 10 more seconds and have activity
   * again for other 5 seconds. As it's common for a thread to be inactive (e.g. sleeping, waiting for I/O, stopped, etc.) during its
   * lifespan, we don't change the threads list automatically to avoid a poor user experience.
   * Note that users can still explicitly change the threads order by using the drag-and-drop functionality.
   */
  private void importRangeChanged() {
    contentsChanged();
  }

  private void nonImportRangeChanged() {
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax());
    Map<Integer, RangedCpuThread> requestedThreadsRangedCpuThreads = new HashMap<>();

    if (myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      GetEventGroupsResponse response = myProfilers.getClient().getTransportClient().getEventGroups(
        GetEventGroupsRequest.newBuilder()
          .setStreamId(mySession.getStreamId())
          .setPid(mySession.getPid())
          .setKind(Common.Event.Kind.CPU_THREAD)
          .setFromTimestamp(minNs)
          .setToTimestamp(maxNs)
          .build());

      // Merge the two lists.
      for (EventGroup eventGroup : response.getGroupsList()) {
        if (eventGroup.getEventsCount() > 0) {
          Cpu.CpuThreadData threadData = eventGroup.getEvents(0).getCpuThread();
          requestedThreadsRangedCpuThreads.put(threadData.getTid(), myThreadIdToCpuThread
            .computeIfAbsent(threadData.getTid(), id -> new RangedCpuThread(myRange, threadData.getTid(), threadData.getName())));
        }
      }
    }
    else {
      CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
        .setSession(mySession)
        .setStartTimestamp(minNs)
        .setEndTimestamp(maxNs);
      CpuServiceGrpc.CpuServiceBlockingStub client = myProfilers.getClient().getCpuClient();
      CpuProfiler.GetThreadsResponse response = client.getThreads(request.build());

      // Merge the two lists.
      for (CpuProfiler.GetThreadsResponse.Thread newThread : response.getThreadsList()) {
        RangedCpuThread cpuThread = myThreadIdToCpuThread.computeIfAbsent(newThread.getTid(),
                                                                          id -> new RangedCpuThread(myRange, newThread.getTid(),
                                                                                                    newThread.getName()));
        requestedThreadsRangedCpuThreads.put(newThread.getTid(), cpuThread);
      }
    }

    // Find elements that already exist and remove them from the incoming set.
    for (int i = 0; i < getSize(); i++) {
      RangedCpuThread element = getElementAt(i);
      // If our element exists in the incoming set we remove it from the set, because we do not need to do anything with it.
      // If the element does not exist it means we no longer need to show this thread and we remove it from our list of elements.
      if (requestedThreadsRangedCpuThreads.containsKey(element.getThreadId())) {
        requestedThreadsRangedCpuThreads.remove(element.getThreadId());
      }
      else {
        removeOrderedElement(element);
        i--;
      }
    }

    // Add threads that don't have an element already associated with them.
    for (RangedCpuThread element : requestedThreadsRangedCpuThreads.values()) {
      insertOrderedElement(element);
    }
    contentsChanged();
  }

  private void sortElements() {
    // Copy elements into an array before we clear them.
    RangedCpuThread[] elements = new RangedCpuThread[getSize()];
    for (int i = 0; i < getSize(); ++i) {
      elements[i] = get(i);
    }
    clearOrderedElements();

    // Sort by the ThreadInfo field.
    Arrays.sort(elements);

    // Even with the render thread at the top of the sorting, the pre-populated elements get priority so,
    // all of our threads will be added below our process thread in order.
    for (RangedCpuThread element : elements) {
      insertOrderedElement(element);
    }
  }

  /**
   * Build a list of {@link RangedCpuThread} based from the threads contained in a given {@link CpuCapture}.
   */
  void buildImportedTraceThreads(@NotNull CpuCapture capture) {
    capture.getThreads().stream()
      // Create the RangedCpuThread objects from the capture's threads.
      .map(thread -> new RangedCpuThread(myRange, thread.getId(), thread.getName(), capture))
      // Sort them by their natural order.
      .sorted()
      // Now insert the elements in order.
      .forEach(this::insertOrderedElement);
  }

  void updateTraceThreadsForCapture(@NotNull CpuCapture capture) {
    // In the import case we do not have a thread list so we build it.
    if (myIsImportedTrace) {
      buildImportedTraceThreads(capture);
    }
    else {
      myThreadIdToCpuThread.forEach((key, value) -> value.applyCapture(key, capture));
    }
  }

  private void contentsChanged() {
    fireContentsChanged(this, 0, size());
  }

  protected static CpuProfilerStage.ThreadState getState(Cpu.CpuThreadData.State state, boolean captured) {
    switch (state) {
      case RUNNING:
        return captured ? CpuProfilerStage.ThreadState.RUNNING_CAPTURED : CpuProfilerStage.ThreadState.RUNNING;
      case DEAD:
        return captured ? CpuProfilerStage.ThreadState.DEAD_CAPTURED : CpuProfilerStage.ThreadState.DEAD;
      case SLEEPING:
        return captured ? CpuProfilerStage.ThreadState.SLEEPING_CAPTURED : CpuProfilerStage.ThreadState.SLEEPING;
      case WAITING:
        return captured ? CpuProfilerStage.ThreadState.WAITING_CAPTURED : CpuProfilerStage.ThreadState.WAITING;
      default:
        // TODO: Use colors that have been agreed in design review.
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }

  public class RangedCpuThread implements DragAndDropModelListElement, Comparable<RangedCpuThread> {
    @NotNull private final CpuThreadInfo myThreadInfo;
    private final Range myRange;
    private final StateChartModel<CpuProfilerStage.ThreadState> myModel;
    /**
     * If the thread is imported from a trace file (excluding an atrace one), we use a {@link ImportedTraceThreadDataSeries} to represent
     * its data. Otherwise, we use a {@link MergeCaptureDataSeries} that will combine the sampled {@link DataSeries} pulled from perfd, and
     * {@link AtraceCpuCapture}, populated when an atrace capture is parsed.
     */
    private DataSeries<CpuProfilerStage.ThreadState> mySeries;

    public RangedCpuThread(Range range, int threadId, String name) {
      this(range, threadId, name, null);
    }

    /**
     * When a not-null {@link CpuCapture} is passed, it means the thread is imported from a trace file. If the {@link CpuCapture} passed is
     * null, it means that we are in a profiling session. Default behavior is to obtain the {@link CpuProfilerStage.ThreadState} data from
     * perfd. When a capture is selected applyCapture is called and on atrace captures a {@link MergeCaptureDataSeries} is used to collect
     * data from perfd as well as the {@link AtraceCpuCapture}.
     */
    public RangedCpuThread(Range range, int threadId, String name, @Nullable CpuCapture capture) {
      myRange = range;
      myModel = new StateChartModel<>();
      boolean isMainThread = applyCapture(threadId, capture);
      myThreadInfo = new CpuThreadInfo(threadId, name, isMainThread);
    }

    /**
     * @return true if this thread is the main thread.
     */
    private boolean applyCapture(int threadId, @Nullable CpuCapture capture) {
      boolean isMainThread;
      if (myIsImportedTrace) {
        // For imported traces, the main thread ID can be obtained from the capture
        assert capture != null;
        isMainThread = threadId == capture.getMainThreadId();
        if (capture.getType() == Cpu.CpuTraceType.ATRACE) {
          mySeries =
            new AtraceDataSeries<>((AtraceCpuCapture)capture, (atraceCapture) -> atraceCapture.getThreadStatesForThread(threadId));
        }
        else {
          // If thread is created from an imported trace (excluding atrace), we should use an ImportedTraceThreadDataSeries
          mySeries = new ImportedTraceThreadDataSeries(capture, threadId);
        }
      }
      else {
        mySeries = myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled() ?
                   new CpuThreadStateDataSeries(myProfilers.getClient().getTransportClient(),
                                                mySession.getStreamId(),
                                                mySession.getPid(),
                                                threadId,
                                                capture) :
                   new LegacyCpuThreadStateDataSeries(myProfilers.getClient().getCpuClient(), mySession, threadId, capture);
        // If we have an Atrace capture selected then we need to create a MergeCaptureDataSeries
        if (capture != null && capture.getType() == Cpu.CpuTraceType.ATRACE) {
          AtraceCpuCapture atraceCpuCapture = (AtraceCpuCapture)capture;
          AtraceDataSeries<CpuProfilerStage.ThreadState> atraceDataSeries =
            new AtraceDataSeries<>(atraceCpuCapture, (atraceCapture) -> atraceCapture.getThreadStatesForThread(threadId));
          mySeries = new MergeCaptureDataSeries<>(capture, mySeries, atraceDataSeries);
        }
        // For non-imported traces, the main thread ID is equal to the process ID of the current session
        isMainThread = threadId == mySession.getPid();
      }
      // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
      myModel.addSeries(new RangedSeries<>(myRange, mySeries));
      return isMainThread;
    }

    public int getThreadId() {
      return myThreadInfo.getId();
    }

    @NotNull
    public String getName() {
      return myThreadInfo.getName();
    }

    public StateChartModel<CpuProfilerStage.ThreadState> getModel() {
      return myModel;
    }

    public DataSeries<CpuProfilerStage.ThreadState> getStateSeries() {
      return mySeries;
    }

    /**
     * @return Thread Id used to uniquely identify this object in our {@link DragAndDropListModel}
     */
    @Override
    public int getId() {
      return getThreadId();
    }

    /**
     * See {@link CpuThreadInfo} for sort order.
     */
    @Override
    public int compareTo(@NotNull RangedCpuThread o) {
      return myThreadInfo.compareTo(o.myThreadInfo);
    }
  }
}
