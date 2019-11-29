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
package com.android.tools.idea.tests.gui.performance;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.Bleak;
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.UseBleak;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import java.lang.ref.PhantomReference;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The test executes a basic scenario of layout editor (shows the layout and editor tabs of three different
 * layout files). At the beginning of the test a snapshot of all live objects is taken and is later compared with the
 * memory state at the end of the scenario. Note that the scenario should always end up in the same model/UI state
 * as at the beginning of the test (same dialogs open, same content of project files, etc.).
 *
 * <p>The test will mark as non-leaks classes that have less or equal number of instances than at the beginning of the test.
 * The scenario is repeated until all classes are marked or {@code MAX_LOOP_COUNT} loops are made. If there are any unmarked classes
 * (i.e. a number of instances for this class grew with each iteration) the test reports them as a potential memory leak.
 *
 * <p>{@code ourIgnoredClasses} collection lists all classes that are either known leaks (with a bug number assigned to fix it) or
 * known false positives.
 *
 * <p>If {@code CAPTURE_HEAP_DUMPS} field is set to true, the test will create heap dumps from before and after the run for
 * further leak analysis.
 */
@RunWith(GuiTestRemoteRunner.class)
public class LayoutEditorMemoryUseTest {

  private static final int MAX_LOOP_COUNT = 5;
  private static final Set<String> ourIgnoredClasses = ImmutableSet.of();

  private static final Logger LOG = Logger.getInstance(LayoutEditorMemoryUseTest.class);
  private static final boolean CAPTURE_HEAP_DUMPS = false;

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @NotNull
  private static TObjectIntHashMap<String> copyMapWithSizeOnly(THashMap<String, THashSet<Object>> map) {
    TObjectIntHashMap<String> result;
    result = new TObjectIntHashMap<>(map.size());
    for (Map.Entry<String, THashSet<Object>> entry : map.entrySet()) {
      result.put(entry.getKey(), entry.getValue().size());
    }
    return result;
  }

  public static void reportCollectionsDelta(TObjectIntHashMap<String> previousCounts,
                                            TObjectIntHashMap<String> currentCounts) {
    StringBuilder sb = new StringBuilder();
    sb.append("Instance count delta:\n");
    TObjectIntIterator<String> iterator = currentCounts.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      int previousCount = previousCounts.get(iterator.key());
      if (previousCount != 0 && iterator.value() > previousCount) {
        sb.append(" Instance: ")
          .append(iterator.key()).append(" => +").append(iterator.value() - previousCount).append("\n");
      }
    }
    LOG.warn(sb.toString());
  }

  @Test
  public void navigateAndEdit() throws Exception {
    IdeFrameFixture fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    warmUp(fixture);

    if (CAPTURE_HEAP_DUMPS) {
      createHprofDump("/tmp/LayoutEditorMemoryUseTest-before.hprof");
    }

    LeakedInstancesTracker instancesTracker = LeakedInstancesTracker.createWithInitialSnapshot();
    // Stop tracking classes that are ignored
    for (String className : ourIgnoredClasses) {
      instancesTracker.doNotTrack(className);
    }

    TObjectIntHashMap<String> previousCountsInstances = null;
    TObjectIntHashMap<String> currentCountsInstances = null;

    for (int i = 0; i < MAX_LOOP_COUNT && !instancesTracker.isEmpty(); i++) {
      runScenario(fixture);

      instancesTracker.snapshot();

      previousCountsInstances = currentCountsInstances;
      currentCountsInstances = copyMapWithSizeOnly(instancesTracker.getCurrentLeakCounts());
      LOG.info("[Pass " + (i + 1) + "] Potential leaked classes count: " + currentCountsInstances.size());
    }

    if (!currentCountsInstances.isEmpty()) {
      // Leaks have been found. Create a report.

      if (!currentCountsInstances.isEmpty()) {
        reportCollectionsDelta(previousCountsInstances, currentCountsInstances);
      }

      if (CAPTURE_HEAP_DUMPS) {
        instancesTracker = null;
        createHprofDump("/tmp/LayoutEditorMemoryUseTest-after.hprof");
      }

      Assert.fail("Found leaked objects.");
    }
  }

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  public void navigateAndEditWithBLeak() throws Exception {
    IdeFrameFixture fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    Bleak.runWithBleak(() -> runScenario(fixture));
  }

  public void createHprofDump(String path) throws Exception {
    Files.deleteIfExists(Paths.get(path));
    MemoryDumpHelper.captureMemoryDump(path);
  }

  /**
   * Warm-up the scenario. Typically a single scenario run is enough.
   * Any additional setup should be performed in this method (show/hide IDE elements,
   * configure theme, connect app to Firebase, setup emulator, etc.)
   */
  public void warmUp(IdeFrameFixture fixture) {
    runScenario(fixture);
  }

  /**
   * Main scenario. The scenario will be repeated multiple times during a run.
   * The state after each run should be the same as just after the {@code warmUp()} method.
    */
  public void runScenario(IdeFrameFixture fixture) {
    String[] layoutFilePaths = {
      "app/src/main/res/layout/layout2.xml",
      "app/src/main/res/layout/widgets.xml",
      "app/src/main/res/layout/textstyles.xml",
    };

    // First file on design mode
    fixture.getEditor().open(layoutFilePaths[0], EditorFixture.Tab.DESIGN).getLayoutEditor(true).waitForRenderToFinish();
    // Second file on design mode, then switch to text mode
    fixture.getEditor().open(layoutFilePaths[1], EditorFixture.Tab.DESIGN).getLayoutEditor(true).waitForRenderToFinish();
    fixture.getEditor().selectEditorTab(EditorFixture.Tab.EDITOR);
    // Third file on text mode
    fixture.getEditor().open(layoutFilePaths[2], EditorFixture.Tab.EDITOR);
  }

  private static class LeakedInstancesTracker {
    private static final String ANDROID_CLASSES_PREFIX = "com.android.tools.";
    private THashMap<String, THashSet<Object>> myClasses;

    private LeakedInstancesTracker(THashMap<String, THashSet<Object>> classes) {
      myClasses = classes;
    }

    public static LeakedInstancesTracker createWithInitialSnapshot() {
      InstanceCounter instanceCounter = new InstanceCounter(ANDROID_CLASSES_PREFIX);
      LeakHunter.checkLeak(LeakHunter.allRoots(), Object.class, instanceCounter::registerObjectInstance);
      return new LeakedInstancesTracker(instanceCounter.getClassNameToInstancesMap());
    }

    public void snapshot() {
      InstanceCounter instanceCounter = new InstanceCounter(ANDROID_CLASSES_PREFIX);
      LeakHunter.checkLeak(LeakHunter.allRoots(), Object.class, instanceCounter::registerObjectInstance);

      // Remove all classes that have no instances
      for (Iterator<Map.Entry<String, THashSet<Object>>> it = myClasses.entrySet().iterator(); it.hasNext(); ) {
        final Map.Entry<String, THashSet<Object>> entry = it.next();
        if (!instanceCounter.getClassNameToInstancesMap().containsKey(entry.getKey())) {
          it.remove();
        }
      }
      // Remove all classes for which instance counts dropped
      for (Map.Entry<String, THashSet<Object>> entry : instanceCounter.getClassNameToInstancesMap().entrySet()) {
        if (!myClasses.containsKey(entry.getKey())) {
          continue;
        }
        if (myClasses.get(entry.getKey()).size() >= entry.getValue().size()) {
          myClasses.remove(entry.getKey());
        }
        else {
          myClasses.put(entry.getKey(), entry.getValue());
        }
      }
    }

    public boolean isEmpty() {
      return myClasses.isEmpty();
    }

    public THashMap<String, THashSet<Object>> getCurrentLeakCounts() {
      return myClasses;
    }

    public void doNotTrack(String name) {
      myClasses.remove(name);
    }

    private static class InstanceCounter {
      private String myPrefixFilter;
      private THashMap<String, THashSet<Object>> myClassNameToInstances;

      public InstanceCounter(String prefixFilter) {
        myPrefixFilter = prefixFilter;
        myClassNameToInstances = new THashMap<>();
      }

      public boolean registerObjectInstance(Object o) {
        Class clazz = o.getClass();
        final String clazzName = clazz.getName();
        if (myPrefixFilter != null && !clazzName.startsWith(myPrefixFilter)) return false;

        // Ignore phantom references as they can lead to false positives.
        if (PhantomReference.class.isAssignableFrom(clazz)) return false;

        if (myClassNameToInstances.containsKey(clazzName)) {
          myClassNameToInstances.get(clazzName).add(o);
        }
        else {
          myClassNameToInstances.put(clazzName, ContainerUtil.newIdentityTroveSet());
        }
        return false;
      }

      public THashMap<String, THashSet<Object>> getClassNameToInstancesMap() {
        return myClassNameToInstances;
      }
    }
  }
}
