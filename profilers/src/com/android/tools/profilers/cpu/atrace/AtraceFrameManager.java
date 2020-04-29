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
package com.android.tools.profilers.cpu.atrace;

import static com.android.tools.profilers.cpu.CpuThreadInfo.RENDER_THREAD_NAME;

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.cpu.CpuFramesModel;
import com.android.tools.profilers.systemtrace.ProcessModel;
import com.android.tools.profilers.systemtrace.ThreadModel;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * This class builds {@link AtraceFrame}s for each {@code AtraceFrame.FrameThread} types.
 */
public class AtraceFrameManager {

  private final List<AtraceFrame> myMainThreadFrames;
  private final List<AtraceFrame> myRenderThreadFrames;

  private final int myRenderThreadId;

  /**
   * Constructs a default manager, the constructor finds the main thread and will assert if one is not found.
   *
   * @param process Process used to find the main and render threads.
   */
  public AtraceFrameManager(@NotNull ProcessModel process) {
    myMainThreadFrames = buildFramesList(AtraceFrame.FrameThread.MAIN, process, process.getId());
    myRenderThreadId = findRenderThreadId(process);
    myRenderThreadFrames = buildFramesList(AtraceFrame.FrameThread.RENDER, process, myRenderThreadId);
    findAssociatedFrames();
  }

  /**
   * Finds main thread and render thread frames that are associated with each other and adds a link to each one in the other.
   */
  private void findAssociatedFrames() {
    int mainFramesIterator = 0, renderFramesIterator = 0;

    while (mainFramesIterator < myMainThreadFrames.size() && renderFramesIterator < myRenderThreadFrames.size()) {
      AtraceFrame mainThreadFrame = myMainThreadFrames.get(mainFramesIterator);
      AtraceFrame renderThreadFrame = myRenderThreadFrames.get(renderFramesIterator);
      if (renderThreadFrame == AtraceFrame.EMPTY || renderThreadFrame.getEndUs() < mainThreadFrame.getEndUs()) {
        renderFramesIterator++;
      }
      else if (mainThreadFrame == AtraceFrame.EMPTY ||
               renderThreadFrame.getStartUs() > mainThreadFrame.getEndUs() ||
               renderThreadFrame.getStartUs() < mainThreadFrame.getStartUs()) {
        mainFramesIterator++;
      }
      else {
        mainThreadFrame.setAssociatedFrame(renderThreadFrame);
        renderThreadFrame.setAssociatedFrame(mainThreadFrame);
        mainFramesIterator++;
        renderFramesIterator++;
      }
    }
  }

  @NotNull
  private static List<AtraceFrame> buildFramesList(AtraceFrame.FrameThread frameThread,
                                            ProcessModel processModel,
                                            int threadId) {
    List<AtraceFrame> frames = new ArrayList<>();
    Optional<ThreadModel> activeThread = processModel.getThreads().stream().filter((thread) -> thread.getId() == threadId).findFirst();
    if (!activeThread.isPresent()) {
      return frames;
    }
    new SliceStream(activeThread.get().getTraceEvents())
      .matchPattern(Pattern.compile(frameThread.getIdentifierRegEx()))
      .enumerate((sliceGroup) -> {
        AtraceFrame frame = new AtraceFrame(sliceGroup, CpuFramesModel.SLOW_FRAME_RATE_US, frameThread);
        frames.add(frame);
        return SliceStream.EnumerationResult.SKIP_CHILDREN;
      });
    return frames;
  }

  /**
   * Returns a list of frames for a frame type.
   */
  @VisibleForTesting
  List<AtraceFrame> getFramesList(@NotNull AtraceFrame.FrameThread thread) {
    switch (thread) {
      case MAIN:
        return myMainThreadFrames;
      case RENDER:
        return myRenderThreadFrames;
      default:
        return new ArrayList<>();
    }
  }

  /**
   * Returns the render thread id associated with this FrameManager.
   * If a render thread was not found, the value will be {@code Integer.MAX_VALUE}.
   */
  public int getRenderThreadId() {
    return myRenderThreadId;
  }

  /**
   * Returns a series of frames where gaps between frames are filled with empty frames. This allows the caller to determine the
   * frame length by looking at the delta between a valid frames series and the empty frame series that follows it. The delta between
   * an empty frame series and the following frame is idle time between frames.
   *
   * This only supports {@code AtraceFrame.FrameThread.MAIN} and {@code AtraceFrame.FrameThread.RENDER}. Will return an empty list for
   * {@code AtraceFrame.FrameThread.OTHER}.
   */
  @NotNull
  public List<SeriesData<AtraceFrame>> getFrames(@NotNull AtraceFrame.FrameThread thread) {
    List<SeriesData<AtraceFrame>> framesSeries = new ArrayList<>();
    List<AtraceFrame> framesList = getFramesList(thread);
    // Look at each frame converting them to series data.
    // The last frame is handled outside the for loop as we need to add an entry for the frame as well as an entry for the frame ending.
    // Single frames are handled in the last frame case.
    for (int i = 1; i < framesList.size(); i++) {
      AtraceFrame current = framesList.get(i);
      AtraceFrame past = framesList.get(i - 1);
      framesSeries.add(new SeriesData<>(past.getStartUs(), past));

      // Need to get the time delta between two frames.
      // If we have a gap then we add an empty frame to signify to the UI that nothing should be rendered.
      if (past.getEndUs() < current.getStartUs()) {
        framesSeries.add(new SeriesData<>(past.getEndUs(), AtraceFrame.EMPTY));
      }
    }

    // Always add the last frame, and a null frame following to properly setup the series for the UI.
    if (!framesList.isEmpty()) {
      AtraceFrame lastFrame = framesList.get(framesList.size() - 1);
      framesSeries.add(new SeriesData<>(lastFrame.getStartUs(), lastFrame));
      framesSeries.add(new SeriesData<>(lastFrame.getEndUs(), AtraceFrame.EMPTY));
    }
    return framesSeries;
  }

  /**
   * Helper function used to find the main and render threads.
   */
  private static int findRenderThreadId(@NotNull ProcessModel process) {
    Optional<ThreadModel> renderThread =
      process.getThreads().stream().filter((thread) -> thread.getName().equalsIgnoreCase(RENDER_THREAD_NAME)).findFirst();

    // The max process id values we can have is max short, and some invalid process names can be -1
    // so to avoid confusion we use int max.
    return renderThread.map(ThreadModel::getId).orElse(Integer.MAX_VALUE);
  }
}
