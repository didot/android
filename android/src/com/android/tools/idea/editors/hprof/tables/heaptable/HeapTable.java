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
package com.android.tools.idea.editors.hprof.tables.heaptable;

import com.android.tools.idea.editors.hprof.tables.HprofTable;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstancesTreeTable;
import org.jetbrains.annotations.NotNull;

public class HeapTable extends HprofTable {
  @NotNull private InstancesTreeTable myInstancesTreeTable;

  public HeapTable(@NotNull HeapTableModel model, @NotNull InstancesTreeTable instancesTreeTable) {
    super(model);
    myInstancesTreeTable = instancesTreeTable;
  }

  @Override
  public void notifyDominatorsComputed() {
    super.notifyDominatorsComputed();
    myInstancesTreeTable.notifyDominatorsComputed();
  }
}
