// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class ResourceFileData {
  // order matters because of id assigning in R.java
  private final List<ResourceEntry> myValueResources;

  private long myTimestamp;

  public ResourceFileData() {
    this(new ArrayList<>(), 0);
  }

  public ResourceFileData(@NotNull List<ResourceEntry> valueResources, long timestamp) {
    myValueResources = valueResources;
    myTimestamp = timestamp;
  }

  @NotNull
  public List<ResourceEntry> getValueResources() {
    return myValueResources;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public void setTimestamp(long timestamp) {
    myTimestamp = timestamp;
  }

  public void addValueResource(@NotNull ResourceEntry entry) {
    myValueResources.add(entry);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResourceFileData data = (ResourceFileData)o;

    if (myTimestamp != data.myTimestamp) return false;
    if (!myValueResources.equals(data.myValueResources)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myValueResources.hashCode();
    result = 31 * result + (int)(myTimestamp ^ (myTimestamp >>> 32));
    return result;
  }
}
