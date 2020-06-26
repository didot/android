// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler.tools;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionStatus;
import org.jetbrains.android.util.StringBuildingOutputProcessor;
import org.jetbrains.android.util.WaitingStrategies;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExternalParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidMavenExecutor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidMavenExecutor");

  private static final String BUILD_ERROR_INDICATOR = "[error]";

  private AndroidMavenExecutor() {
  }

  public static Map<CompilerMessageCategory, List<String>> generateResources(final Module module) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(module.getProject());

    MavenProject mavenProject = projectsManager.findProject(module);
    final MavenRunnerParameters parameters =
      new MavenRunnerParameters(true, mavenProject.getDirectory(), mavenProject.getFile().getName(),
                                Collections.singletonList("process-resources"),
                                projectsManager.getExplicitProfiles());

    final Map<CompilerMessageCategory, List<String>> result = new HashMap<CompilerMessageCategory, List<String>>();
    result.put(CompilerMessageCategory.ERROR, new ArrayList<String>());

    try {
      JavaParameters javaParams = ApplicationManager.getApplication().runReadAction(new Computable<JavaParameters>() {
        @Nullable
        @Override
        public JavaParameters compute() {
          try {
            return MavenExternalParameters.createJavaParameters(module.getProject(), parameters);
          }
          catch (ExecutionException e) {
            LOG.info(e);
            result.get(CompilerMessageCategory.ERROR).add(e.getMessage());
            return null;
          }
        }
      });
      if (javaParams == null) {
        return result;
      }

      GeneralCommandLine commandLine = javaParams.toCommandLine();
      final StringBuildingOutputProcessor processor = new StringBuildingOutputProcessor();
      boolean success =
        AndroidUtils.executeCommand(commandLine, processor, WaitingStrategies.WaitForever.getInstance()) == ExecutionStatus.SUCCESS;
      String message = processor.getMessage();
      if (!success) {
        LOG.info(message);
        String lcmessage = StringUtil.toLowerCase(message);
        int buildErrorIndex = lcmessage.indexOf(BUILD_ERROR_INDICATOR);
        if (buildErrorIndex >= 0) {
          result.get(CompilerMessageCategory.ERROR).add(message.substring(buildErrorIndex));
        }
      }
    }
    catch (ExecutionException e) {
      LOG.info(e);
      result.get(CompilerMessageCategory.ERROR).add(e.getMessage());
    }

    return result;
  }
}
