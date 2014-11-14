/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wizard step for specifying JDK location.
 */
public class JdkLocationStep extends FirstRunWizardStep {
  private static final String MAC_JDKS_DIR = "/Library/Java/JavaVirtualMachines/";
  private static final String MAC_JDK_CONTENT_PATH = "/Contents/Home";
  private static final String WINDOWS_JDKS_DIR = "C:\\Program Files\\Java";
  private static final String JDK_URL = "http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html";

  private final ScopedStateStore.Key<String> myPathKey;
  private JPanel myContents;
  private TextFieldWithBrowseButton myJdkPath;
  private JButton myDownloadPageLink;
  private JLabel myError;
  private JButton myDetectButton;
  private JLabel myDetectLabel;
  // Show errors only after the user touched the value
  private boolean myUserInput = false;

  public JdkLocationStep(ScopedStateStore.Key<String> pathKey) {
    super("Java Settings");
    myPathKey = pathKey;
    myDownloadPageLink.setText(getLinkText());
    WelcomeUIUtils.makeButtonAHyperlink(myDownloadPageLink, JDK_URL);
    myDownloadPageLink.getParent().invalidate();
    setComponent(myContents);
    myError.setForeground(JBColor.red);
    FileChooserDescriptor folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myJdkPath.addBrowseFolderListener("Select JDK Location", "Select compatible JDK location", null, folderDescriptor);
    myError.setText(null);
    // Does not seem like there's reliable default for JDK locations on Linux...
    // Hence, "Detect" is only available on Windows/Mac
    if (SystemInfo.isMac || SystemInfo.isWindows) {
      myDetectButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ProgressManager.getInstance().run(new DetectJdkTask());
        }
      });
    }
    else {
      myDetectButton.setVisible(false);
      myDetectLabel.setVisible(false);
    }
  }

  private static String getLinkText() {
    // TODO ARM support?
    if (SystemInfo.isMac) {
      return "Mac OS X x64";
    }
    else if (SystemInfo.isLinux) {
      return SystemInfo.is32Bit ? "Linux x86" : "Linux x64";
    }
    else if (SystemInfo.isWindows) {
      return SystemInfo.is32Bit ? "Windows x86" : "Windows x64";
    }
    else {
      return SystemInfo.OS_NAME;
    }
  }

  private static boolean isJdk7(String path) {
    String jdkVersion = JavaSdk.getJdkVersion(path);
    if (jdkVersion != null) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdkVersion);
      if (version != null && !version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static String validateJdkLocation(@Nullable String location) {
    if (StringUtil.isEmpty(location)) {
      return "Path is empty";
    }
    if (!JdkUtil.checkForJdk(new File(location))) {
      return "Path specified is not a valid JDK location";
    }
    if (!isJdk7(location)) {
      return "JDK 7.0 or newer is required";
    }
    return null;
  }

  @NotNull
  private static Iterable<String> getCandidatePaths() {
    if (SystemInfo.isMac) {
      return getMacCandidateJdks();
    }
    else if (SystemInfo.isWindows) {
      return getWindowsCandidateJdks();
    }
    else {
      // No default location for Linux...
      return Collections.emptyList();
    }
  }

  @NotNull
  private static Iterable<String> getMacCandidateJdks() {
    // See http://docs.oracle.com/javase/7/docs/webnotes/install/mac/mac-jdk.html
    return getCandidatePaths(MAC_JDKS_DIR, MAC_JDK_CONTENT_PATH);
  }

  @NotNull
  private static Iterable<String> getWindowsCandidateJdks() {
    // See http://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html
    return getCandidatePaths(WINDOWS_JDKS_DIR, "");
  }

  private static Iterable<String> getCandidatePaths(String basedir, final String suffix) {
    final File location = new File(basedir);
    if (location.isDirectory()) {
      return Iterables.transform(Arrays.asList(location.list()), new Function<String, String>() {
        @Override
        public String apply(@Nullable String dir) {
          return new File(location, dir + suffix).getAbsolutePath();
        }
      });
    }
    return Collections.emptyList();
  }

  @Override
  public boolean validate() {
    String path = myState.get(myPathKey);
    if (!StringUtil.isEmpty(path)) {
      myUserInput = true;
    }
    String message = validateJdkLocation(path);
    if (myUserInput) {
      setErrorHtml(message);
    }
    return StringUtil.isEmpty(message);
  }

  @Override
  public void init() {
    register(myPathKey, myJdkPath);
  }

  @Override
  public boolean isStepVisible() {
    InstallerData installerData = InstallerData.get(myState);
    return !installerData.hasValidJdkLocation();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myJdkPath;
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return myError;
  }

  private class DetectJdkTask extends Task.Modal {
    private final AtomicBoolean myCancelled = new AtomicBoolean(false);
    private String myPath = null;

    public DetectJdkTask() {
      super(null, "Detect JDK", true);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      String topVersion = null;
      String chosenPath = null;
      for (String path : getCandidatePaths()) {
        if (myCancelled.get()) {
          return;
        }
        if (StringUtil.isEmpty(validateJdkLocation(path))) {
          String version = JavaSdk.getInstance().getVersionString(path);
          if (topVersion == null || version == null || topVersion.compareTo(version) < 0) {
            topVersion = version;
            chosenPath = path;
          }
        }
      }
      myPath = chosenPath;
    }

    @Override
    public void onSuccess() {
      if (myPath != null) {
        myState.put(myPathKey, myPath);
      }
      super.onSuccess();
    }

    @Override
    public void onCancel() {
      myCancelled.set(true);
    }
  }
}
