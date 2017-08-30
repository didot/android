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

package org.jetbrains.kotlin.android.quickfix;

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
@TestMetadata("idea/testData/android/lintQuickfix")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class AndroidLintQuickfixTestGenerated extends AbstractAndroidLintQuickfixTest {
    public void testAllFilesPresentInLintQuickfix() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("idea/testData/android/lintQuickfix/findViewById")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class FindViewById extends AbstractAndroidLintQuickfixTest {
        public void testAllFilesPresentInFindViewById() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix/findViewById"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("nullableType.kt")
        public void testNullableType() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/findViewById/nullableType.kt");
            doTest(fileName);
        }

        @TestMetadata("simple.kt")
        public void testSimple() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/findViewById/simple.kt");
            doTest(fileName);
        }
    }

    @TestMetadata("idea/testData/android/lintQuickfix/parcelable")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Parcelable extends AbstractAndroidLintQuickfixTest {
        public void testAllFilesPresentInParcelable() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix/parcelable"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("missingCreator.kt")
        public void testMissingCreator() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/parcelable/missingCreator.kt");
            doTest(fileName);
        }

        @TestMetadata("noImplementation.kt")
        public void testNoImplementation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/parcelable/noImplementation.kt");
            doTest(fileName);
        }
    }

    @TestMetadata("idea/testData/android/lintQuickfix/requiresApi")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class RequiresApi extends AbstractAndroidLintQuickfixTest {
        public void testAllFilesPresentInRequiresApi() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix/requiresApi"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("annotation.kt")
        public void testAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/annotation.kt");
            doTest(fileName);
        }

        @TestMetadata("companion.kt")
        public void testCompanion() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/companion.kt");
            doTest(fileName);
        }

        @TestMetadata("defaultParameter.kt")
        public void testDefaultParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/defaultParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("extend.kt")
        public void testExtend() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/extend.kt");
            doTest(fileName);
        }

        @TestMetadata("functionLiteral.kt")
        public void testFunctionLiteral() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/functionLiteral.kt");
            doTest(fileName);
        }

        @TestMetadata("inlinedConstant.kt")
        public void testInlinedConstant() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/inlinedConstant.kt");
            doTest(fileName);
        }

        @TestMetadata("method.kt")
        public void testMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/method.kt");
            doTest(fileName);
        }

        @TestMetadata("property.kt")
        public void testProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/property.kt");
            doTest(fileName);
        }

        @TestMetadata("topLevelProperty.kt")
        public void testTopLevelProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/topLevelProperty.kt");
            doTest(fileName);
        }

        @TestMetadata("when.kt")
        public void testWhen() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/requiresApi/when.kt");
            doTest(fileName);
        }
    }

    @TestMetadata("idea/testData/android/lintQuickfix/suppressLint")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class SuppressLint extends AbstractAndroidLintQuickfixTest {
        @TestMetadata("activityMethod.kt")
        public void testActivityMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/activityMethod.kt");
            doTest(fileName);
        }

        @TestMetadata("addToExistingAnnotation.kt")
        public void testAddToExistingAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/addToExistingAnnotation.kt");
            doTest(fileName);
        }

        public void testAllFilesPresentInSuppressLint() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix/suppressLint"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("constructorParameter.kt")
        public void testConstructorParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/constructorParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("destructuringDeclaration.kt")
        public void testDestructuringDeclaration() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/destructuringDeclaration.kt");
            doTest(fileName);
        }

        @TestMetadata("lambdaArgument.kt")
        public void testLambdaArgument() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/lambdaArgument.kt");
            doTest(fileName);
        }

        @TestMetadata("lambdaArgumentProperty.kt")
        public void testLambdaArgumentProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/lambdaArgumentProperty.kt");
            doTest(fileName);
        }

        @TestMetadata("methodParameter.kt")
        public void testMethodParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/methodParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("propertyWithLambda.kt")
        public void testPropertyWithLambda() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/propertyWithLambda.kt");
            doTest(fileName);
        }

        @TestMetadata("simpleProperty.kt")
        public void testSimpleProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/suppressLint/simpleProperty.kt");
            doTest(fileName);
        }
    }

    @TestMetadata("idea/testData/android/lintQuickfix/targetApi")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class TargetApi extends AbstractAndroidLintQuickfixTest {
        public void testAllFilesPresentInTargetApi() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix/targetApi"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("annotation.kt")
        public void testAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/annotation.kt");
            doTest(fileName);
        }

        @TestMetadata("companion.kt")
        public void testCompanion() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/companion.kt");
            doTest(fileName);
        }

        @TestMetadata("defaultParameter.kt")
        public void testDefaultParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/defaultParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("extend.kt")
        public void testExtend() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/extend.kt");
            doTest(fileName);
        }

        @TestMetadata("functionLiteral.kt")
        public void testFunctionLiteral() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/functionLiteral.kt");
            doTest(fileName);
        }

        @TestMetadata("inlinedConstant.kt")
        public void testInlinedConstant() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/inlinedConstant.kt");
            doTest(fileName);
        }

        @TestMetadata("method.kt")
        public void testMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/method.kt");
            doTest(fileName);
        }

        @TestMetadata("property.kt")
        public void testProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/property.kt");
            doTest(fileName);
        }

        @TestMetadata("topLevelProperty.kt")
        public void testTopLevelProperty() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/topLevelProperty.kt");
            doTest(fileName);
        }

        @TestMetadata("when.kt")
        public void testWhen() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetApi/when.kt");
            doTest(fileName);
        }
    }

    @TestMetadata("idea/testData/android/lintQuickfix/targetVersionCheck")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class TargetVersionCheck extends AbstractAndroidLintQuickfixTest {
        public void testAllFilesPresentInTargetVersionCheck() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/android/lintQuickfix/targetVersionCheck"), Pattern.compile("^([\\w\\-_]+)\\.kt$"), TargetBackend.ANY, true);
        }

        @TestMetadata("annotation.kt")
        public void testAnnotation() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/annotation.kt");
            doTest(fileName);
        }

        @TestMetadata("defaultParameter.kt")
        public void testDefaultParameter() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/defaultParameter.kt");
            doTest(fileName);
        }

        @TestMetadata("destructuringDeclaration.kt")
        public void testDestructuringDeclaration() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/destructuringDeclaration.kt");
            doTest(fileName);
        }

        @TestMetadata("expressionBody.kt")
        public void testExpressionBody() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/expressionBody.kt");
            doTest(fileName);
        }

        @TestMetadata("functionLiteral.kt")
        public void testFunctionLiteral() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/functionLiteral.kt");
            doTest(fileName);
        }

        @TestMetadata("getterWIthExpressionBody.kt")
        public void testGetterWIthExpressionBody() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/getterWIthExpressionBody.kt");
            doTest(fileName);
        }

        @TestMetadata("if.kt")
        public void testIf() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/if.kt");
            doTest(fileName);
        }

        @TestMetadata("ifWithBlock.kt")
        public void testIfWithBlock() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/ifWithBlock.kt");
            doTest(fileName);
        }

        @TestMetadata("inlinedConstant.kt")
        public void testInlinedConstant() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/inlinedConstant.kt");
            doTest(fileName);
        }

        @TestMetadata("method.kt")
        public void testMethod() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/method.kt");
            doTest(fileName);
        }

        @TestMetadata("when.kt")
        public void testWhen() throws Exception {
            String fileName = KotlinTestUtils.navigationMetadata("idea/testData/android/lintQuickfix/targetVersionCheck/when.kt");
            doTest(fileName);
        }
    }
}
