/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.annotator;

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
@TestMetadata("idea/testData/android/gutterIcon")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class AndroidGutterIconTestGenerated extends AbstractAndroidGutterIconTest {
    public void testAllFilesPresentInGutterIcon() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/gutterIcon"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("color.kt")
    public void testColor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/gutterIcon/color.kt");
        doTest(fileName);
    }

    @TestMetadata("drawable.kt")
    public void testDrawable() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/gutterIcon/drawable.kt");
        doTest(fileName);
    }

    @TestMetadata("mipmap.kt")
    public void testMipmap() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/gutterIcon/mipmap.kt");
        doTest(fileName);
    }

    @TestMetadata("relatedFiles.kt")
    public void testRelatedFiles() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/gutterIcon/relatedFiles.kt");
        doTest(fileName);
    }

    @TestMetadata("systemColor.kt")
    public void testSystemColor() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/gutterIcon/systemColor.kt");
        doTest(fileName);
    }

    @TestMetadata("systemDrawable.kt")
    public void testSystemDrawable() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/gutterIcon/systemDrawable.kt");
        doTest(fileName);
    }

    @TestMetadata("idea/testData/android/gutterIcon/res")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Res extends AbstractAndroidGutterIconTest {
        public void testAllFilesPresentInRes() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/gutterIcon/res"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
        }
    }
}
