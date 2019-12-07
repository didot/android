/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.error;

import com.android.tools.idea.common.error.Issue;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.error.IssueProvider;
import com.android.tools.idea.common.error.LintIssueProvider;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NlIssueModelTest {
  private IssueModel myIssueModel;
  private Disposable myDisposable;

  @Before
  public void setUp() throws Exception {
    myIssueModel = new IssueModel();
    myDisposable = Disposer.newDisposable();
    ApplicationManager.setApplication(new MockApplication(myDisposable), myDisposable);
  }

  @Test
  public void setRenderErrorModel() {
    RenderErrorModel.Issue issue = MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR);
    RenderErrorModel renderErrorModel = createRenderErrorModel(issue);

    assertFalse(myIssueModel.hasIssues());
    assertFalse(hasRenderError());
    assertEquals(0, myIssueModel.getIssueCount());
    myIssueModel.addIssueProvider(new RenderIssueProvider(renderErrorModel));
    assertTrue(myIssueModel.hasIssues());
    assertTrue(hasRenderError());
    assertEquals(1, myIssueModel.getIssueCount());
    assertArrayEquals(new Issue[]{RenderIssueProvider.NlRenderIssueWrapper.wrapIssue(issue)}, myIssueModel.getIssues().toArray());
  }

  @NotNull
  private static RenderErrorModel createRenderErrorModel(RenderErrorModel.Issue... issues) {
    return new RenderErrorModel(ImmutableList.copyOf(issues));
  }

  @Test
  public void setLintAnnotationModel() {

    LintAnnotationsModel lintAnnotationsModel = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(lintAnnotationsModel, HighlightDisplayLevel.ERROR);

    assertFalse(myIssueModel.hasIssues());
    assertFalse(hasRenderError());
    assertEquals(0, myIssueModel.getIssueCount());
    myIssueModel.addIssueProvider(new LintIssueProvider(lintAnnotationsModel));
    assertTrue(myIssueModel.hasIssues());
    assertFalse(hasRenderError());
    assertTrue(myIssueModel.getIssueCount() == 1);
    assertArrayEquals(new Issue[]{new LintIssueProvider.LintIssueWrapper(lintAnnotationsModel.getIssues().get(0))},
                      myIssueModel.getIssues().toArray());
  }

  @Test
  public void listenerCalled() {
    final boolean[] listenerCalled = {false, false};
    IssueModel.IssueModelListener listener1 = () -> listenerCalled[0] = true;
    IssueModel.IssueModelListener listener2 = () -> listenerCalled[1] = true;
    myIssueModel.addErrorModelListener(listener1);
    myIssueModel.addErrorModelListener(listener2);
    myIssueModel.addIssueProvider(new RenderIssueProvider(createRenderErrorModel(
      MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR))));
/* b/145854391
    assertTrue(listenerCalled[0]);
    assertTrue(listenerCalled[1]);
b/145854391 */
    listenerCalled[0] = false;
    listenerCalled[1] = false;
    myIssueModel.removeErrorModelListener(listener1);
    myIssueModel.addIssueProvider(new RenderIssueProvider(createRenderErrorModel(
      MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR))));
    assertFalse(listenerCalled[0]);
/* b/145854391
    assertTrue(listenerCalled[1]);
b/145854391 */
  }

  @Test
  public void warningErrorCount() {
    assertFalse(myIssueModel.hasIssues());
    myIssueModel.addIssueProvider(new RenderIssueProvider(
      createRenderErrorModel(
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.ERROR),
        MockIssueFactory.createRenderIssue(HighlightSeverity.WARNING))));
    assertEquals(3, myIssueModel.getIssueCount());
    assertEquals(2, myIssueModel.getErrorCount());
    assertEquals(1, myIssueModel.getWarningCount());
    LintAnnotationsModel model = new LintAnnotationsModel();
    MockIssueFactory.addLintIssue(model, HighlightDisplayLevel.ERROR);
    MockIssueFactory.addLintIssue(model, HighlightDisplayLevel.WARNING);
    MockIssueFactory.addLintIssue(model, HighlightDisplayLevel.ERROR);
    myIssueModel.addIssueProvider(new LintIssueProvider(model));
    assertEquals(6, myIssueModel.getIssueCount());
    assertEquals(4, myIssueModel.getErrorCount());
    assertEquals(2, myIssueModel.getWarningCount());
  }

  @Test
  public void addAndRemoveIssueProvider() {
    IssueProvider fakeProvider = new IssueProvider() {
      @Override
      public void collectIssues(@NotNull ImmutableCollection.Builder<Issue> issueListBuilder) {
      }
    };
    assertTrue(fakeProvider.getListeners().isEmpty());

    myIssueModel.addIssueProvider(fakeProvider);
    assertEquals(1, fakeProvider.getListeners().size());
    assertEquals(myIssueModel.myUpdateCallback, fakeProvider.getListeners().get(0));

    myIssueModel.removeIssueProvider(fakeProvider);
    assertTrue(fakeProvider.getListeners().isEmpty());
  }

  @After
  public void tearDown() throws Exception {
    Disposer.dispose(myDisposable);
  }

  public boolean hasRenderError() {
    return myIssueModel.getIssues()
      .stream()
      .anyMatch(issue -> issue instanceof RenderIssueProvider.NlRenderIssueWrapper && issue.getSeverity() == HighlightSeverity.ERROR);
  }
}