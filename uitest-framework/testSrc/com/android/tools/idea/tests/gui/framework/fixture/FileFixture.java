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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static junit.framework.Assert.assertNotNull;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.util.Strings.quote;

public class FileFixture {
  @NotNull private final Project myProject;
  @NotNull private final File myPath;
  @NotNull private final VirtualFile myVirtualFile;

  public FileFixture(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myPath = virtualToIoFile(file);
    myVirtualFile = file;
  }

  @NotNull
  public FileFixture waitUntilErrorAnalysisFinishes() {
    Wait.minutes(2).expecting("error analysis to finish").until(
      () -> execute(
        new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            // isRunningOrPending() should be enough, but tests fail. During code analysis, DaemonCodeAnalyzerImpl, keeps calling
            // cancelUpdateProgress(), and then restarting again, but the restart is queued on the UI Thread, so for some moments,
            // isRunningOrPending() returns false, while technically there is in an event, on the UI queue, waiting.
            // isErrorAnalyzingFinished() checks a dirty flag, and the flag is not clean until the analysis is done.
            DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzerEx.getInstanceEx(myProject);
            return !codeAnalyzer.isRunningOrPending() && codeAnalyzer.isErrorAnalyzingFinished(getPsiFile());
          }
        }));
    return this;
  }

  @NotNull
  public Collection<HighlightInfo> getHighlightInfos(@NotNull final HighlightSeverity severity) {
    waitUntilErrorAnalysisFinishes();

    final Document document = getNotNullDocument();
    Collection<HighlightInfo> highlightInfos = execute(new GuiQuery<Collection<HighlightInfo>>() {
      @Override
      protected Collection<HighlightInfo> executeInEDT() throws Throwable {
        CommonProcessors.CollectProcessor<HighlightInfo> processor = new CommonProcessors.CollectProcessor<>();
        DaemonCodeAnalyzerEx.processHighlights(document, myProject, severity, 0, document.getTextLength(), processor);
        return processor.getResults();
      }
    });
    assert highlightInfos != null;
    return highlightInfos;
  }

  @NotNull
  private PsiFile getPsiFile() {
    final PsiFile psiFile = execute(new GuiQuery<PsiFile>() {
      @Override
      protected PsiFile executeInEDT() throws Throwable {
        return PsiManager.getInstance(myProject).findFile(myVirtualFile);
      }
    });
    assertNotNull("No Psi file found for path " + quote(myVirtualFile.getPath()), psiFile);
    return psiFile;
  }

  @NotNull
  public FileFixture waitForCodeAnalysisHighlightCount(@NotNull HighlightSeverity severity, int expected) {
    waitUntilErrorAnalysisFinishes();
    assertThat(getHighlightInfos(severity)).hasSize(expected);
    return this;
  }

  @NotNull
  private Document getNotNullDocument() {
    Document document = getDocument(myVirtualFile);
    assertNotNull("No Document found for path " + quote(myPath.getPath()), document);
    return document;
  }

  @Nullable
  public static Document getDocument(@NotNull final VirtualFile file) {
    return execute(new GuiQuery<Document>() {
      @Override
      protected Document executeInEDT() throws Throwable {
        return FileDocumentManager.getInstance().getDocument(file);
      }
    });
  }
}
