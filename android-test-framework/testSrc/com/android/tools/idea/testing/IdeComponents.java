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
package com.android.tools.idea.testing;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import com.intellij.mock.MockDumbService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.util.pico.DefaultPicoContainer;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.stubbing.Answer;
import org.picocontainer.ComponentAdapter;

public final class IdeComponents implements Disposable {
  private Project myProject;
  private final Queue<Runnable> myUndoQueue = new ArrayDeque<>();

  public IdeComponents(@NotNull Project project) {
    this(project, project);
  }

  public IdeComponents(@NotNull IdeaProjectTestFixture fixture) {
    this(fixture.getProject(), fixture.getTestRootDisposable());
  }

  public IdeComponents(@Nullable Project project, @NotNull Disposable disposable) {
    if (project instanceof ProjectImpl) {
      if (((ProjectImpl)project).isLight() && disposable == project) {
        throw new AssertionError("Light (in-memory) projects are not disposed between tests, please use other IdeComponents " +
                                 "constructor when using light fixtures.");
      }
    }

    myProject = project;
    if (project != null) {
      Disposer.register(project, () -> myProject = null);
    }

    Disposer.register(disposable, this);
  }

  @NotNull
  public <T> T mockApplicationService(@NotNull Class<T> serviceType) {
    T mock = mock(serviceType);
    doReplaceService(ApplicationManager.getApplication(), serviceType, mock, myUndoQueue);
    return mock;
  }
  @NotNull
  public <T> T mockApplicationServiceWithAnswer(@NotNull Class<T> serviceType, Answer<Object> runtimeExceptionAnswer) {
    T mock = mock(serviceType, runtimeExceptionAnswer);
    doReplaceService(ApplicationManager.getApplication(), serviceType, mock, myUndoQueue);
    return mock;
  }

  public <T> void replaceApplicationService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    doReplaceService(ApplicationManager.getApplication(), serviceType, newServiceInstance, myUndoQueue);
  }

  public <T> void replaceProjectService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    doReplaceService(myProject, serviceType, newServiceInstance, myUndoQueue);
  }

  public <T> void replaceModuleService(@NotNull Module module, @NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    doReplaceService(module, serviceType, newServiceInstance, myUndoQueue);
  }

  /**
   * DumbService is a regular project service, however unlike other services it can't be mocked by directly replacing its
   * instance in the ServiceManager. The reason is that ServiceManager is accessed only once to retrieve the project's DumbService
   * instance (during the project initialisation). After that, the instance is saved into {@link DumbService#INSTANCE_KEY} and
   * obtained from there as a result of each {@link DumbService#getInstance(Project)} call onwards.
   * Therefore, a special method is required to deal with this situation by operating directly with
   * {@link DumbService#INSTANCE_KEY} and placing the mock there.
   *
   * @see DumbService#INSTANCE_KEY
   * @see DumbService#getInstance(Project)
   */
  public static void replaceProjectDumbService(@NotNull Project project, @NotNull DumbService newServiceInstance) {
    doReplaceProjectDumbService(project, newServiceInstance, null);
  }

  private static void doReplaceProjectDumbService(@NotNull Project project,
                                                  @NotNull DumbService newServiceInstance,
                                                  @Nullable Queue<Runnable> undoQueue) {
    DumbService oldInstance = SentinelDumbService.replaceInstance(project, newServiceInstance);
    if (undoQueue != null) {
      undoQueue.add(() -> doReplaceProjectDumbService(project, oldInstance, null));
    }
  }

  @NotNull
  public <T> T mockProjectService(@NotNull Class<T> serviceType) {
    T mock = mock(serviceType);
    assertNotNull(myProject);
    doReplaceService(myProject, serviceType, mock, myUndoQueue);
    return mock;
  }

  @Override
  public void dispose() {
    restore();
  }

  private void restore() {
    for (Runnable runnable : myUndoQueue) {
      runnable.run();
    }
    myUndoQueue.clear();
  }

  private static <T> void doReplaceService(@NotNull ComponentManager componentManager,
                                           @NotNull Class<T> serviceType,
                                           @NotNull T newServiceInstance,
                                           @Nullable Queue<Runnable> undoQueue) {
    DefaultPicoContainer picoContainer = (DefaultPicoContainer)componentManager.getPicoContainer();

    String componentKey = serviceType.getName();

    Object componentInstance = picoContainer.getComponentInstance(componentKey);
    assert componentInstance == null || serviceType.isAssignableFrom(componentInstance.getClass());
    T oldServiceInstance = (T)componentInstance;

    ComponentAdapter componentAdapter = picoContainer.unregisterComponent(componentKey);
    assert componentAdapter != null : String.format("%s not registered in %s, are you using the right service scope (application vs project)?",
                                                    componentKey, componentManager.getClass().getSimpleName());

    picoContainer.registerComponentInstance(componentKey, newServiceInstance);
    assertSame(newServiceInstance, picoContainer.getComponentInstance(componentKey));

    if (undoQueue != null && oldServiceInstance != null) {
      undoQueue.add(() -> doReplaceService(componentManager, serviceType, oldServiceInstance, null));
    }
  }

  private static class SentinelDumbService extends MockDumbService {
    /**
     * This class is a sentinel to access DumbService.INSTANCE_KEY when replacing project-wide DumbService.
     * So the constructor is private as we never need instances of this class, just need to make the compiler happy.
     */
    private SentinelDumbService(@NotNull Project project) {
      super(project);
    }

    protected static DumbService replaceInstance(@NotNull Project project, @NotNull DumbService newInstance) {
      try {
        Field field = DumbService.class.getDeclaredField("INSTANCE_KEY");
        field.setAccessible(true);
        Object instance_key_object = field.get(null);
        NotNullLazyKey<DumbService, Project> instance_key = (NotNullLazyKey<DumbService, Project>)instance_key_object;

        // TODO: Replace the reflection above with a plain call to INSTANCE_KEY once it's widened from private to protected upstream.
        // We need to use reflection in the mean time as tools/adt/idea must hold the guarantee of being compile-time
        // compatible with the platform code.
        // See also ag/3177871.
        DumbService oldInstance = instance_key.getValue(project);
        instance_key.set(project, newInstance);
        return oldInstance;
      }
      catch (ReflectiveOperationException | ClassCastException e) {
        throw new UnsupportedOperationException("Dumb service could not be mocked.", e);
      }
    }
  }
}
