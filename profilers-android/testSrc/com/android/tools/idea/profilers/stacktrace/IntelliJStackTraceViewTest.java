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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeMouse;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.laf.HeadlessListUI;
import com.android.tools.profilers.FakeFeatureTracker;
import com.android.tools.profilers.stacktrace.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class IntelliJStackTraceViewTest {
  private static final String STACK_STRING =
    "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)\n" +
    "com.example.android.displayingbitmaps.util.ImageFetcher.processBitmap(ImageFetcher.java:214)\n" +
    "com.example.android.displayingbitmaps.util.ImageFetcher.processBitmap(ImageFetcher.java:257)\n" +
    "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:312)\n" +
    "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:257)\n" +
    "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)\n" +
    "java.util.concurrent.FutureTask.run(FutureTask.java:237)\n" +
    "java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1133)\n" +
    "java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:607)\n" +
    "java.lang.Thread.run(Thread.java:761)";
  private static final List<CodeLocation> CODE_LOCATIONS = Arrays.stream(STACK_STRING.split("\\n")).map(
    line -> new StackFrameParser(line).toCodeLocation())
    .collect(Collectors.toList());

  private IntelliJStackTraceView myStackView;

  public static StackTraceModel createStackTraceModel() {
    return new StackTraceModel(new FakeCodeNavigator(new FakeFeatureTracker()));
  }

  public static IntelliJStackTraceView createStackTraceView(Project project, StackTraceModel model) {
    return new IntelliJStackTraceView(project, model, (p, location) -> new FakeCodeElement(location));
  }

  @Before
  public void before() {
    // Arbitrary size just so we can click on it
    StackTraceModel model = createStackTraceModel();
    IntelliJStackTraceView view = createStackTraceView(ProjectStub.getInstance(), model);
    view.getComponent().setLocation(0, 0);
    view.getComponent().setSize(100, 400);
    view.getListView().setUI(new HeadlessListUI());

    myStackView = view;
  }

  @Test
  public void equalityTest() {
    final String duplicateTestString = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";
    StackFrameParser parser = new StackFrameParser(duplicateTestString);
    CodeLocation positiveTest = parser.toCodeLocation();
    CodeLocation negativeTest1 = CodeLocation.stub();
    CodeLocation negativeTest2 = new CodeLocation.Builder(positiveTest).setMethodName(positiveTest.getFileName()).build();
    CodeLocation negativeTest3 = new CodeLocation.Builder(positiveTest).setLineNumber(-1).build();
    assertThat(CODE_LOCATIONS.get(0)).isEqualTo(positiveTest);
    assertThat(negativeTest1).isNotEqualTo(positiveTest);
    assertThat(negativeTest2).isNotEqualTo(positiveTest);
    assertThat(negativeTest3).isNotEqualTo(positiveTest);
    assertThat(negativeTest2).isNotEqualTo(negativeTest1);
    assertThat(negativeTest3).isNotEqualTo(negativeTest1);
  }

  @Test
  public void nativeEqualityTest() {
    CodeLocation loc = new CodeLocation.Builder("type").setMethodName("method").setNativeModuleName("module1")
      .setMethodParameters(Arrays.asList("int", "std::string &"))
      .setFileName("file").setLineNumber(1).setNativeCode(true).build();
    CodeLocation positive = new CodeLocation.Builder(loc).build();
    assertThat(loc).isEqualTo(positive);

    CodeLocation negativeTest1 = new CodeLocation.Builder(positive).setNativeCode(false).build();
    assertThat(loc).isNotEqualTo(negativeTest1);

    CodeLocation negativeTest2 = new CodeLocation.Builder(positive).setNativeModuleName("module2").build();
    assertThat(loc).isNotEqualTo(negativeTest2);
  }

  @Test
  public void setStackFramesTest() {
    myStackView.getModel().setStackFrames(STACK_STRING);
    List<CodeLocation> viewLocations = myStackView.getModel().getCodeLocations();
    assertThat(viewLocations).isEqualTo(CODE_LOCATIONS);

    myStackView.getModel().setStackFrames(ThreadId.INVALID_THREAD_ID, CODE_LOCATIONS);
    viewLocations = myStackView.getModel().getCodeLocations();
    assertThat(viewLocations).isEqualTo(CODE_LOCATIONS);

    myStackView.getModel().setStackFrames(new ThreadId(5), CODE_LOCATIONS);
    viewLocations = myStackView.getModel().getCodeLocations();
    assertThat(viewLocations).isEqualTo(CODE_LOCATIONS);
    ListModel model = myStackView.getListView().getModel();
    assertThat(model.getSize()).isEqualTo(CODE_LOCATIONS.size() + 1);
    Object threadElement = model.getElementAt(model.getSize() - 1);
    assertThat(threadElement).isInstanceOf(ThreadElement.class);
    assertThat(((ThreadElement)threadElement).getThreadId()).isEqualTo(new ThreadId(5));
  }

  @Test
  public void setSelectedRowByModel() throws InvocationTargetException, InterruptedException {
    final int[] invocationCount = {0};
    myStackView.getModel().setStackFrames(STACK_STRING);
    myStackView.getListView().addListSelectionListener(e -> invocationCount[0]++);

    JList list = myStackView.getListView();
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setSelectedIndex(3);
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
    assertThat(invocationCount[0]).isEqualTo(1);
    assertThat(list.getSelectedIndex()).isEqualTo(3);

    myStackView.getModel().setSelectedIndex(-2);
    assertThat(invocationCount[0]).isEqualTo(2);
    assertThat(list.getSelectedValue()).isNull();
  }

  @Ignore("b/73322442")
  @Test
  public void doubleClickingStackViewNavigatesToSelectedElement() throws InvocationTargetException, InterruptedException {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList list = myStackView.getListView();
    assertThat(list.getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setStackFrames(STACK_STRING);
    assertThat(myStackView.getModel().getCodeLocations().size()).isEqualTo(CODE_LOCATIONS.size());

    fakeUi.mouse.doubleClick(5, 5); // First row
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
    assertThat(invocationCount[0]).isEqualTo(1);
  }

  @Test
  public void rightClickingStackTraceView() {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList list = myStackView.getListView();
    assertThat(list.getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setStackFrames(STACK_STRING);
    assertThat(myStackView.getModel().getCodeLocations().size()).isEqualTo(CODE_LOCATIONS.size());

    fakeUi.mouse.click(5, 5, FakeMouse.Button.RIGHT); // First row
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
  }

  @Test
  public void pressingEnterOnStackViewNavigatesToSelectedElement() throws Exception {
    FakeUi fakeUi = new FakeUi(myStackView.getComponent());
    AspectObserver observer = new AspectObserver();
    final int[] invocationCount = {0};
    myStackView.getModel().addDependency(observer).onChange(StackTraceModel.Aspect.SELECTED_LOCATION, () -> invocationCount[0]++);

    JList list = myStackView.getListView();
    assertThat(list.getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.SINGLE_SELECTION);
    assertThat(invocationCount[0]).isEqualTo(0);
    assertThat(list.getSelectedIndex()).isEqualTo(-1);

    myStackView.getModel().setStackFrames(STACK_STRING);
    assertThat(myStackView.getModel().getCodeLocations().size()).isEqualTo(CODE_LOCATIONS.size());

    fakeUi.mouse.click(5, 5); // First row
    assertThat(list.getSelectedValue()).isInstanceOf(FakeCodeElement.class);
    assertThat(invocationCount[0]).isEqualTo(0);

    fakeUi.keyboard.setFocus(list);
    fakeUi.keyboard.press(FakeKeyboard.Key.ENTER);
    assertThat(invocationCount[0]).isEqualTo(1);
  }

  private static class FakeCodeElement implements CodeElement {
    @NotNull private final CodeLocation myCodeLocation;

    public FakeCodeElement(@NotNull CodeLocation codeLocation) {
      myCodeLocation = codeLocation;
    }

    @NotNull
    @Override
    public CodeLocation getCodeLocation() {
      return myCodeLocation;
    }

    @NotNull
    @Override
    public String getPackageName() {
      return "";
    }

    @NotNull
    @Override
    public String getSimpleClassName() {
      return "";
    }

    @NotNull
    @Override
    public String getMethodName() {
      return "";
    }

    @Override
    public boolean isInUserCode() {
      return false;
    }
  }
}
