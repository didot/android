/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class CaptureObjectLoaderTest {

  private CaptureObjectLoader myLoader = null;

  @Before
  public void setup() {
    myLoader = new CaptureObjectLoader();
  }

  @Test(expected = AssertionError.class)
  public void loadCaptureBeforeStart() throws Exception {
    TestCaptureObject capture = new TestCaptureObject(new CountDownLatch(1), true);
    myLoader.loadCapture(capture);
  }

  @Test
  public void loadCaptureSuccess() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, true);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture);

    future.addListener(() -> {
      try {
        CaptureObject loadedCapture = future.get();
        assertEquals(capture, loadedCapture);
      }
      catch (InterruptedException exception) {
        assert false;
      }
      catch (ExecutionException exception) {
        assert false;
      }
      catch (CancellationException ignored) {
        assert false;
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    loadLatch.countDown();
    doneLatch.await();
  }

  @Test
  public void loadCaptureFailure() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, false);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture);

    future.addListener(() -> {
      try {
        future.get();
        assert false;
      }
      catch (InterruptedException exception) {
        assert false;
      }
      catch (ExecutionException exception) {
        // No-op - expected path.
      }
      catch (CancellationException ignored) {
        assert false;
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    loadLatch.countDown();
    doneLatch.await();
  }

  @Test
  public void loadCaptureCancel() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, true);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture);

    future.addListener(() -> {
      try {
        future.get();
        assert false;
      }
      catch (InterruptedException exception) {
        assert false;
      }
      catch (ExecutionException exception) {
        assert false;
      }
      catch (CancellationException ignored) {
        // No-op - expected path.
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    myLoader.stop();
    doneLatch.await();
  }

  private static class TestCaptureObject implements CaptureObject {
    private final CountDownLatch myLoadLatch;
    private final boolean myLoadSuccess;

    public TestCaptureObject(@NotNull CountDownLatch loadLatch, boolean loadSuccess) {
      myLoadLatch = loadLatch;
      myLoadSuccess = loadSuccess;
    }

    @NotNull
    @Override
    public String getLabel() {
      return "";
    }

    @NotNull
    @Override
    public List<HeapObject> getHeaps() {
      return Collections.emptyList();
    }

    @Override
    public long getStartTimeNs() {
      return 0;
    }

    @Override
    public long getEndTimeNs() {
      return 0;
    }

    @Override
    public boolean load() {
      try {
        myLoadLatch.await();
      }
      catch (InterruptedException ignored) {
      }

      if (!myLoadSuccess) {
        throw new RuntimeException();
      }

      return true;
    }

    @Override
    public boolean isDoneLoading() {
      return false;
    }

    @Override
    public boolean isError() {
      return false;
    }
  }
}