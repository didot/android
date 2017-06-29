/*
 * Copyright 2010-2017 JetBrains s.r.o.
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
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class ParcelCheckerTestGenerated extends AbstractParcelCheckerTest {
    public void testAllFilesPresentInChecker() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("constructors.kt")
    public void testConstructors() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/constructors.kt");
        doTest(fileName);
    }

    @TestMetadata("customCreator.kt")
    public void testCustomCreator() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/customCreator.kt");
        doTest(fileName);
    }

    @TestMetadata("customWriteToParcel.kt")
    public void testCustomWriteToParcel() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/customWriteToParcel.kt");
        doTest(fileName);
    }

    @TestMetadata("modality.kt")
    public void testModality() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/modality.kt");
        doTest(fileName);
    }

    @TestMetadata("notMagicParcel.kt")
    public void testNotMagicParcel() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/notMagicParcel.kt");
        doTest(fileName);
    }

    @TestMetadata("properties.kt")
    public void testProperties() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/properties.kt");
        doTest(fileName);
    }

    @TestMetadata("simple.kt")
    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/simple.kt");
        doTest(fileName);
    }

    @TestMetadata("withoutParcelableSupertype.kt")
    public void testWithoutParcelableSupertype() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/withoutParcelableSupertype.kt");
        doTest(fileName);
    }

    @TestMetadata("wrongAnnotationTarget.kt")
    public void testWrongAnnotationTarget() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("plugins/android-extensions/android-extensions-idea/testData/android/parcel/checker/wrongAnnotationTarget.kt");
        doTest(fileName);
    }
}
