/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.CommonProcessors;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;

public class FileFixture {
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myVirtualFile;

  public FileFixture(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myVirtualFile = file;
  }

  @NotNull
  public FileFixture waitUntilErrorAnalysisFinishes() {
    // TODO: Should this really take as long as 20 seconds?
    Wait.seconds(20).expecting("error analysis to finish").until(() -> GuiQuery.getNonNull(() -> {
      // isRunningOrPending() should be enough, but tests fail. During code analysis, DaemonCodeAnalyzerImpl, keeps calling
      // cancelUpdateProgress(), and then restarting again, but the restart is queued on the UI Thread, so for some moments,
      // isRunningOrPending() returns false, while technically there is in an event, on the UI queue, waiting.
      // isErrorAnalyzingFinished() checks a dirty flag, and the flag is not clean until the analysis is done.
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myVirtualFile);
      return !codeAnalyzer.isRunningOrPending() && codeAnalyzer.isErrorAnalyzingFinished(psiFile);
    }));
    return this;
  }

  @NotNull
  public Collection<HighlightInfo> getHighlightInfos(@NotNull final HighlightSeverity severity) {
    waitUntilErrorAnalysisFinishes();
    return GuiQuery.getNonNull(
      () -> {
        Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
        CommonProcessors.CollectProcessor<HighlightInfo> processor = new CommonProcessors.CollectProcessor<>();
        DaemonCodeAnalyzerEx.processHighlights(document, myProject, severity, 0, document.getTextLength(), processor);
        return processor.getResults();
      });
  }

  @NotNull
  public FileFixture waitForCodeAnalysisHighlightCount(@NotNull HighlightSeverity severity, int expected) {
    Wait.seconds(5)
      .expecting("number of highlight items to be " + expected)
      .until(() -> getHighlightInfos(severity).size() == expected);
    return this;
  }
}
