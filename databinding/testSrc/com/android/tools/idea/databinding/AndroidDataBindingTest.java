/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.finders.DataBindingScopeEnlarger;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests symbol resolution in data binding expressions in layout XML files. The code being tested is located in
 * {@link org.jetbrains.android.dom.converters.DataBindingConverter} and
 * {@link com.android.tools.idea.lang.databinding.DataBindingXmlReferenceContributor}.
 */
@RunWith(Parameterized.class)
public class AndroidDataBindingTest {
  private static final String DUMMY_CLASS_QNAME = "p1.p2.DummyClass";

  @NotNull
  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @NotNull
  private final DataBindingMode myDataBindingMode;

  @Parameters(name = "{0}")
  public static List<DataBindingMode> getModes() {
    return Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX);
  }

  public AndroidDataBindingTest(@NotNull DataBindingMode mode) {
    myDataBindingMode = mode;
  }

  @Before
  public void setUp() {
    JavaCodeInsightTestFixture fixture = getFixture();

    fixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/databinding");
    fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML);
    AndroidFacet androidFacet = FacetManager.getInstance(myProjectRule.module).getFacetByType(AndroidFacet.ID);
    ModuleDataBinding.getInstance(androidFacet).setMode(myDataBindingMode);
  }

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a {@link JavaCodeInsightTestFixture} because our
   * {@link AndroidProjectRule} is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private JavaCodeInsightTestFixture getFixture() {
    return ((JavaCodeInsightTestFixture)myProjectRule.fixture);
  }

  private void copyLayout(String name) {
    getFixture().copyFileToProject("res/layout/" + name + ".xml");
  }

  private void copyClass(String qName) {
    String asPath = qName.replace(".", "/");
    getFixture().copyFileToProject("src/" + asPath + ".java");
  }

  private void copyClass(String qName, String targetQName) {
    String source = qName.replace(".", "/");
    String dest = targetQName.replace(".", "/");
    getFixture().copyFileToProject("src/" + source + ".java", "src/" + dest + ".java");
  }

  private static void assertMethod(PsiClass aClass, String name, String returnType, String... parameters) {
    PsiMethod[] methods = aClass.findMethodsByName(name, true);
    assertEquals(1, methods.length);
    PsiMethod method = methods[0];
    assertNotNull(method.getReturnType());
    assertEquals(returnType, method.getReturnType().getCanonicalText());
    PsiParameterList parameterList = method.getParameterList();
    assertEquals(parameters.length, parameterList.getParametersCount());
    for (String parameterQName : parameters) {
      assertEquals(parameterQName, parameterList.getParameters()[0].getType().getCanonicalText());
    }
  }

  @Test
  @RunsInEdt
  public void testSimpleVariableResolution() {
    copyLayout("basic_binding");
    copyClass(DUMMY_CLASS_QNAME);

    PsiClass aClass = getFixture().findClass("p1.p2.databinding.BasicBindingBinding");
    assertNotNull(aClass);

    assertNotNull(aClass.findFieldByName("view1", false));
    assertMethod(aClass, "setDummy", "void", DUMMY_CLASS_QNAME);
    assertMethod(aClass, "getDummy", DUMMY_CLASS_QNAME);
  }

  /**
   * Tests symbol resolution in the scenario described in https://issuetracker.google.com/65467760.
   */
  @Test
  @RunsInEdt
  public void testPropertyResolution() {
    if (myDataBindingMode == DataBindingMode.SUPPORT) {
      copyClass("p1.p2.ClassWithBindableProperty");
    } else {
      copyClass("p1.p2.ClassWithBindableProperty_androidx", "p1.p2.ClassWithBindableProperty");
    }
    getFixture().configureByFile("res/layout/data_binding_property_reference.xml");

    PsiElement element = getFixture().getElementAtCaret();
    assertTrue(element instanceof PsiMethod);
    assertEquals("getProperty", ((PsiMethod)element).getName());
  }

  @Test
  @RunsInEdt
  public void testImportResolution() {
    copyLayout("import_variable");
    copyClass(DUMMY_CLASS_QNAME);

    PsiClass aClass = getFixture().findClass("p1.p2.databinding.ImportVariableBinding");
    assertNotNull(aClass);

    assertMethod(aClass, "setDummy", "void", DUMMY_CLASS_QNAME);
    assertMethod(aClass, "getDummy", DUMMY_CLASS_QNAME);

    assertMethod(aClass, "setDummyList", "void", "java.util.List<" + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyList", "java.util.List<" + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyMap", "void", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyMap", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyArray", "void", DUMMY_CLASS_QNAME + "[]");
    assertMethod(aClass, "getDummyArray", DUMMY_CLASS_QNAME + "[]");

    assertMethod(aClass, "setDummyMultiDimArray", "void", DUMMY_CLASS_QNAME + "[][][]");
    assertMethod(aClass, "getDummyMultiDimArray", DUMMY_CLASS_QNAME + "[][][]");
  }

  @Test
  @RunsInEdt
  public void testImportAliasResolution() {
    copyLayout("import_via_alias");
    copyClass(DUMMY_CLASS_QNAME);

    PsiClass aClass = getFixture().findClass("p1.p2.databinding.ImportViaAliasBinding");
    assertNotNull(aClass);

    assertMethod(aClass, "setDummy", "void", DUMMY_CLASS_QNAME);
    assertMethod(aClass, "getDummy", DUMMY_CLASS_QNAME);

    assertMethod(aClass, "setDummyList", "void", "java.util.List<" + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyList", "java.util.List<" + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyMap", "void", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyMap", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyMap2", "void", "java.util.Map<" + DUMMY_CLASS_QNAME + ",java.lang.String>");
    assertMethod(aClass, "getDummyMap2", "java.util.Map<" + DUMMY_CLASS_QNAME + ",java.lang.String>");

    assertMethod(aClass, "setDummyArray", "void", DUMMY_CLASS_QNAME + "[]");
    assertMethod(aClass, "getDummyArray", DUMMY_CLASS_QNAME + "[]");

    assertMethod(aClass, "setDummyMultiDimArray", "void", DUMMY_CLASS_QNAME + "[][][]");
    assertMethod(aClass, "getDummyMultiDimArray", DUMMY_CLASS_QNAME + "[][][]");
  }

  @Test
  @RunsInEdt
  public void testDataBindingComponentContainingFileIsNotNull() {
    // Random class in the current module; we just need something so we can resolve it, triggering
    // a data binding scope-enlargement behind the scenes
    copyClass(DUMMY_CLASS_QNAME);
    PsiClass aClass = getFixture().findClass(DUMMY_CLASS_QNAME);
    assertNotNull(aClass);
    GlobalSearchScope moduleScope = aClass.getResolveScope();

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(myProjectRule.getProject());
    PsiClass foundClass;
    if (myDataBindingMode == DataBindingMode.SUPPORT) {
      foundClass = javaPsiFacade.findClass("android.databinding.DataBindingComponent", moduleScope);
    }
    else {
      foundClass = javaPsiFacade.findClass("androidx.databinding.DataBindingComponent", moduleScope);
    }
    assertNotNull(foundClass);
    assertNotNull(foundClass.getContainingFile());
  }
}
