/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.InnerTestClasses;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.JetTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("plugins/android-idea-plugin/testData/android/completion")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class AndroidCompletionTestGenerated extends AbstractAndroidCompletionTest {
    public void testAllFilesPresentInCompletion() throws Exception {
        JetTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("plugins/android-idea-plugin/testData/android/completion"), Pattern.compile("^([^\\.]+)$"), false);
    }

    @TestMetadata("fqNameInAttr")
    public void testFqNameInAttr() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/fqNameInAttr/");
        doTest(fileName);
    }

    @TestMetadata("fqNameInAttrFragment")
    public void testFqNameInAttrFragment() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/fqNameInAttrFragment/");
        doTest(fileName);
    }

    @TestMetadata("fqNameInTag")
    public void testFqNameInTag() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/fqNameInTag/");
        doTest(fileName);
    }

    @TestMetadata("fqNameInTagFragment")
    public void testFqNameInTagFragment() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/fqNameInTagFragment/");
        doTest(fileName);
    }

    @TestMetadata("multiFile")
    public void testMultiFile() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/multiFile/");
        doTest(fileName);
    }

    @TestMetadata("multiFileFragment")
    public void testMultiFileFragment() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/multiFileFragment/");
        doTest(fileName);
    }

    @TestMetadata("propertiesSimple")
    public void testPropertiesSimple() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/propertiesSimple/");
        doTest(fileName);
    }

    @TestMetadata("propertiesSimpleFragment")
    public void testPropertiesSimpleFragment() throws Exception {
        String fileName = JetTestUtils.navigationMetadata("plugins/android-idea-plugin/testData/android/completion/propertiesSimpleFragment/");
        doTest(fileName);
    }
}
