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
package com.android.tools.idea.avdmanager;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.*;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Information on a system image. Used internally by the avd manager.
 */
public final class SystemImageDescription {
  private IAndroidTarget myTarget;
  private ISystemImage mySystemImage;
  private RemotePackage myRemotePackage;

  public SystemImageDescription(IAndroidTarget target, ISystemImage systemImage) {
    myTarget = target;
    mySystemImage = systemImage;
  }

  public SystemImageDescription(RemotePackage remotePackage, IAndroidTarget target) {
    this.myRemotePackage = remotePackage;

    assert hasSystemImage(remotePackage);
    mySystemImage = new RemoteSystemImage(remotePackage);
    myTarget = target;
  }

  static boolean hasSystemImage(RepoPackage p) {
    TypeDetails details = p.getTypeDetails();
    if (!(details instanceof DetailsTypes.ApiDetailsType)) {
      return false;
    }
    int apiLevel = ((DetailsTypes.ApiDetailsType)details).getApiLevel();
    if (details instanceof DetailsTypes.SysImgDetailsType) {
      return true;
    }
    // Platforms up to 13 included a bundled system image
    if (details instanceof DetailsTypes.PlatformDetailsType && apiLevel <= 13) {
      return true;
    }
    // Google APIs addons up to 18 included a bundled system image
    if (details instanceof DetailsTypes.AddonDetailsType && ((DetailsTypes.AddonDetailsType)details).getVendor().getId().equals("google") &&
        ((DetailsTypes.AddonDetailsType)details).getTag().getId().equals("google_apis") && apiLevel <= 18) {
      return true;
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myTarget, mySystemImage, myRemotePackage);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SystemImageDescription)) {
      return false;
    }
    SystemImageDescription other = (SystemImageDescription)obj;
    return Objects.equal(myTarget, other.myTarget) && Objects.equal(mySystemImage, other.mySystemImage) &&
           Objects.equal(myRemotePackage, other.myRemotePackage);
  }

  @Nullable
  public AndroidVersion getVersion() {
    return mySystemImage.getAndroidVersion();
  }

  public RepoPackage getRemotePackage() {
    return myRemotePackage;
  }

  public boolean isRemote() {
    return myRemotePackage != null;
  }

  @NotNull
  public String getAbiType() {
    return mySystemImage.getAbiType();
  }

  @NotNull
  public IdDisplay getTag() {
    return mySystemImage.getTag();
  }

  public String getName() {
    if (getVersion() != null) {
      return String.format("Android %s", SdkVersionInfo.getVersionString(getVersion().getFeatureLevel()));
    }
    return "Unknown platform";
  }

  public String getVendor() {
    if (myTarget != null) {
      return myTarget.getVendor();
    }
    if (mySystemImage != null && mySystemImage.getAddonVendor() != null) {
      return mySystemImage.getAddonVendor().getDisplay();
    }
    return "";
  }

  public String getVersionName() {
    return SdkVersionInfo.getVersionString(mySystemImage.getAndroidVersion().getApiLevel());
  }

  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @Nullable
  Revision getRevision() {
    return mySystemImage.getRevision();
  }

  public File[] getSkins() {
    return mySystemImage.getSkins();
  }

  private static class RemoteSystemImage implements ISystemImage {
    private final RemotePackage myRemotePackage;
    private final IdDisplay myTag;
    private final IdDisplay myVendor;
    private final String myAbi;
    private final AndroidVersion myAndroidVersion;

    public RemoteSystemImage(RemotePackage p) {
      myRemotePackage = p;

      TypeDetails details = myRemotePackage.getTypeDetails();
      assert details instanceof DetailsTypes.ApiDetailsType;
      myAndroidVersion = DetailsTypes.getAndroidVersion((DetailsTypes.ApiDetailsType)details);

      IdDisplay tag = null;
      IdDisplay vendor = null;
      String abi = "armeabi";

      if (details instanceof DetailsTypes.AddonDetailsType) {
        tag = ((DetailsTypes.AddonDetailsType)details).getTag();
        vendor = ((DetailsTypes.AddonDetailsType)details).getVendor();
      }
      if (details instanceof DetailsTypes.SysImgDetailsType) {
        tag = ((DetailsTypes.SysImgDetailsType)details).getTag();
        vendor = ((DetailsTypes.SysImgDetailsType)details).getVendor();
        abi = ((DetailsTypes.SysImgDetailsType)details).getAbi();      }
      myTag = tag != null ? tag : SystemImage.DEFAULT_TAG;
      myVendor = vendor;
      myAbi = abi;

    }

    @NonNull
    @Override
    public File getLocation() {
      assert false : "Can't get location for remote image";
      return new File("");
    }

    @NonNull
    @Override
    public IdDisplay getTag() {
      return myTag;
    }

    @com.android.annotations.Nullable
    @Override
    public IdDisplay getAddonVendor() {
      return myVendor;
    }

    @NonNull
    @Override
    public String getAbiType() {
      return myAbi;
    }

    @NonNull
    @Override
    public File[] getSkins() {
      return new File[0];
    }

    @NonNull
    @Override
    public Revision getRevision() {
      return myRemotePackage.getVersion();
    }

    @Override
    public AndroidVersion getAndroidVersion() {
      return myAndroidVersion;
    }

    @Override
    public int compareTo(ISystemImage o) {
      if (o instanceof RemoteSystemImage) {
        return myRemotePackage.compareTo(((RemoteSystemImage)o).myRemotePackage);
      }
      return 1;
    }
  }

}
