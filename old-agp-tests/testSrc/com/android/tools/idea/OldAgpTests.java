/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.junit4.OldAgpSuite;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.LeakCheckerRule;
import com.android.tools.tests.MavenRepoRule;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

/**
 * Runs tests annotated with {@link com.android.testutils.junit4.OldAgpTest}.
 */
@RunWith(OldAgpSuite.class)
@JarTestSuiteRunner.ExcludeClasses({OldAgpTests.class})
public class OldAgpTests extends IdeaTestSuiteBase {

  @ClassRule public static LeakCheckerRule checker = new LeakCheckerRule();

  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  @ClassRule public static MavenRepoRule mavenRepos = MavenRepoRule.fromTestSuiteSystemProperty();
}
