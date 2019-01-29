// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.issues

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.suggestions.SuggestionsViewIssueRenderer
import com.android.tools.idea.gradle.structure.model.*
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class SuggestionsViewIssueRendererTest {
  @Mock private lateinit var context: PsContext
  private var viewUsagePath = TestPath("/WITH_USAGE", null, "href-dest")
  private var quickFix = object: PsQuickFix {
    override fun getHyperlinkDestination(context: PsContext): String? = "link"
    override fun getHtml(context: PsContext): String = "html"
  }
  private val testIssuePath = TestPath("/PATH")
  private val testIssueParentedPath = TestPath("/CHILD", testIssuePath)

  @Before
  fun setUp() {
    initMocks(this)
  }

  private fun createIssue(testIssuePath: PsPath, quickFix: PsQuickFix? = null) : PsIssue =
      PsGeneralIssue("TEXT", "DESCRIPTION", testIssuePath, PsIssueType.PROJECT_ANALYSIS, PsIssue.Severity.ERROR, quickFix)

  data class RenderResult(val header: String?, val details: String?)

  private fun renderIssue(renderer: IssueRenderer, psIssue: PsIssue, scope: PsPath?) =
      "<td[^>]*>.*</td><td[^>]*>(((?!<br/>).)*)(<br/>(.*))?</td>".toRegex().find(renderer.renderIssue(psIssue, scope)).let {
        val header = it?.groups?.get(1)?.value.orEmpty()
        val details = it?.groups?.get(4)?.value.orEmpty()
        RenderResult(header, details)
      }

  @Test
  fun testRenderIssue() {
    val testIssue = createIssue(testIssuePath)
    val renderer = SuggestionsViewIssueRenderer(context)
    val result = renderIssue(renderer, testIssue, scope = null)
    assertThat(result.header, equalTo("<b>/PATH : TEXT</b>"))
    assertThat(result.details, equalTo(""))
  }

  @Test
  fun testRenderIssue_withParent_noShowParentPath() {
    val testIssue = createIssue(testIssueParentedPath)
    val renderer = SuggestionsViewIssueRenderer(context)
    val result = renderIssue(renderer, testIssue, scope = testIssueParentedPath.parents[0])
    assertThat(result.header, equalTo("<b>/CHILD : TEXT</b>"))
    assertThat(result.details, equalTo(""))
  }

  @Test
  fun testRenderIssue_withParent_showParentPath() {
    val testIssue = createIssue(testIssueParentedPath)
    val renderer = SuggestionsViewIssueRenderer(context)
    val result = renderIssue(renderer, testIssue, scope = null)
    assertThat(result.header, equalTo("""<b>/CHILD (<a href="null">/PATH</a>) : TEXT</b>"""))
    assertThat(result.details, equalTo(""))
  }

  @Test
  fun testRenderIssue_viewUsage() {
    val testIssue = createIssue(viewUsagePath)
    val renderer = SuggestionsViewIssueRenderer(context)
    val result = renderIssue(renderer, testIssue, scope = null)
    assertThat(result.header, equalTo("<b>/WITH_USAGE : TEXT</b>"))
    assertThat(result.details, equalTo("<a href='href-dest'>View usage</a>"))
  }

  @Test
  fun testRenderIssue_renderPathAndQuickFix() {
    val testIssue = createIssue(testIssuePath, quickFix = quickFix)
    val renderer = SuggestionsViewIssueRenderer(context)
    val result = renderIssue(renderer, testIssue, scope = null)
    assertThat(result.header, equalTo("<b>/PATH : TEXT</b>"))
    assertThat(result.details, equalTo(""))
  }
}