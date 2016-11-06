/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.intentions;

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
@TestMetadata("idea/testData/android/intentions")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class AndroidIntentionTestGenerated extends AbstractAndroidIntentionTest {
    public void testAllFilesPresentInIntentions() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/intentions"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("idea/testData/android/intentions/suppressLint")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class SuppressLint extends AbstractAndroidIntentionTest {
        @TestMetadata("activityMethod.kt")
        public void testActivityMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/activityMethod.kt");
            doTest(fileName);
        }

        @TestMetadata("addToExistingAnnotation.kt")
        public void testAddToExistingAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/addToExistingAnnotation.kt");
            doTest(fileName);
        }

        public void testAllFilesPresentInSuppressLint() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/intentions/suppressLint"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("constructorParameter.kt")
        public void testConstructorParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/constructorParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("destructuringDeclaration.kt")
        public void testDestructuringDeclaration() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/destructuringDeclaration.kt");
            doTest(fileName);
        }

        @TestMetadata("lambdaArgument.kt")
        public void testLambdaArgument() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/lambdaArgument.kt");
            doTest(fileName);
        }

        @TestMetadata("lambdaArgumentProperty.kt")
        public void testLambdaArgumentProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/lambdaArgumentProperty.kt");
            doTest(fileName);
        }

        @TestMetadata("methodParameter.kt")
        public void testMethodParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/methodParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("propertyWithLambda.kt")
        public void testPropertyWithLambda() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/propertyWithLambda.kt");
            doTest(fileName);
        }

        @TestMetadata("simpleProperty.kt")
        public void testSimpleProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/intentions/suppressLint/simpleProperty.kt");
            doTest(fileName);
        }
    }
}
