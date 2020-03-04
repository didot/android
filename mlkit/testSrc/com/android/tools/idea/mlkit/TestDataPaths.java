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
package com.android.tools.idea.mlkit;

import com.android.testutils.TestUtils;

/**
 * Constants for ml model binding test data locations.
 */
public class TestDataPaths {
  public static final String TEST_DATA_ROOT = TestUtils.getWorkspaceFile("tools/adt/idea/mlkit/testData").getPath();

  public static final String PROJECT_WITH_TWO_MODULES_BUT_ONLY_ONE_ENABLED = "projects/projectWithTwoModulesButOnlyOneEnabled";
}
