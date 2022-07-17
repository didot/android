package org.jetbrains.android.sdk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidNewSdkDialog extends DialogWrapper {
  private JPanel myContentPanel;
  private JComboBox myInternalJdkComboBox;
  private JComboBox myBuildTargetComboBox;

  protected AndroidNewSdkDialog(@Nullable Project project,
                                @NotNull List<String> javaSdkNames,
                                @NotNull String selectedJavaSdkName,
                                @NotNull List<String> targetNames,
                                @NotNull String selectedTargetName) {
    super(project);
    setTitle("Create New Android SDK");
    myInternalJdkComboBox.setModel(new CollectionComboBoxModel(javaSdkNames, selectedJavaSdkName));
    myBuildTargetComboBox.setModel(new CollectionComboBoxModel(targetNames, selectedTargetName));

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public int getSelectedJavaSdkIndex() {
    return myInternalJdkComboBox.getSelectedIndex();
  }

  public int getSelectedTargetIndex() {
    return myBuildTargetComboBox.getSelectedIndex();
  }
}
