/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.Slow
import com.android.emulator.control.EmulatorControllerGrpc
import com.android.emulator.control.EmulatorStatus
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.MouseEvent
import com.android.emulator.control.PhysicalModelValue
import com.android.emulator.control.SnapshotPackage
import com.android.emulator.control.SnapshotServiceGrpc
import com.android.emulator.control.VmRunState
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_GRPC_CALLS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import io.grpc.CallCredentials
import io.grpc.CompressorRegistry
import io.grpc.ConnectivityState
import io.grpc.DecompressorRegistry
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.StreamObserver
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls a running Emulator.
 */
class EmulatorController(val emulatorId: EmulatorId, parentDisposable: Disposable) : Disposable {
  private var channel: ManagedChannel? = null
  @Volatile private var emulatorControllerStub: EmulatorControllerGrpc.EmulatorControllerStub? = null
  @Volatile private var snapshotServiceStub: SnapshotServiceGrpc.SnapshotServiceStub? = null
  @Volatile private var emulatorConfigInternal: EmulatorConfiguration? = null
  @Volatile internal var skinDefinition: SkinDefinition? = null
  private var stateInternal = AtomicReference(ConnectionState.NOT_INITIALIZED)
  private val connectionStateListeners: ConcurrentList<ConnectionStateListener> = ContainerUtil.createConcurrentList()
  private val connectivityStateWatcher = object : Runnable {
    override fun run() {
      if (connectionState == ConnectionState.DISCONNECTED) {
        return // DISCONNECTED state is final.
      }
      val ch = channel ?: return
      val state = ch.getState(false)
      when (state) {
        ConnectivityState.CONNECTING -> connectionState = ConnectionState.CONNECTING
        ConnectivityState.SHUTDOWN -> connectionState = ConnectionState.DISCONNECTED
        else -> {}
      }
      ch.notifyWhenStateChanged(state, this)
    }
  }

  var emulatorConfig: EmulatorConfiguration
    get() {
      return emulatorConfigInternal ?: throwNotYetConnected()
    }
    private inline set(value) {
      emulatorConfigInternal = value
    }

  var connectionState: ConnectionState
    get() {
      return stateInternal.get()
    }
    private set(value) {
      if (stateInternal.getAndSet(value) != value) {
        for (listener in connectionStateListeners) {
          listener.connectionStateChanged(this, value)
        }
      }
    }

  private var emulatorController: EmulatorControllerGrpc.EmulatorControllerStub
    get() {
      return emulatorControllerStub ?: throwNotYetConnected()
    }
    private inline set(stub) {
      emulatorControllerStub = stub
    }

  private var snapshotService: SnapshotServiceGrpc.SnapshotServiceStub
    get() {
      return snapshotServiceStub ?: throwNotYetConnected()
    }
    private inline set(stub) {
      snapshotServiceStub = stub
    }

  init {
    Disposer.register(parentDisposable, this)
  }

  @AnyThread
  fun addConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.add(listener)
  }

  @AnyThread
  fun removeConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.remove(listener)
  }

  /**
   * Establishes a connection to the Emulator. The process of establishing a connection is partially
   * asynchronous, but the synchronous part of this method also takes considerable time.
   */
  @Slow
  fun connect() {
    val maxInboundMessageSize: Int
    if (emulatorId.avdFolder != null) {
      val config = EmulatorConfiguration.readAvdDefinition(emulatorId.avdId, emulatorId.avdFolder)
      if (config == null) {
        // The error has already been logged.
        connectionState = ConnectionState.DISCONNECTED
        return
      }
      emulatorConfig = config
      skinDefinition = SkinDefinitionCache.getInstance().getSkinDefinition(config.skinFolder)

      // TODO: Change 4 to 3 after b/150494232 is fixed.
      maxInboundMessageSize = config.displayWidth * config.displayWidth * 4 + 100
    }
    else {
      maxInboundMessageSize = 20 * 1024 * 1024
    }

    connectionState = ConnectionState.CONNECTING
    val channel = NettyChannelBuilder
      .forAddress("localhost", emulatorId.grpcPort)
      .usePlaintext() // TODO: Add support for TLS encryption.
      .maxInboundMessageSize(maxInboundMessageSize)
      .compressorRegistry(CompressorRegistry.newEmptyInstance()) // Disable data compression.
      .decompressorRegistry(DecompressorRegistry.emptyInstance())
      .build()
    this.channel = channel

    val token = emulatorId.grpcToken
    if (token == null) {
      emulatorController = EmulatorControllerGrpc.newStub(channel)
      snapshotService = SnapshotServiceGrpc.newStub(channel)
    }
    else {
      val credentials = TokenCallCredentials(token)
      emulatorController = EmulatorControllerGrpc.newStub(channel).withCallCredentials(credentials)
      snapshotService = SnapshotServiceGrpc.newStub(channel).withCallCredentials(credentials)
    }

    channel.notifyWhenStateChanged(channel.getState(false), connectivityStateWatcher)
    fetchConfiguration()
  }

  /**
   * Sends a [KeyboardEvent] to the Emulator.
   */
  fun sendKey(keyboardEvent: KeyboardEvent, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("sendKey(${shortDebugString(keyboardEvent)})")
    }
    emulatorController.sendKey(keyboardEvent, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSendKeyMethod()))
  }

  /**
   * Sends a [MouseEvent] to the Emulator.
   */
  fun sendMouse(mouseEvent: MouseEvent, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS.get()) {
      LOG.info("sendMouse(${shortDebugString(mouseEvent)})")
    }
    emulatorController.sendMouse(mouseEvent, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSendMouseMethod()))
  }

  /**
   * Retrieves a physical model value.
   */
  fun getPhysicalModel(physicalType: PhysicalModelValue.PhysicalType, streamObserver: StreamObserver<PhysicalModelValue>) {
    val modelValue = PhysicalModelValue.newBuilder().setTarget(physicalType).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getPhysicalModel(${shortDebugString(modelValue)})")
    }
    emulatorController.getPhysicalModel(
        modelValue, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetPhysicalModelMethod()))
  }

  /**
   * Sets a physical model value.
   */
  fun setPhysicalModel(modelValue: PhysicalModelValue, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setPhysicalModel(${shortDebugString(modelValue)})")
    }
    emulatorController.setPhysicalModel(
        modelValue, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetPhysicalModelMethod()))
  }

  /**
   * Sets a virtual machine state.
   */
  fun setVmState(vmState: VmRunState, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setVmModel(${shortDebugString(vmState)})")
    }
    emulatorController.setVmState(vmState, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetVmStateMethod()))
  }

  /**
   * Retrieves a screenshot of an Emulator display.
   */
  fun getScreenshot(imageFormat: ImageFormat, streamObserver: StreamObserver<Image>) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getScreenshot(${shortDebugString(imageFormat)})")
    }
    emulatorController.getScreenshot(imageFormat, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetScreenshotMethod()))
  }

  /**
   * Streams a series of screenshots.
   */
  fun streamScreenshot(imageFormat: ImageFormat, streamObserver: StreamObserver<Image>): Cancelable? {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("streamScreenshot(${shortDebugString(imageFormat)})")
    }
    val method = EmulatorControllerGrpc.getStreamScreenshotMethod()
    val call = emulatorController.channel.newCall(method, emulatorController.callOptions)
    ClientCalls.asyncServerStreamingCall(call, imageFormat, DelegatingStreamObserver(streamObserver, method))
    return object : Cancelable {
      override fun cancel() {
        call.cancel("Canceled by consumer", null)
      }
    }
  }

  private fun fetchConfiguration() {
    val responseObserver = object : DummyStreamObserver<EmulatorStatus>() {
      override fun onNext(response: EmulatorStatus) {
        // TODO: Simplify this code after b/152438029 is fixed.
        if (emulatorConfigInternal == null) {
          val config = EmulatorConfiguration.fromHardwareConfig(response.hardwareConfig!!)
          if (config == null) {
            LOG.warn("Incomplete hardware configuration")
            connectionState = ConnectionState.DISCONNECTED
          }
          else {
            emulatorConfig = config
            // Asynchronously read skin definition.
            ApplicationManager.getApplication().executeOnPooledThread {
              skinDefinition = SkinDefinitionCache.getInstance().getSkinDefinition(config.skinFolder)
              connectionState = ConnectionState.CONNECTED
            }
          }
        }
        else {
          connectionState = ConnectionState.CONNECTED
        }
      }

      override fun onError(t: Throwable) {
        connectionState = ConnectionState.DISCONNECTED
      }
    }

    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getStatus()")
    }
    emulatorController.getStatus(Empty.getDefaultInstance(),
                                 DelegatingStreamObserver(responseObserver, EmulatorControllerGrpc.getGetStatusMethod()))
  }

  fun saveSnapshot(snapshotId: String, streamObserver: StreamObserver<SnapshotPackage> = getDummyObserver()) {
    val snapshot = SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("saveSnapshot(${shortDebugString(snapshot)})")
    }
    snapshotService.saveSnapshot(snapshot, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getSaveSnapshotMethod()))
  }

  private fun throwNotYetConnected(): Nothing {
    throw IllegalStateException("Not yet connected to the Emulator")
  }

  override fun dispose() {
    channel?.shutdownNow()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EmulatorController

    return emulatorId == other.emulatorId
  }

  override fun hashCode(): Int {
    return emulatorId.hashCode()
  }

  /**
   * The state of the [EmulatorController].
   */
  enum class ConnectionState {
    NOT_INITIALIZED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
  }

  private open inner class DelegatingStreamObserver<RequestT, ResponseT>(
    val delegate: StreamObserver<in ResponseT>?,
    val method: MethodDescriptor<in RequestT, in ResponseT>
  ) : StreamObserver<ResponseT> {
    override fun onNext(response: ResponseT) {
      delegate?.onNext(response)
    }

    override fun onError(t: Throwable) {
      if (!(t is StatusRuntimeException && t.status.code == Status.Code.CANCELLED) && channel?.isShutdown == false) {
        LOG.warn("${method.fullMethodName} call failed - ${t.message}")
      }

      delegate?.onError(t)

      if (t is StatusRuntimeException && t.status.code == Status.Code.UNAVAILABLE) {
        connectionState = ConnectionState.DISCONNECTED
      }
    }

    override fun onCompleted() {
      delegate?.onCompleted()
    }
  }

  /**
   * Defines interface for an object that receives notifications when the state of the Emulator
   * connection changes.
   */
  interface ConnectionStateListener {
    /**
     * Called when the state of the Emulator connection changes.
     */
    @AnyThread
    fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState)
  }

  private class TokenCallCredentials(private val token: String) : CallCredentials() {

    override fun applyRequestMetadata(requestInfo: RequestInfo, executor: Executor, applier: MetadataApplier) {
      executor.execute {
        try {
          val headers = Metadata()
          headers.put(AUTHORIZATION_METADATA_KEY, "Bearer $token")
          applier.apply(headers)
        }
        catch (e: Throwable) {
          applier.fail(Status.UNAUTHENTICATED.withCause(e))
        }
      }
    }

    override fun thisUsesUnstableApi() {
    }
  }

  companion object {
    @JvmStatic
    private val AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    @JvmStatic
    private val LOG = Logger.getInstance(EmulatorController::class.java)
    @JvmStatic
    private val DUMMY_OBSERVER = DummyStreamObserver<Any>()

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> getDummyObserver(): StreamObserver<T> {
      return DUMMY_OBSERVER as StreamObserver<T>
    }
  }
}
