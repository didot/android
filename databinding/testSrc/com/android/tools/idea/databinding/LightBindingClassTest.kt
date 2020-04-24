/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding

import com.android.resources.ResourceUrl
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.LayoutBindingTypeUtil
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests that verify that navigating between data binding components.
 */
@RunsInEdt
class LightBindingClassTest {
  private val projectRule = AndroidProjectRule.onDisk()

  // We want to run tests on EDT, but we also need to make sure the project rule is not initialized on EDT.
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val facet
    get() = projectRule.module.androidFacet!!

  private val project
    get() = projectRule.project

  private fun insertXml(psiFile: PsiFile, offset: Int, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(offset, xml)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun deleteXml(psiFile: PsiFile, range: TextRange) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(range.startOffset, range.endOffset)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun updateXml(psiFile: PsiFile, range: TextRange, xml: String) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(psiFile)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.replaceString(range.startOffset, range.endOffset, xml)
      documentManager.commitDocument(document)
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun findChild(psiFile: PsiFile, clazz: Class<out XmlElement>, predicate: (XmlTag) -> Boolean): Array<XmlTag> {
    return PsiTreeUtil.findChildrenOfType(psiFile, clazz).filterIsInstance<XmlTag>().filter(predicate).toTypedArray()
  }

  private fun verifyLightFieldsMatchXml(fields: List<PsiField>, vararg tags: XmlTag) {
    val fieldIds = fields.map(PsiField::getName).toList()
    val tagIds = tags
      .map { tag -> tag.getAttribute("android:id")!!.value!! }
      .map { id -> DataBindingUtil.convertAndroidIdToJavaFieldName(ResourceUrl.parse(id)!!.name) }
      .toList()
    assertThat(fieldIds).isEqualTo(tagIds)
  }

  @Before
  fun setUp() {
    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())

    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = DataBindingMode.ANDROIDX
  }

  @Test
  fun lightClassConstructorIsPrivate() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.constructors).hasLength(1)
    assertThat(binding.constructors.first().hasModifier(JvmModifier.PRIVATE))
  }

  @Test
  fun lightClassContainsFieldByIndex() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    val tags = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)
  }

  @Test
  fun androidIdsWithDotSyntaxAreSupported() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test.id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val field = binding.fields.first { it.name == "testId" }
    assertThat(field.type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("android.view.LinearLayout", context))
  }

  @Test
  fun addingAndRemovingLayoutFilesUpdatesTheCache() {
    val firstFile = fixture.addFileToProject("res/layout/activity_first.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class FirstActivity {}")

    // This find forces a cache to be initialized
    val firstBinding = fixture.findClass("test.db.databinding.ActivityFirstBinding", context) as LightBindingClass?
    assertThat(firstBinding).isNotNull()

    fixture.addFileToProject("res/layout/activity_second.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())

    // This second file should be findable, meaning the cache was updated
    val secondBinding = fixture.findClass("test.db.databinding.ActivitySecondBinding", context) as LightBindingClass?
    assertThat(secondBinding).isNotNull()

    // Make sure alternate layouts are found by searching for its BindingImpl
    assertThat(fixture.findClass("test.db.databinding.ActivitySecondBindingLandImpl", context)).isNull()

    fixture.addFileToProject("res/layout-land/activity_second.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())

    assertThat(fixture.findClass("test.db.databinding.ActivitySecondBindingLandImpl", context)).isNotNull()

    // We also should be returning the same "ActivityFirstBinding" light class, not a new instance
    assertThat(fixture.findClass("test.db.databinding.ActivityFirstBinding", context)).isEqualTo(firstBinding)

    WriteCommandAction.runWriteCommandAction(project) { firstFile.delete() }
    assertThat(fixture.findClass("test.db.databinding.ActivityFirstBinding", context)).isNull()
  }

  @Test
  fun addViewRefreshesLightClassFields() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).hasLength(1)

      val tag = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }[0]
      insertXml(file, tag.textRange.endOffset, """
        <LinearLayout
              android:id="@+id/test_id2"
              android:orientation="vertical"
              android:layout_width="fill_parent"
              android:layout_height="fill_parent">
          </LinearLayout>
      """.trimIndent())
    }

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      val tags = findChild(file, XmlTag::class.java) { it.name == "LinearLayout" }
      verifyLightFieldsMatchXml(binding.fields.toList(), *tags)
    }
  }

  @Test
  fun removeViewRefreshesLightClassFields() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).hasLength(1)

      val tag = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }[0]
      deleteXml(file, tag.textRange)
    }

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).isEmpty()
    }
  }

  @Test
  fun updateIdRefreshesLightClassFields() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.fields).hasLength(1)

      val attribute = PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java)
        .filter { it is XmlAttribute && it.localName == "id" }[0] as XmlAttribute
      updateXml(file, attribute.valueElement!!.valueTextRange, "@+id/updated_id")
    }

    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      val tags = findChild(file, XmlTag::class.java) { it.localName == "LinearLayout" }
      verifyLightFieldsMatchXml(binding.fields.toList(), *tags)
    }
  }

  @Test
  fun updateVariablesRefreshesLightClassFields_withSingleLayout() {
    val file = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='obsolete' type='String'/>
        </data>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")
    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("getObsolete", "setObsolete")
    }
    val tag = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java).first { (it as XmlTag).localName == "variable" }
    updateXml(file, tag.textRange,
      // language=XML
              "<variable name='first' type='Integer'/> <variable name='second' type='String'/>")
    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      binding.methods.map { it.name }.let { methodNames ->
        assertThat(methodNames).containsAllOf("getFirst", "setFirst", "getSecond", "setSecond")
        assertThat(methodNames).containsNoneOf("getObsolete", "setObsolete")
      }
    }
  }

  @Test
  fun updateVariablesRefreshesLightClassFields_withMultipleLayoutConfigurations() {
    val mainLayout = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='first' type='String'/>
        </data>
      </layout>
    """.trimIndent())
    val landscapeLayout = fixture.addFileToProject(
      "res/layout-land/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='second' type='String'/>
        </data>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")
    (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("getFirst", "getSecond", "setFirst", "setSecond")
    }
    (fixture.findClass("test.db.databinding.ActivityMainBindingImpl", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("setFirst", "setSecond")
    }
    (fixture.findClass("test.db.databinding.ActivityMainBindingLandImpl", context) as LightBindingClass).let { binding ->
      assertThat(binding.methods.map { it.name }).containsAllOf("setFirst", "setSecond")
    }
    // Update first XML file
    run {
      val tag = PsiTreeUtil.findChildrenOfType(mainLayout, XmlTag::class.java).first { (it as XmlTag).localName == "variable" }
      updateXml(mainLayout, tag.textRange, "<variable name='third' type='String'/>")
      (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
        binding.methods.map { it.name }.let { methodNames ->
          assertThat(methodNames).containsAllOf("getSecond", "getThird")
          assertThat(methodNames).doesNotContain("getFirst")
        }
      }
    }
    // Update the second XML file
    run {
      val tag = PsiTreeUtil.findChildrenOfType(landscapeLayout, XmlTag::class.java).first { (it as XmlTag).localName == "variable" }
      updateXml(landscapeLayout, tag.textRange, "<variable name='fourth' type='String'/>")
      (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
        binding.methods.map { it.name }.let { methodNames ->
          assertThat(methodNames).containsAllOf("getThird", "getFourth")
          assertThat(methodNames).containsNoneOf("getFirst", "getSecond")
        }
      }
    }
    // Update both files at the same time
    run {
      val tagMain = PsiTreeUtil.findChildrenOfType(mainLayout, XmlTag::class.java).first { (it as XmlTag).localName == "variable" }
      updateXml(mainLayout, tagMain.textRange, "<variable name='fifth' type='String'/>")
      val tagLand = PsiTreeUtil.findChildrenOfType(landscapeLayout, XmlTag::class.java).first { (it as XmlTag).localName == "variable" }
      updateXml(landscapeLayout, tagLand.textRange, "<variable name='sixth' type='String'/>")
      (fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass).let { binding ->
        binding.methods.map { it.name }.let { methodNames ->
          assertThat(methodNames).containsAllOf("getFifth", "getSixth")
          assertThat(methodNames).containsNoneOf("getFirst", "getSecond", "getThird", "getFourth")
        }
      }
    }
  }

  @Test
  fun createViewFieldWithJavaType() {
    fixture.addFileToProject("src/java/com/example/Test.java", """
      package com.example;
      class Test {}
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view
            android:id="@+id/test_id"
            android:class="com.example.Test"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    fields[0].let { field ->
      val modifierList = field.modifierList!!
      val nullabilityManager = NullableNotNullManager.getInstance(project)
      assertThat(nullabilityManager.isNotNull(field, false)).isTrue()
      assertThat(modifierList.hasExplicitModifier(PsiModifier.PUBLIC)).isTrue()
      assertThat(modifierList.hasExplicitModifier(PsiModifier.FINAL)).isTrue()
    }
    val tags = findChild(file, XmlTag::class.java) { it.localName == "view" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("com.example.Test", context))
  }

  @Test
  fun createMergeFieldWithTargetLayoutType() {
    fixture.addFileToProject("res/layout/other_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
      </layout>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <merge
            android:id="@+id/test_id"
            android:layout="@layout/other_activity"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    ResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChild(file, XmlTag::class.java) { it.localName == "merge" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("test.db.databinding.OtherActivityBinding", context))
  }

  @Test
  fun createIncludeFieldWithTargetLayoutType() {
    fixture.addFileToProject("res/layout/other_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
      </layout>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <include
            android:id="@+id/test_id"
            android:layout="@layout/other_activity"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    ResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    val tags = findChild(file, XmlTag::class.java) { it.localName == "include" }
    verifyLightFieldsMatchXml(fields.toList(), *tags)

    assertThat(fields[0].name).isEqualTo("testId")
    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("test.db.databinding.OtherActivityBinding", context))
  }

  @Test
  fun createIncludeFieldWithPlainType() {
    assertThat(facet.isViewBindingEnabled()).isFalse() // Behavior of includes is slightly different if view binding is enabled

    fixture.addFileToProject("res/layout/simple_text.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <TextView xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    """.trimIndent())
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <include
            android:id="@+id/included"
            android:layout="@layout/simple_text"/>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    // initialize app resources
    ResourceRepositoryManager.getAppResources(facet)

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    val fields = binding.fields
    assertThat(fields).hasLength(1)
    assertThat(fields[0].name).isEqualTo("included")
    assertThat(fields[0].type).isEqualTo(LayoutBindingTypeUtil.parsePsiType("android.view.TextView", context))
  }

  @Test
  fun expectedStaticMethodsAreGenerated() {
    fixture.addFileToProject("res/layout/view_root_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view />
      </layout>
    """.trimIndent())
    fixture.addFileToProject("res/layout/merge_root_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <merge />
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class ViewRootActivity {}")

    // Same API for regardless of view root or merge root
    // Compare with LightViewBindingClassTest#expectedStaticMethodsAreGenerated
    listOf("test.db.databinding.ViewRootActivityBinding", "test.db.databinding.MergeRootActivityBinding").forEach { classPath ->
      (fixture.findClass(classPath, context) as LightBindingClass).let { binding ->
        val methods = binding.methods.filter { it.hasModifier(JvmModifier.STATIC) }
        assertThat(methods.map { it.presentation!!.presentableText }).containsExactly(
          "inflate(LayoutInflater)",
          "inflate(LayoutInflater, Object)",
          "inflate(LayoutInflater, ViewGroup, boolean)",
          "inflate(LayoutInflater, ViewGroup, boolean, Object)",
          "bind(View)",
          "bind(View, Object)"
        )
      }
    }
  }

  @Test
  fun bindingsNotGeneratedForNonDataBindingLayouts() {
    fixture.addFileToProject("res/layout/activity_view.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <view />
      </layout>
    """.trimIndent())
    fixture.addFileToProject("res/layout/plain_view.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <view xmlns:android="http://schemas.android.com/apk/res/android" />
    """.trimIndent())
    val context = fixture.addClass("public class ViewActivity {}")

    assertThat(fixture.findClass("test.db.databinding.ActivityViewBinding", context) as? LightBindingClass).isNotNull()
    assertThat(fixture.findClass("test.db.databinding.PlainViewBinding", context) as? LightBindingClass).isNull()
  }

  @Test
  fun fieldsAreAnnotatedNonNullAndNullableCorrectly() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout android:id="@+id/always_present">
          <TextView android:id="@+id/sometimes_present" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    fixture.addFileToProject("res/layout-land/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout android:id="@+id/always_present">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields).hasLength(2)
    val alwaysPresentField = binding.fields.first { it.name == "alwaysPresent" }
    val sometimesPresentField = binding.fields.first { it.name == "sometimesPresent" }

    val nullabilityManager = NullableNotNullManager.getInstance(project)
    assertThat(nullabilityManager.isNotNull(alwaysPresentField, false)).isTrue()
    assertThat(nullabilityManager.isNullable(sometimesPresentField, false)).isTrue()
  }

  @Test
  fun methodsAreAnnotatedNonNullAndNullableCorrectly() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class MainActivity {}")
    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass

    binding.methods.filter { it.name == "inflate" }.let { inflateMethods ->
      assertThat(inflateMethods).hasSize(4)
      inflateMethods.first { it.parameters.size == 4 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        (inflateMethod.parameters[1] as PsiParameter).assertExpected("ViewGroup", "root", isNullable = true)
        (inflateMethod.parameters[2] as PsiParameter).assertExpected("boolean", "attachToRoot")
        (inflateMethod.parameters[3] as PsiParameter).assertExpected("Object", "bindingComponent", isNullable = true)
        inflateMethod.returnType!!.assertExpected("ActivityMainBinding")
      }

      inflateMethods.first { it.parameters.size == 3 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        (inflateMethod.parameters[1] as PsiParameter).assertExpected("ViewGroup", "root", isNullable = true)
        (inflateMethod.parameters[2] as PsiParameter).assertExpected("boolean", "attachToRoot")
        inflateMethod.returnType!!.assertExpected("ActivityMainBinding")
      }

      inflateMethods.first { it.parameters.size == 2 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        (inflateMethod.parameters[1] as PsiParameter).assertExpected("Object", "bindingComponent", isNullable = true)
        inflateMethod.returnType!!.assertExpected("ActivityMainBinding")
      }

      inflateMethods.first { it.parameters.size == 1 }.let { inflateMethod ->
        (inflateMethod.parameters[0] as PsiParameter).assertExpected("LayoutInflater", "inflater")
        inflateMethod.returnType!!.assertExpected("ActivityMainBinding")
      }
    }

    binding.methods.filter { it.name == "bind" }.let { bindMethods ->
      assertThat(bindMethods).hasSize(2)
      bindMethods.first { it.parameters.size == 2 }.let { bindMethod ->
        (bindMethod.parameters[0] as PsiParameter).assertExpected("View", "view")
        (bindMethod.parameters[1] as PsiParameter).assertExpected("Object", "bindingComponent", isNullable = true)
        bindMethod.returnType!!.assertExpected("ActivityMainBinding")
      }

      bindMethods.first { it.parameters.size == 1 }.let { bindMethod ->
        (bindMethod.parameters[0] as PsiParameter).assertExpected("View", "view")
        bindMethod.returnType!!.assertExpected("ActivityMainBinding")
      }
    }
  }

  @Test
  fun viewProxyClassGeneratedForViewStubs() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <ViewStub
            android:id="@+id/test_id"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </ViewStub>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)!!
    assertThat(binding.findFieldByName("testId", false)!!.type.canonicalText)
      .isEqualTo(LayoutBindingModuleCache.getInstance(facet).dataBindingMode.viewStubProxy)
  }

  @Test
  fun noFieldsAreGeneratedForMergeTags() {
    val file = fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          <Button android:id="@+id/test_button" />
          <merge android:id="@+id/test_merge" />
          <TextView android:id="@+id/test_text" />
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context) as LightBindingClass
    assertThat(binding.fields.map { field -> field.name }).containsExactly("testButton", "testText")
  }

  @Test
  fun bindingCacheReturnsConsistentValuesIfResourcesDontChange() {
    val bindingCache = LayoutBindingModuleCache.getInstance(facet)

    // We want to initialize resources but NOT add a data binding layout file yet. This will ensure
    // we test the case where there are no layout resource files in the project yet.
    fixture.addFileToProject(
      "res/values/strings.xml",
      // language=XML
      """
        <resources>
          <string name="app_name">DummyAppName</string>
        </resources>
      """.trimIndent()
    )

    // language=XML
    val dummyXml = """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
      """.trimIndent()

    val noResourcesGroups = bindingCache.bindingLayoutGroups
    assertThat(noResourcesGroups.size).isEqualTo(0)
    assertThat(noResourcesGroups).isSameAs(bindingCache.bindingLayoutGroups)

    fixture.addFileToProject("res/layout/activity_first.xml", dummyXml)

    val oneResourceGroups = bindingCache.bindingLayoutGroups
    assertThat(oneResourceGroups.size).isEqualTo(1)
    assertThat(oneResourceGroups).isNotSameAs(noResourcesGroups)
    assertThat(oneResourceGroups).isSameAs(bindingCache.bindingLayoutGroups)

    fixture.addFileToProject("res/layout/activity_second.xml", dummyXml)
    val twoResourcesGroups = bindingCache.bindingLayoutGroups
    assertThat(twoResourcesGroups.size).isEqualTo(2)
    assertThat(twoResourcesGroups).isNotSameAs(noResourcesGroups)
    assertThat(twoResourcesGroups).isNotSameAs(oneResourceGroups)
    assertThat(twoResourcesGroups).isSameAs(bindingCache.bindingLayoutGroups)
  }

  @Test
  fun bindingCacheRecoversAfterExitingDumbMode() {
    // language=XML
    val dummyXml = """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout />
      </layout>
      """.trimIndent()

    fixture.addFileToProject("res/layout/activity_first.xml", dummyXml)

    // initialize app resources
    ResourceRepositoryManager.getAppResources(facet)

    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding")

    val dumbService = DumbService.getInstance(project) as DumbServiceImpl
    dumbService.isDumb = true

    // First, verify that dumb mode doesn't prevent us from accessing the previous cache
    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding")

    // XML updates are ignored in dumb mode
    fixture.addFileToProject("res/layout/activity_second.xml", dummyXml)
    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding")

    // XML updates should catch up after dumb mode is exited (in other words, we didn't save a snapshot of the stale
    // cache from before)
    dumbService.isDumb = false
    assertThat(LayoutBindingModuleCache.getInstance(facet).bindingLayoutGroups.map { group -> group.mainLayout.className })
      .containsExactly("ActivityFirstBinding", "ActivitySecondBinding")
  }

  private fun PsiType.assertExpected(typeName: String, isNullable: Boolean = false) {
    assertThat(presentableText).isEqualTo(typeName)
    if (this !is PsiPrimitiveType) {
      val nullabilityManager = NullableNotNullManager.getInstance(fixture.project)
      val nullabilityAnnotation = if (isNullable) nullabilityManager.defaultNullable else nullabilityManager.defaultNotNull
      assertThat(annotations.map { it.text }).contains("@$nullabilityAnnotation")
    }
  }

  private fun PsiParameter.assertExpected(typeName: String, name: String, isNullable: Boolean = false) {
    type.assertExpected(typeName, isNullable)
    assertThat(name).isEqualTo(name)
  }
}
