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
 */
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferTypeAction;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferWireframeAction;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.service.Context;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos.FramebufferAttachment;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.msg.Msg;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.ui.content.Content;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

public class FrameBufferController extends ImagePanelController implements AtomStream.Listener {
  public static Content createUI(GfxTraceEditor editor, MainController.ContentCreator contentCreator) {
    FrameBufferController controller = new FrameBufferController(editor);
    return contentCreator.create(controller.myPanel, controller.getFocusComponent());
  }

  private static final int MAX_SIZE = 0xffff;

  @NotNull private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  @NotNull private final RenderSettings mySettings = new RenderSettings();
  @NotNull private FramebufferAttachment myFramebufferAttachment = FramebufferAttachment.Color0;

  private FrameBufferController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);

    editor.getAtomStream().addListener(this);

    mySettings.setMaxHeight(MAX_SIZE);
    mySettings.setMaxWidth(MAX_SIZE);
    mySettings.setWireframeMode(WireframeMode.None);

    initToolbar(getToolbarActions(), false);
  }

  private DefaultActionGroup getToolbarActions() {
    FramebufferTypeAction typeAction = new FramebufferTypeAction(this);
    myEditor.addConnectionListener(con -> typeAction.setMultiRenderTargetSupport(con.getFeatures().hasFramebufferAttachment()));

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(typeAction);
    group.add(new Separator());
    group.add(new FramebufferWireframeAction(this, WireframeMode.None, "Shaded", "Display the framebuffer with shaded polygons",
                                             AndroidIcons.GfxTrace.WireframeNone));
    group.add(new FramebufferWireframeAction(this, WireframeMode.Overlay, "Shaded + Wireframe",
                                             "Display the framebuffer with shaded polygons and overlay the wireframe of the last draw call",
                                             AndroidIcons.GfxTrace.WireframeOverlay));
    group.add(new FramebufferWireframeAction(this, WireframeMode.All, "Wireframe", "Display the framebuffer with wireframes",
                                             AndroidIcons.GfxTrace.WireframeAll));
    group.add(new Separator());
    return group;
  }

  @NotNull
  public FramebufferAttachment getFramebufferAttachment() {
    return myFramebufferAttachment;
  }

  public void setFramebufferAttachment(@NotNull FramebufferAttachment bufferType) {
    if (!myFramebufferAttachment.equals(bufferType)) {
      myFramebufferAttachment = bufferType;
      updateBuffer();
    }
  }

  @NotNull
  public WireframeMode getWireframeMode() {
    return mySettings.getWireframeMode();
  }

  public void setWireframeMode(@NotNull WireframeMode mode) {
    if (!mySettings.getWireframeMode().equals(mode)) {
      mySettings.setWireframeMode(mode);
      updateBuffer();
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (myRenderDevice.updateIfNotNull(event.findDevicePath())) {
      updateBuffer();
    }
  }

  @Override
  public void onAtomLoadingStart(AtomStream atoms) {
    setImage(null);
  }

  @Override
  public void onAtomLoadingComplete(AtomStream atoms) {
  }

  @Override
  public void onAtomsSelected(AtomRangePath path, Object source) {
    updateBuffer();
  }

  @Override
  public void onContextChanged(@NotNull Context context) {
  }

  private void updateBuffer() {
    AtomRangePath atomPath = myEditor.getAtomStream().getSelectedAtomsPath();
    if (atomPath != null) {
      setImage(FetchedImage.load(myEditor.getClient(), getImageInfoPath(atomPath.getPathToLast())));
    }
  }

  private ListenableFuture<ImageInfoPath> getImageInfoPath(AtomPath atomPath) {
    DevicePath device = myRenderDevice.getPath();

    if (device == null) {
      return Futures.immediateFailedFuture(new ErrDataUnavailable().setReason(new Msg().setIdentifier(GfxTraceEditor.MESSAGE_NO_REPLAY_DEVICE)));
    }

    if (myEditor.getFeatures().hasFramebufferAttachment()) {
      return myEditor.getClient().getFramebufferAttachment(device, atomPath, myFramebufferAttachment, mySettings);
    }

    // DEPRECATED: Logic below is to support GAPIS without the 'framebuffer-attachment' feature.
    switch (myFramebufferAttachment) {
      case Color0:
        return myEditor.getClient().getFramebufferColor(device, atomPath, mySettings);
      case Depth:
        return myEditor.getClient().getFramebufferDepth(device, atomPath);
      default:
        return null;
    }
    // END DEPRECATED
  }
}
