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
package com.android.tools.profilers;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

public class StudioProfilersViewTest {

  private final FakeProfilerService myService = new FakeProfilerService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("StudioProfilerTestChannel", myService);
  private StudioProfilers myProfilers;
  private FakeTimer myTimer;
  private StudioProfilersView myView;


  @Before
  public void setUp() throws Exception {
    IdeProfilerServices ide = mock(IdeProfilerServices.class);
    myTimer = new FakeTimer();
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), ide, myTimer);
    // Make sure a process is selected
    myTimer.tick(10);
    myView = new StudioProfilersView(myProfilers, new IdeProfilerComponentsStub());
    myView.bind(FakeStage.class, FakeView::new);
  }

  @Test
  public void testSameStageTransition() {
    FakeStage stage = new FakeStage(myProfilers);
    myProfilers.setStage(stage);
    StageView view = myView.getStageView();

    myProfilers.setStage(stage);
    assertEquals(view, myView.getStageView());
  }

  @Test
  public void testDeviceRendering() throws IOException {
    StudioProfilersView.DeviceComboBoxRenderer renderer = new StudioProfilersView.DeviceComboBoxRenderer();
    JList<Profiler.Device> list = new JList<>();
    // Null device
    Profiler.Device device = null;
    Component component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertEquals(renderer.getEmptyText(), component.toString());

    // Standard case
    device = Profiler.Device.newBuilder()
      .setModel("Model")
      .setSerial("1234")
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertEquals("Model (1234)", component.toString());

    // Suffix not serial
    device = Profiler.Device.newBuilder()
      .setModel("Model-9999")
      .setSerial("1234")
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertEquals("Model-9999 (1234)", component.toString());

    // Suffix serial
    device = Profiler.Device.newBuilder()
      .setModel("Model-1234")
      .setSerial("1234")
      .build();
    component = renderer.getListCellRendererComponent(list, device, 0, false, false);
    assertEquals("Model (1234)", component.toString());
  }

  @Test
  public void testProcessRendering() throws IOException {
    StudioProfilersView.ProcessComboBoxRenderer renderer = new StudioProfilersView.ProcessComboBoxRenderer();
    JList<Profiler.Process> list = new JList<>();
    // Null process
    Profiler.Process process = null;
    Component component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertEquals(renderer.getEmptyText(), component.toString());

    // Process
    process = Profiler.Process.newBuilder()
      .setName("MyProcessName")
      .setPid(1234)
      .build();
    component = renderer.getListCellRendererComponent(list, process, 0, false, false);
    assertEquals("MyProcessName (1234)", component.toString());
  }

  @Test
  public void testMonitorStage() throws Exception {
    transitionStage(new StudioMonitorStage(myProfilers));
  }

  @Test
  public void testNetworkStage() throws Exception {
    transitionStage(new NetworkProfilerStage(myProfilers));
  }

  @Test
  public void testMemoryStage() throws Exception {
    transitionStage(new MemoryProfilerStage(myProfilers));
  }

  @Test
  public void testCpuStage() throws Exception {
    transitionStage(new CpuProfilerStage(myProfilers));
  }

  @Test
  public void testNoStage() throws Exception {
    StudioProfilersView view = new StudioProfilersView(myProfilers, new IdeProfilerComponentsStub());
    JPanel component = view.getComponent();
    assertNotReachable(myProfilers, view, component);
  }

  public void transitionStage(Stage stage) throws Exception {
    JPanel component = myView.getComponent();
    myProfilers.setStage(new FakeStage(myProfilers));
    assertNotReachable(myProfilers, myView, component);
    myProfilers.setStage(stage);
    // At this point it could be reachable with standard swing listeners.
    myProfilers.setStage(new FakeStage(myProfilers));
    // If we leaked a listener or a component in the tree then there will be a path
    // from the model all the way up to the main view or the main component. There could
    // be the case that some listeners that don't point to the view/component are still
    // leaked but it would be pretty rare that such a listener was needed in the first place.
    assertNotReachable(myProfilers, myView, component);
  }

  /**
   * Asserts that none of the objects can be reached from object.
   */

  private void assertNotReachable(Object object, Object... objects) throws IllegalAccessException {
    Set<Object> set = Sets.newIdentityHashSet();
    Set<Object> invalid = Sets.newIdentityHashSet();
    invalid.addAll(Arrays.asList(objects));
    collectReachable(new LinkedList<>(), object, invalid::contains, set);
  }

  /**
   * Collects all the objects reachable from "object" by following hard links. This method doesn't dive in if it finds
   * objects within java.lang or io.grpc.
   */
  private void collectReachable(LinkedList<Object> path, Object object, Predicate<Object> invalid, Set<Object> reachable) throws IllegalAccessException {
    if (object == null || object.getClass().equals(WeakReference.class) || object.getClass().equals(WeakHashMap.class)) {
      return;
    }
    if (!reachable.add(object)) {
      return;
    }
    String name = object.getClass().getCanonicalName();
    name = name == null ? "" : name;

    if (invalid.test(object)) {
      // We didn't see this object before
      // There are several new internal objects created by the interaction of the UI. Only flag
      // this as an error if it is within our package.
      if (name.startsWith("com.android.tools")) {
        String error = "Found invalid object:\n";
        error += " > \"" + object + "\" :: " + object.getClass().getName() + "\n";
        error += " Reference path:\n";

        for (Object previous : path) {
          error += " > \"" + previous + "\" :: " + previous.getClass().getName() + "\n";
        }
        assertFalse(error, true);
      }
    }
    if (!object.getClass().isArray() && (name.startsWith("java.lang") || name.startsWith("io.grpc"))) {
      return;
    }
    path.push(object);
    if (object.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(object); i++) {
        Object value = Array.get(object, i);
        collectReachable(path, value, invalid, reachable);
      }
    }
    else {
      ArrayList<Field> fields = new ArrayList<>();
      collectInheritedDeclaredFields(object.getClass(), fields);
      for (Field field : fields) {
        if (!field.getType().isPrimitive()) {
          field.setAccessible(true);
          Object value = field.get(object);
          collectReachable(path, value, invalid, reachable);
        }
      }
    }
    path.pop();
  }

  private void collectInheritedDeclaredFields(Class<?> clazz, ArrayList<Field> fields) {
    Collections.addAll(fields, clazz.getDeclaredFields());
    if (clazz.getSuperclass() != null) {
      collectInheritedDeclaredFields(clazz.getSuperclass(), fields);
    }
  }

  static class FakeStage extends Stage {
    public FakeStage(@NotNull StudioProfilers profilers) {
      super(profilers);
    }
    @Override
    public void enter() {

    }
    @Override
    public void exit() {

    }
    @Override
    public ProfilerMode getProfilerMode() {
      return ProfilerMode.NORMAL;
    }
  }

  static class FakeView extends StageView<FakeStage> {

    public FakeView(@NotNull StudioProfilersView profilersView, @NotNull FakeStage stage) {
      super(profilersView, stage);
    }

    @Override
    public JComponent getToolbar() {
      return new JPanel();
    }
  }
}