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
package com.android.tools.idea.run.applychanges

import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.util.SwapInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.content.Content
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.picocontainer.PicoContainer
import kotlin.test.fail

/**
 * Unit tests for utility functions of apply-changes run pipeline.
 */
@RunWith(JUnit4::class)
class ApplyChangesUtilsTest {
  @Mock
  lateinit var mockEnv: ExecutionEnvironment
  @Mock
  lateinit var mockDevices: DeviceFutures
  @Mock
  lateinit var mockProject: Project
  @Mock
  lateinit var mockExecutionManager: ExecutionManager
  @Mock
  lateinit var mockPicoContainer: PicoContainer
  @Mock
  lateinit var mockRunProfile: AndroidRunConfigurationBase
  @Mock
  lateinit var mockExecutionTarget: ExecutionTarget
  @Mock
  lateinit var mockDebugManager: DebuggerManagerEx
  @Mock
  lateinit var mockRunContentManager: RunContentManager

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    `when`(mockEnv.project).thenReturn(mockProject)
    `when`(mockEnv.runProfile).thenReturn(mockRunProfile)
    `when`(mockEnv.executionTarget).thenReturn(mockExecutionTarget)
    `when`(mockProject.picoContainer).thenReturn(mockPicoContainer)
    `when`(mockPicoContainer.getComponentInstance(eq(ExecutionManager::class.java.name))).thenReturn(mockExecutionManager)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf())
    `when`(mockProject.getComponent(eq(DebuggerManager::class.java))).thenReturn(mockDebugManager)
    `when`(mockDebugManager.sessions).thenReturn(listOf())
    `when`(mockPicoContainer.getComponentInstance(eq(RunContentManager::class.java.name))).thenReturn(mockRunContentManager)
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_noPreviousHandler() {
    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isNull()
    assertThat(prevHandler.executionConsole).isNull()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithSwapInfo() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf(mockProcessHandler))
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    `when`(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    `when`(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    `when`(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    val mockRunContentDescriptor = mock(RunContentDescriptor::class.java)
    `when`(mockRunContentDescriptor.processHandler).thenReturn(mockProcessHandler)
    val mockConsole = mock(ExecutionConsole::class.java)
    `when`(mockRunContentDescriptor.executionConsole).thenReturn(mockConsole)
    `when`(mockRunContentManager.allDescriptors).thenReturn(listOf(mockRunContentDescriptor))
    val mockSwapInfo = mock(SwapInfo::class.java)
    `when`(mockEnv.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(mockSwapInfo)

    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isSameAs(mockProcessHandler)
    assertThat(prevHandler.executionConsole).isSameAs(mockConsole)
    verify(mockProcessHandler, never()).detachProcess()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithSwapInfo_noPreviousConsole() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf(mockProcessHandler))
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    `when`(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    `when`(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    `when`(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    val mockRunContentDescriptor = mock(RunContentDescriptor::class.java)
    `when`(mockRunContentDescriptor.processHandler).thenReturn(mockProcessHandler)
    `when`(mockRunContentManager.allDescriptors).thenReturn(listOf(mockRunContentDescriptor))
    val mockSwapInfo = mock(SwapInfo::class.java)
    `when`(mockEnv.getUserData(eq(SwapInfo.SWAP_INFO_KEY))).thenReturn(mockSwapInfo)

    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isSameAs(mockProcessHandler)
    assertThat(prevHandler.executionConsole).isNull()
    verify(mockProcessHandler, never()).detachProcess()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithoutSwapInfo_triggersColdSwap() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf(mockProcessHandler))
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    `when`(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    `when`(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    `when`(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)

    val prevHandler = findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)

    assertThat(prevHandler.processHandler).isNull()
    assertThat(prevHandler.executionConsole).isNull()

    // Make sure that the previous process handler is detached when the swap info is missing.
    verify(mockProcessHandler).detachProcess()
  }

  @Test
  fun findExistingSessionAndMaybeDetachForColdSwap_previousHandlerWithoutSwapInfo_coldSwapCancelledByUser() {
    val mockProcessHandler = mock(ProcessHandler::class.java)
    `when`(mockExecutionManager.runningProcesses).thenReturn(arrayOf(mockProcessHandler))
    val mockSessionInfo = mock(AndroidSessionInfo::class.java)
    `when`(mockProcessHandler.getUserData(eq(AndroidSessionInfo.KEY))).thenReturn(mockSessionInfo)
    `when`(mockSessionInfo.runConfiguration).thenReturn(mockRunProfile)
    `when`(mockSessionInfo.processHandler).thenReturn(mockProcessHandler)
    val mockRunContentDescriptor = mock(RunContentDescriptor::class.java)
    `when`(mockRunContentDescriptor.processHandler).thenReturn(mockProcessHandler)
    `when`(mockRunContentManager.allDescriptors).thenReturn(listOf(mockRunContentDescriptor))
    val mockContent = mock(Content::class.java)
    `when`(mockRunContentDescriptor.attachedContent).thenReturn(mockContent)
    val mockExecutor = mock(Executor::class.java)
    `when`(mockContent.getUserData(any(Key::class.java))).thenReturn(mockExecutor)

    try {
      findExistingSessionAndMaybeDetachForColdSwap(mockEnv, mockDevices)
      fail("Expected ApplyChangesCancelledException but did not happen")
    }
    catch (expected: ApplyChangesCancelledException) {
      // pass.
    }

    // Make sure that the previous process handler is not detached when cancellation.
    verify(mockProcessHandler, never()).detachProcess()
  }
}