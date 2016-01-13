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

import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.rpclib.any.Box;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.Info;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.rpclib.schema.Message;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.StringTable;
import com.android.tools.idea.editors.gfxtrace.service.path.TimingInfoPath;
import com.android.tools.rpclib.rpccore.Broadcaster;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListenableFuture;

public final class ServiceClientRPC extends ServiceClient {
  private final Broadcaster myBroadcaster;
  private final ListeningExecutorService myExecutorService;

  public ServiceClientRPC(ListeningExecutorService executorService, InputStream in, OutputStream out, int mtu, int version) {
    myExecutorService = executorService;
    myBroadcaster = new Broadcaster(in, out, mtu, myExecutorService, version);
  }
  @Override
  public ListenableFuture<Path> follow(Path p) {
    return myExecutorService.submit(new FollowCallable(p));
  }
  @Override
  public ListenableFuture<Object> get(Path p) {
    return myExecutorService.submit(new GetCallable(p));
  }
  @Override
  public ListenableFuture<Info[]> getAvailableStringTables() {
    return myExecutorService.submit(new GetAvailableStringTablesCallable());
  }
  @Override
  public ListenableFuture<CapturePath[]> getCaptures() {
    return myExecutorService.submit(new GetCapturesCallable());
  }
  @Override
  public ListenableFuture<DevicePath[]> getDevices() {
    return myExecutorService.submit(new GetDevicesCallable());
  }
  @Override
  public ListenableFuture<String[]> getFeatures() {
    return myExecutorService.submit(new GetFeaturesCallable());
  }
  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferColor(DevicePath device, AtomPath after, RenderSettings settings) {
    return myExecutorService.submit(new GetFramebufferColorCallable(device, after, settings));
  }
  @Override
  public ListenableFuture<ImageInfoPath> getFramebufferDepth(DevicePath device, AtomPath after) {
    return myExecutorService.submit(new GetFramebufferDepthCallable(device, after));
  }
  @Override
  public ListenableFuture<Message> getSchema() {
    return myExecutorService.submit(new GetSchemaCallable());
  }
  @Override
  public ListenableFuture<StringTable> getStringTable(Info info) {
    return myExecutorService.submit(new GetStringTableCallable(info));
  }
  @Override
  public ListenableFuture<TimingInfoPath> getTimingInfo(DevicePath device, CapturePath capture, TimingFlags flags) {
    return myExecutorService.submit(new GetTimingInfoCallable(device, capture, flags));
  }
  @Override
  public ListenableFuture<CapturePath> importCapture(String name, byte[] Data) {
    return myExecutorService.submit(new ImportCaptureCallable(name, Data));
  }
  @Override
  public ListenableFuture<CapturePath> loadCapture(String path) {
    return myExecutorService.submit(new LoadCaptureCallable(path));
  }
  @Override
  public ListenableFuture<Path> set(Path p, Object v) {
    return myExecutorService.submit(new SetCallable(p, v));
  }

  private class FollowCallable implements Callable<Path> {
    private final CallFollow myCall;
    private final Exception myStack = new StackException();

    private FollowCallable(Path p) {
      myCall = new CallFollow();
      myCall.setP(p);
    }
    @Override
    public Path call() throws Exception {
      try {
        ResultFollow result = (ResultFollow)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetCallable implements Callable<Object> {
    private final CallGet myCall;
    private final Exception myStack = new StackException();

    private GetCallable(Path p) {
      myCall = new CallGet();
      myCall.setP(p);
    }
    @Override
    public Object call() throws Exception {
      try {
        ResultGet result = (ResultGet)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetAvailableStringTablesCallable implements Callable<Info[]> {
    private final CallGetAvailableStringTables myCall;
    private final Exception myStack = new StackException();

    private GetAvailableStringTablesCallable() {
      myCall = new CallGetAvailableStringTables();
    }
    @Override
    public Info[] call() throws Exception {
      try {
        ResultGetAvailableStringTables result = (ResultGetAvailableStringTables)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetCapturesCallable implements Callable<CapturePath[]> {
    private final CallGetCaptures myCall;
    private final Exception myStack = new StackException();

    private GetCapturesCallable() {
      myCall = new CallGetCaptures();
    }
    @Override
    public CapturePath[] call() throws Exception {
      try {
        ResultGetCaptures result = (ResultGetCaptures)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetDevicesCallable implements Callable<DevicePath[]> {
    private final CallGetDevices myCall;
    private final Exception myStack = new StackException();

    private GetDevicesCallable() {
      myCall = new CallGetDevices();
    }
    @Override
    public DevicePath[] call() throws Exception {
      try {
        ResultGetDevices result = (ResultGetDevices)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetFeaturesCallable implements Callable<String[]> {
    private final CallGetFeatures myCall;
    private final Exception myStack = new StackException();

    private GetFeaturesCallable() {
      myCall = new CallGetFeatures();
    }
    @Override
    public String[] call() throws Exception {
      try {
        ResultGetFeatures result = (ResultGetFeatures)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetFramebufferColorCallable implements Callable<ImageInfoPath> {
    private final CallGetFramebufferColor myCall;
    private final Exception myStack = new StackException();

    private GetFramebufferColorCallable(DevicePath device, AtomPath after, RenderSettings settings) {
      myCall = new CallGetFramebufferColor();
      myCall.setDevice(device);
      myCall.setAfter(after);
      myCall.setSettings(settings);
    }
    @Override
    public ImageInfoPath call() throws Exception {
      try {
        ResultGetFramebufferColor result = (ResultGetFramebufferColor)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetFramebufferDepthCallable implements Callable<ImageInfoPath> {
    private final CallGetFramebufferDepth myCall;
    private final Exception myStack = new StackException();

    private GetFramebufferDepthCallable(DevicePath device, AtomPath after) {
      myCall = new CallGetFramebufferDepth();
      myCall.setDevice(device);
      myCall.setAfter(after);
    }
    @Override
    public ImageInfoPath call() throws Exception {
      try {
        ResultGetFramebufferDepth result = (ResultGetFramebufferDepth)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetSchemaCallable implements Callable<Message> {
    private final CallGetSchema myCall;
    private final Exception myStack = new StackException();

    private GetSchemaCallable() {
      myCall = new CallGetSchema();
    }
    @Override
    public Message call() throws Exception {
      try {
        ResultGetSchema result = (ResultGetSchema)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetStringTableCallable implements Callable<StringTable> {
    private final CallGetStringTable myCall;
    private final Exception myStack = new StackException();

    private GetStringTableCallable(Info info) {
      myCall = new CallGetStringTable();
      myCall.setInfo(info);
    }
    @Override
    public StringTable call() throws Exception {
      try {
        ResultGetStringTable result = (ResultGetStringTable)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class GetTimingInfoCallable implements Callable<TimingInfoPath> {
    private final CallGetTimingInfo myCall;
    private final Exception myStack = new StackException();

    private GetTimingInfoCallable(DevicePath device, CapturePath capture, TimingFlags flags) {
      myCall = new CallGetTimingInfo();
      myCall.setDevice(device);
      myCall.setCapture(capture);
      myCall.setFlags(flags);
    }
    @Override
    public TimingInfoPath call() throws Exception {
      try {
        ResultGetTimingInfo result = (ResultGetTimingInfo)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class ImportCaptureCallable implements Callable<CapturePath> {
    private final CallImportCapture myCall;
    private final Exception myStack = new StackException();

    private ImportCaptureCallable(String name, byte[] Data) {
      myCall = new CallImportCapture();
      myCall.setName(name);
      myCall.setData(Data);
    }
    @Override
    public CapturePath call() throws Exception {
      try {
        ResultImportCapture result = (ResultImportCapture)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class LoadCaptureCallable implements Callable<CapturePath> {
    private final CallLoadCapture myCall;
    private final Exception myStack = new StackException();

    private LoadCaptureCallable(String path) {
      myCall = new CallLoadCapture();
      myCall.setPath(path);
    }
    @Override
    public CapturePath call() throws Exception {
      try {
        ResultLoadCapture result = (ResultLoadCapture)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private class SetCallable implements Callable<Path> {
    private final CallSet myCall;
    private final Exception myStack = new StackException();

    private SetCallable(Path p, Object v) {
      myCall = new CallSet();
      myCall.setP(p);
      myCall.setV(v);
    }
    @Override
    public Path call() throws Exception {
      try {
        ResultSet result = (ResultSet)myBroadcaster.Send(myCall);
        return result.getValue();
      } catch (Exception e) {
        e.initCause(myStack);
        throw e;
      }
    }
  }
  private static class StackException extends Exception {
    @Override
    public String toString() {
      return String.valueOf(getCause());
    }
  }
}
