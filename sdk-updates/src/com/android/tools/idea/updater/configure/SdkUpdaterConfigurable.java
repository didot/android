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
package com.android.tools.idea.updater.configure;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.sdk.wizard.v2.SdkQuickfixUtils;
import com.android.tools.idea.sdkv2.RepoProgressIndicatorAdapter;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AncestorListenerAdapter;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configurable for the Android SDK Manager.
 * TODO(jbakermalone): implement the searchable interface more completely. Unfortunately it seems that this involves not using forms
 * for the UI?
 */
public class SdkUpdaterConfigurable implements SearchableConfigurable {
  private SdkUpdaterConfigPanel myPanel;
  private AndroidSdkHandler mySdkHandler;
  private String myCurrentChannel;
  private Runnable myChannelChangedCallback;

  @NotNull
  @Override
  public String getId() {
    return "AndroidSdkUpdater";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Android SDK Updater";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myChannelChangedCallback = new Runnable() {
      @Override
      public void run() {
        String channel = StudioSettingsController.getInstance().getChannel();
        if (!Objects.equal(channel, myCurrentChannel)) {
          myCurrentChannel = channel;
          myPanel.refresh();
        }
      }
    };
    mySdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    myPanel =
      new SdkUpdaterConfigPanel(mySdkHandler, myChannelChangedCallback, new StudioDownloader(), StudioSettingsController.getInstance());
    JComponent component = myPanel.getComponent();
    component.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        myChannelChangedCallback.run();
      }
    });

    return myPanel.getComponent();
  }

  private RepoManager getRepoManager() {
    return mySdkHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass()));
  }

  @Override
  public boolean isModified() {
    if (myPanel.isModified()) {
      return true;
    }

    // If the user modifies the channel, comes back here, and then applies the change, we want to be able to update
    // right away. Thus we mark ourselves as modified if UpdateSettingsConfigurable is modified, and then reload in
    // apply().
    DataContext dataContext = DataManager.getInstance().getDataContext(myPanel.getComponent());
    Settings data = Settings.KEY.getData(dataContext);
    if (data != null) {
      Configurable updatesConfigurable = data.find("preferences.updates");
      if (updatesConfigurable != null) {
        return updatesConfigurable.isModified();
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.saveSources();

    HtmlBuilder message = new HtmlBuilder();
    message.openHtmlBody();
    final List<LocalPackage> toDelete = Lists.newArrayList();
    final Map<RemotePackage, UpdatablePackage> requestedPackages = Maps.newHashMap();
    for (NodeStateHolder holder : myPanel.getStates()) {
      if (holder.getState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        if (holder.getPkg().hasLocal()) {
          toDelete.add(holder.getPkg().getLocal());
        }
      }
      else if (holder.getState() == NodeStateHolder.SelectedState.INSTALLED &&
               (holder.getPkg().isUpdate() || !holder.getPkg().hasLocal())) {
        UpdatablePackage pkg = holder.getPkg();
        requestedPackages.put(pkg.getRemote(), pkg);
      }
    }
    boolean found = false;
    if (!toDelete.isEmpty()) {
      found = true;
      message.add("The following components will be deleted: \n");
      message.beginList();
      for (LocalPackage item : toDelete) {
        message.listItem().add(item.getDisplayName()).add(", Revision: ")
          .add(item.getVersion().toString());
      }
      message.endList();
    }
    if (!requestedPackages.isEmpty()) {
      found = true;
      message.add("The following components will be installed: \n");
      message.beginList();
      Multimap<RemotePackage, RemotePackage> dependencies = HashMultimap.create();
      com.android.repository.api.ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
      RepositoryPackages packages = mySdkHandler.getSdkManager(progress).getPackages();
      for (RemotePackage item : requestedPackages.keySet()) {
        List<RemotePackage> packageDependencies = InstallerUtil.computeRequiredPackages(ImmutableList.of(item), packages, progress);
        if (packageDependencies == null) {
          Messages.showErrorDialog((Project)null, "Unable to resolve dependencies for " + item.getDisplayName(), "Dependency Error");
          throw new ConfigurationException("Unable to resolve dependencies.");
        }
        for (RemotePackage dependency : packageDependencies) {
          dependencies.put(dependency, item);
        }
        message.listItem().add(String.format("%1$s %2$s %3$s", item.getDisplayName(),
                                             item.getTypeDetails() instanceof DetailsTypes.ApiDetailsType ? "revision" : "version",
                                             item.getVersion()));
      }
      for (RemotePackage dependency : dependencies.keySet()) {
        if (requestedPackages.containsKey(dependency)) {
          continue;
        }
        Set<RemotePackage> requests = Sets.newHashSet(dependencies.get(dependency));
        requests.remove(dependency);
        if (!requests.isEmpty()) {
          message.listItem().add(dependency.getDisplayName())
            .add(" (Required by ");
          Iterator<RemotePackage> requestIter = requests.iterator();
          message.add(requestIter.next().getDisplayName());
          while (requestIter.hasNext()) {
            message.add(", ").add(requestIter.next().getDisplayName());
          }
          message.add(")");
        }
      }
      message.endList();
    }
    message.closeHtmlBody();
    if (found) {
      if (Messages.showOkCancelDialog((Project)null, message.getHtml(), "Confirm Change", AllIcons.General.Warning) == Messages.OK) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          @Override
          public void run() {
            ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
            com.android.repository.api.ProgressIndicator repoProgress = new RepoProgressIndicatorAdapter(progress);
            FileOp fop = FileOpUtils.create();
            for (LocalPackage item : toDelete) {
              AndroidSdkHandler.findBestInstaller(item).uninstall(item, repoProgress, getRepoManager(), fop);
            }
          }
        }, "Uninstalling", false, null, myPanel.getComponent());
        if (!requestedPackages.isEmpty()) {
          ModelWizardDialog dialog = SdkQuickfixUtils.createDialog(myPanel.getComponent(), requestedPackages.values());
          if (dialog != null) {
            dialog.show();
          }
        }

        myPanel.refresh();
      }
      else {
        throw new ConfigurationException("Installation was canceled.");
      }
    }
    else {
      // We didn't have any changes, so just reload (maybe the channel changed).
      myChannelChangedCallback.run();
    }
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
  }
}
