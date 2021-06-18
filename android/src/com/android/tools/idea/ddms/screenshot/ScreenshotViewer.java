/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.resources.ScreenOrientation;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.device.DeviceArtDescriptor;
import com.android.tools.adtui.device.DeviceArtPainter;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.pixelprobe.color.Colors;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import java.awt.BorderLayout;
import java.awt.color.ICC_ColorSpace;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;
import org.w3c.dom.Node;

public class ScreenshotViewer extends DialogWrapper implements DataProvider {
  @NonNls private static final String SCREENSHOT_VIEWER_DIMENSIONS_KEY = "ScreenshotViewer.Dimensions";
  @NonNls private static final String SCREENSHOT_SAVE_PATH_KEY = "ScreenshotViewer.SavePath";

  private final @NotNull Project myProject;
  private final @Nullable IDevice myDevice;

  private final VirtualFile myBackingVirtualFile;
  private final ImageFileEditor myImageFileEditor;
  private final FileEditorProvider myProvider;

  private final List<DeviceArtDescriptor> myDeviceArtDescriptors;

  private JPanel myPanel;
  private JButton myRefreshButton;
  private JButton myRotateRightButton;
  private JButton myRotateLeftButton;
  private JPanel myContentPane;
  private JCheckBox myFrameScreenshotCheckBox;
  private JComboBox<String> myDeviceArtCombo;
  private JButton myCopyButton;

  /**
   * Number of quadrants by which the screenshot from the device has been rotated. One of 0, 1, 2 or 3.
   */
  private int myRotationQuadrants = 0;

  /**
   * Reference to the screenshot obtained from the device and then rotated by {@link #myRotationQuadrants} degrees.
   * Accessed from both EDT and background threads.
   */
  private final AtomicReference<BufferedImage> mySourceImageRef = new AtomicReference<>();

  /**
   * Reference to the framed screenshot displayed on screen. Accessed from both EDT and background threads.
   */
  private final AtomicReference<BufferedImage> myDisplayedImageRef = new AtomicReference<>();

  /**
   * User specified destination where the screenshot is saved.
   */
  private @Nullable File myScreenshotFile;

  public ScreenshotViewer(@NotNull Project project,
                          @NotNull BufferedImage image,
                          @NotNull File backingFile,
                          @Nullable IDevice device,
                          @Nullable String deviceModel) {
    super(project, true);

    myProject = project;
    myDevice = device;
    mySourceImageRef.set(image);
    myDisplayedImageRef.set(image);

    myBackingVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(backingFile);
    assert myBackingVirtualFile != null;

    myRefreshButton.setIcon(AllIcons.Actions.Refresh);
    myRefreshButton.setEnabled(device != null);

    myProvider = getImageFileEditorProvider();
    myImageFileEditor = (ImageFileEditor)myProvider.createEditor(myProject, myBackingVirtualFile);
    myContentPane.setLayout(new BorderLayout());
    myContentPane.add(myImageFileEditor.getComponent(), BorderLayout.CENTER);

    ActionListener listener = actionEvent -> {
      if (actionEvent.getSource() == myRefreshButton) {
        doRefreshScreenshot();
      }
      else if (actionEvent.getSource() == myRotateRightButton) {
        doRotateScreenshot(3);
      }
      else if (actionEvent.getSource() == myRotateLeftButton) {
        doRotateScreenshot(1);
      }
      else if (actionEvent.getSource() == myFrameScreenshotCheckBox
               || actionEvent.getSource() == myDeviceArtCombo) {
        doFrameScreenshot();
      }
      else if (actionEvent.getSource() == myCopyButton) {
        BufferedImage currentImage = myImageFileEditor.getImageEditor().getDocument().getValue();
        CopyPasteManager.getInstance().setContents(new BufferedImageTransferable(currentImage));
        String groupId = NotificationGroup.createIdWithTitle("Screen Capture", AndroidBundle.message("android.ddms.actions.screenshot"));
        Notifications.Bus.notify(new Notification(groupId,
                                                  AndroidBundle.message("android.ddms.actions.screenshot"),
                                                  AndroidBundle.message("android.ddms.actions.screenshot.copied.to.clipboard"),
                                                  NotificationType.INFORMATION), myProject);
      }
    };

    myRefreshButton.addActionListener(listener);
    myRotateRightButton.addActionListener(listener);
    myRotateLeftButton.addActionListener(listener);
    myFrameScreenshotCheckBox.addActionListener(listener);
    myDeviceArtCombo.addActionListener(listener);
    myCopyButton.addActionListener(listener);

    myDeviceArtDescriptors = getDescriptorsToFrame(image);
    String[] titles = new String[myDeviceArtDescriptors.size()];
    for (int i = 0; i < myDeviceArtDescriptors.size(); i++) {
      titles[i] = myDeviceArtDescriptors.get(i).getName();
    }
    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(titles);
    myDeviceArtCombo.setModel(model);

    // Set the default device art descriptor selection
    myDeviceArtCombo.setSelectedIndex(getDefaultDescriptor(myDeviceArtDescriptors, image, deviceModel));

    setModal(false);
    setTitle(AndroidBundle.message("android.ddms.actions.screenshot.title"));
    init();
  }

  /**
   * Makes the screenshot viewer's focus on the image itself when opened, to allow keyboard shortcut copying
   */
  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myImageFileEditor.getComponent();
  }

  // returns the list of descriptors capable of framing the given image
  private static @NotNull List<DeviceArtDescriptor> getDescriptorsToFrame(@NotNull BufferedImage image) {
    double imgAspectRatio = image.getWidth() / (double)image.getHeight();
    ScreenOrientation orientation = imgAspectRatio >= (1 - ImageUtils.EPSILON) ? ScreenOrientation.LANDSCAPE : ScreenOrientation.PORTRAIT;

    List<DeviceArtDescriptor> allDescriptors = DeviceArtDescriptor.getDescriptors(null);
    return ContainerUtil.filter(allDescriptors, descriptor -> descriptor.canFrameImage(image, orientation));
  }

  private static int getDefaultDescriptor(@NotNull List<DeviceArtDescriptor> deviceArtDescriptors, @NotNull BufferedImage image,
                                          @Nullable String deviceModel) {
    int index = -1;

    if (deviceModel != null) {
      index = findDescriptorIndexForProduct(deviceArtDescriptors, deviceModel);
    }

    if (index < 0) {
      // Assume that if the min resolution is > 1280, then we are on a tablet
      String defaultDevice = Math.min(image.getWidth(), image.getHeight()) > 1280 ? "Generic Tablet" : "Generic Phone";
      index = findDescriptorIndexForProduct(deviceArtDescriptors, defaultDevice);
    }

    // If we can't find anything (which shouldn't happen since we should get the Generic Phone/Tablet),
    // default to the first one.
    if (index < 0) {
      index = 0;
    }

    return index;
  }

  private static int findDescriptorIndexForProduct(@NotNull List<DeviceArtDescriptor> descriptors, @NotNull String deviceModel) {
    for (int i = 0; i < descriptors.size(); i++) {
      DeviceArtDescriptor d = descriptors.get(i);
      if (d.getName().equalsIgnoreCase(deviceModel)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected void dispose() {
    myProvider.disposeEditor(myImageFileEditor);
    super.dispose();
  }

  private void doRefreshScreenshot() {
    assert myDevice != null;
    new ScreenshotTask(myProject, myDevice) {
      @Override
      public void onSuccess() {
        String msg = getError();
        if (msg != null) {
          Messages.showErrorDialog(myProject, msg, AndroidBundle.message("android.ddms.actions.screenshot.title"));
          return;
        }

        BufferedImage image = getScreenshot();
        mySourceImageRef.set(image);
        processScreenshot(myFrameScreenshotCheckBox.isSelected(), myRotationQuadrants);
      }
    }.queue();
  }

  private void doRotateScreenshot(int numQuadrants) {
    assert numQuadrants >= 0;
    myRotationQuadrants = (myRotationQuadrants + numQuadrants) % 4;
    processScreenshot(myFrameScreenshotCheckBox.isSelected(), numQuadrants);
  }

  private void doFrameScreenshot() {
    boolean shouldFrame = myFrameScreenshotCheckBox.isSelected();

    myDeviceArtCombo.setEnabled(shouldFrame);

    if (shouldFrame) {
      processScreenshot(true, 0);
    }
    else {
      myDisplayedImageRef.set(mySourceImageRef.get());
      updateEditorImage();
    }
  }

  private void processScreenshot(boolean addFrame, int rotateByQuadrants) {
    DeviceArtDescriptor spec = addFrame ? myDeviceArtDescriptors.get(myDeviceArtCombo.getSelectedIndex()) : null;

    new ImageProcessorTask(myProject, mySourceImageRef.get(), rotateByQuadrants, spec, myBackingVirtualFile) {
      @Override
      public void onSuccess() {
        mySourceImageRef.set(getRotatedImage());
        myDisplayedImageRef.set(getProcessedImage());
        updateEditorImage();
      }
    }.queue();
  }

  private static class ImageProcessorTask extends Task.Backgroundable {
    private final @NotNull BufferedImage mySrcImage;
    private final int myRotationQuadrants;
    private final @Nullable DeviceArtDescriptor myDescriptor;
    private final @Nullable VirtualFile myDestinationFile;

    private BufferedImage myRotatedImage;
    private BufferedImage myProcessedImage;

    public ImageProcessorTask(@Nullable Project project,
                              @NotNull BufferedImage srcImage,
                              int rotateByQuadrants,
                              @Nullable DeviceArtDescriptor descriptor,
                              @Nullable VirtualFile writeToFile) {
      super(project, AndroidBundle.message("android.ddms.screenshot.image.processor.task.title"), false);

      mySrcImage = srcImage;
      myRotationQuadrants = rotateByQuadrants;
      myDescriptor = descriptor;
      myDestinationFile = writeToFile;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myRotatedImage = ImageUtils.rotateByQuadrants(mySrcImage, myRotationQuadrants);

      if (myDescriptor != null) {
        myProcessedImage = DeviceArtPainter.createFrame(myRotatedImage, myDescriptor, false, false);
      }
      else {
        myProcessedImage = myRotatedImage;
      }

      myProcessedImage = ImageUtils.cropBlank(myProcessedImage, null);

      // Update the backing file, this is necessary for operations that read the backing file from the editor,
      // such as: Right click image -> Open in external editor
      if (myDestinationFile != null) {
        File file = VfsUtilCore.virtualToIoFile(myDestinationFile);
        try {
          writePng(myProcessedImage, file);
        }
        catch (IOException e) {
          Logger.getInstance(ImageProcessorTask.class).error("Unexpected error while writing to backing file", e);
        }
      }
    }

    protected BufferedImage getProcessedImage() {
      return myProcessedImage;
    }

    protected BufferedImage getRotatedImage() {
      return myRotatedImage;
    }
  }

  @VisibleForTesting
  void updateEditorImage() {
    BufferedImage image = myDisplayedImageRef.get();
    ImageEditor imageEditor = myImageFileEditor.getImageEditor();

    imageEditor.getDocument().setValue(image);
    pack();

    // After image has updated, set the focus to image to allow keyboard shortcut copying.
    IdeFocusManager.getInstance(myProject).requestFocusInProject(getPreferredFocusedComponent(), myProject);
  }

  private @NotNull FileEditorProvider getImageFileEditorProvider() {
    FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(myProject, myBackingVirtualFile);
    assert providers.length > 0;

    // Note: In case there are multiple providers for image files, we'd prefer to get the bundled
    // image editor, but we don't have access to any of its implementation details so we rely
    // on the editor type id being "images" as defined by ImageFileEditorProvider#EDITOR_TYPE_ID.
    for (FileEditorProvider p : providers) {
      if (p.getEditorTypeId().equals("images")) {
        return p;
      }
    }

    return providers[0];
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected @NonNls @NotNull String getDimensionServiceKey() {
    return SCREENSHOT_VIEWER_DIMENSIONS_KEY;
  }

  @Override
  public @Nullable Object getData(@NonNls @NotNull String dataId) {
    // This is required since the Image Editor's actions are dependent on the context
    // being a ImageFileEditor.
    return PlatformDataKeys.FILE_EDITOR.is(dataId) ? myImageFileEditor : null;
  }

  @Override
  protected @NotNull String getHelpId() {
    return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/am-screenshot.html";
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    getOKAction().putValue(Action.NAME, AndroidBundle.message("android.ddms.screenshot.save.ok.button.text"));
  }

  @Override
  protected void doOKAction() {
    FileSaverDescriptor descriptor =
        new FileSaverDescriptor(AndroidBundle.message("android.ddms.screenshot.save.title"), "", SdkConstants.EXT_PNG);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    VirtualFile baseDir = loadScreenshotPath();
    VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName());
    if (fileWrapper == null) {
      return;
    }

    myScreenshotFile = fileWrapper.getFile();
    try {
      writePng(myDisplayedImageRef.get(), myScreenshotFile);
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject, AndroidBundle.message("android.ddms.screenshot.save.error", e),
                               AndroidBundle.message("android.ddms.actions.screenshot.title"));
      return;
    }

    VirtualFile virtualFile = fileWrapper.getVirtualFile();
    if (virtualFile != null) {
      PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
      properties.setValue(SCREENSHOT_SAVE_PATH_KEY, virtualFile.getParent().getPath());
    }

    super.doOKAction();
  }

  private static void writePng(@NotNull BufferedImage image, @NotNull File outFile) throws IOException {
    ImageWriter pngWriter = getWriter(image, SdkConstants.EXT_PNG);
    if (pngWriter == null) {
      throw new IOException("Failed to find PNG writer");
    }

    //noinspection ResultOfMethodCallIgnored
    outFile.delete();

    try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(outFile)) {
      pngWriter.setOutput(outputStream);

      if (image.getColorModel().getColorSpace() instanceof ICC_ColorSpace) {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
        ImageWriteParam writeParams = pngWriter.getDefaultWriteParam();
        IIOMetadata metadata = pngWriter.getDefaultImageMetadata(type, writeParams);

        ICC_ColorSpace colorSpace = (ICC_ColorSpace)image.getColorModel().getColorSpace();
        byte[] data = deflate(colorSpace.getProfile().getData());

        Node node = metadata.getAsTree("javax_imageio_png_1.0");
        IIOMetadataNode iccp = new IIOMetadataNode("iCCP");
        iccp.setUserObject(data);
        iccp.setAttribute("profileName", Colors.getIccProfileDescription(colorSpace.getProfile()));
        iccp.setAttribute("compressionMethod", "deflate");
        node.appendChild(iccp);

        metadata.setFromTree("javax_imageio_png_1.0", node);

        pngWriter.write(new IIOImage(image, null, metadata));
      }
      else {
        pngWriter.write(image);
      }
      pngWriter.dispose();
    }
  }

  private static byte @NotNull [] deflate(byte @NotNull [] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);

    Deflater deflater = new Deflater();
    deflater.setInput(data);
    deflater.finish();

    byte[] buffer = new byte[4096];
    while (!deflater.finished()) {
      int count = deflater.deflate(buffer);
      out.write(buffer, 0, count);
    }
    data = out.toByteArray();
    return data;
  }

  private static @Nullable ImageWriter getWriter(@NotNull RenderedImage image, @NotNull String format) {
    ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
    Iterator<ImageWriter> iterator = ImageIO.getImageWriters(type, format);
    if (iterator.hasNext()) {
      return iterator.next();
    }
    else {
      return null;
    }
  }

  private @NotNull String getDefaultFileName() {
    Calendar now = Calendar.getInstance();
    String fileName = "%s-%tF-%tH%tM%tS";
    // Add extension to filename on Mac only see: b/38447816.
    return String.format(SystemInfo.isMac ? fileName + ".png" : fileName, myDevice != null ? "device" : "layout", now, now, now, now);
  }

  public @NotNull File getScreenshot() {
    assert myScreenshotFile != null;
    return myScreenshotFile;
  }

  @TestOnly
  @NotNull ImageFileEditor getImageFileEditor() {
    return myImageFileEditor;
  }

  private @Nullable VirtualFile loadScreenshotPath() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    String lastPath = properties.getValue(SCREENSHOT_SAVE_PATH_KEY);

    if (lastPath != null) {
      return LocalFileSystem.getInstance().findFileByPath(lastPath);
    }

    return ProjectUtil.guessProjectDir(myProject);
  }

  private static class BufferedImageTransferable implements Transferable {
    private final @NotNull BufferedImage myImage;

    public BufferedImageTransferable(@NotNull BufferedImage image) {
      myImage = image;
    }

    @Override
    public @NotNull DataFlavor @NotNull [] getTransferDataFlavors() {
      return new DataFlavor[] { DataFlavor.imageFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(@NotNull DataFlavor dataFlavor) {
      return DataFlavor.imageFlavor.equals(dataFlavor);
    }

    @Override
    public @NotNull BufferedImage getTransferData(@NotNull DataFlavor dataFlavor) throws UnsupportedFlavorException {
      if (!DataFlavor.imageFlavor.equals(dataFlavor)) {
        throw new UnsupportedFlavorException(dataFlavor);
      }
      return myImage;
    }
  }
}
