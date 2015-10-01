/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.schema.Message;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public abstract class ServiceClient {
  //<<<Start:Java.ClientBody:1>>>
  public abstract ListenableFuture<Path> follow(Path p);
  public abstract ListenableFuture<Object> get(Path p);
  public abstract ListenableFuture<CapturePath[]> getCaptures();
  public abstract ListenableFuture<DevicePath[]> getDevices();
  public abstract ListenableFuture<ImageInfoPath> getFramebufferColor(DevicePath device, AtomPath after, RenderSettings settings);
  public abstract ListenableFuture<ImageInfoPath> getFramebufferDepth(DevicePath device, AtomPath after);
  public abstract ListenableFuture<Message> getSchema();
  public abstract ListenableFuture<TimingInfoPath> getTimingInfo(DevicePath device, CapturePath capture, TimingFlags flags);
  public abstract ListenableFuture<CapturePath> importCapture(String name, byte[] Data);
  public abstract ListenableFuture<CapturePath> loadCapture(String path);
  public abstract ListenableFuture<Path> set(Path p, Object v);
  //<<<End:Java.ClientBody:1>>>

  public ListenableFuture<Device> get(DevicePath p) {
    return getAndCast(p);
  }

  public ListenableFuture<Capture> get(CapturePath p) {
    return getAndCast(p);
  }

  public ListenableFuture<AtomList> get(AtomsPath p) {
    return getAndCast(p);
  }

  public ListenableFuture<AtomGroup> get(HierarchyPath p) {
    return getAndCast(p);
  }

  public ListenableFuture<ImageInfo> get(ImageInfoPath p) {
    return getAndCast(p);
  }

  public ListenableFuture<ImageInfo> get(ThumbnailPath p) {
    return getAndCast(p);
  }

  public ListenableFuture<byte[]> get(BlobPath p) {
    return getAndCast(p);
  }

  public ListenableFuture<Resources> get(ResourcesPath p) {
    return getAndCast(p);
  }

  public ListenableFuture<MemoryInfo> get(MemoryRangePath p) {
    return getAndCast(p);
  }

  private <T> ListenableFuture<T> getAndCast(Path p) {
    return Futures.transform(get(p), new Function<Object, T>() {
      @Override
      public T apply(Object result) {
        return (T)result;
      }
    });
  }
}
