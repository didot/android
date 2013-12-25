package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.*;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class IntellijLintClient extends LintClient implements Disposable {
  protected static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.IntellijLintClient");

  @NonNull protected abstract List<Issue> getIssues();
  @Nullable protected abstract Module getModule();
  @NonNull protected Project myProject;
  @Nullable protected Map<com.android.tools.lint.detector.api.Project, Module> myModuleMap;

  protected IntellijLintClient(@NonNull Project project) {
    myProject = project;
  }

  /** Creates a lint client for batch inspections */
  public static IntellijLintClient forBatch(@NotNull Project project,
                                            @NotNull Map<Issue, Map<File, List<ProblemData>>> problemMap,
                                            @NotNull AnalysisScope scope,
                                            @NotNull List<Issue> issues) {
    return new BatchLintClient(project, problemMap, scope, issues);
  }

  /**
   * Creates a lint client used for in-editor single file lint analysis (e.g. background checking while user is editing.)
   */
  public static IntellijLintClient forEditor(@NotNull State state) {
    return new EditorLintClient(state);
  }

  @Nullable
  protected Module findModuleForLintProject(@NotNull Project project,
                                            @NotNull com.android.tools.lint.detector.api.Project lintProject) {
    if (myModuleMap != null) {
      Module module = myModuleMap.get(lintProject);
      if (module != null) {
        return module;
      }
    }
    final File dir = lintProject.getDir();
    final VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
    return vDir != null ? ModuleUtilCore.findModuleForFile(vDir, project) : null;
  }

  void setModuleMap(@Nullable Map<com.android.tools.lint.detector.api.Project, Module> moduleMap) {
    myModuleMap = moduleMap;
  }

  @Override
  public Configuration getConfiguration(@NonNull com.android.tools.lint.detector.api.Project project) {
    return new IntellijLintConfiguration(getIssues());
  }

  @Override
  public abstract void report(@NonNull Context context,
                              @NonNull Issue issue,
                              @NonNull Severity severity,
                              @Nullable Location location,
                              @NonNull String message,
                              @Nullable Object data);

  /**
   * Recursively calls {@link #report} on the secondary location of this error, if any, which in turn may call it on a third
   * linked location, and so on.This is necessary since IntelliJ problems don't have secondary locations; instead, we create one
   * problem for each location associated with the lint error.
   */
  protected void reportSecondary(@NonNull Context context, @NonNull Issue issue, @NonNull Severity severity, @NonNull Location location,
                                 @NonNull String message, @Nullable Object data) {
    Location secondary = location.getSecondary();
    if (secondary != null) {
      if (secondary.getMessage() != null) {
        message = message + " (" + secondary.getMessage() + ")";
      }
      report(context, issue, severity, secondary, message, data);
    }
  }

  @Override
  public void log(@NonNull Severity severity, @Nullable Throwable exception, @Nullable String format, @Nullable Object... args) {
    if (severity == Severity.ERROR || severity == Severity.FATAL) {
      if (format != null) {
        LOG.error(String.format(format, args), exception);
      } else if (exception != null) {
        LOG.error(exception);
      }
    } else if (severity == Severity.WARNING) {
      if (format != null) {
        LOG.warn(String.format(format, args), exception);
      } else if (exception != null) {
        LOG.warn(exception);
      }
    } else {
      if (format != null) {
        LOG.info(String.format(format, args), exception);
      } else if (exception != null) {
        LOG.info(exception);
      }
    }
  }

  @Override
  public IDomParser getDomParser() {
    return new DomPsiParser(this);
  }

  @Override
  public IJavaParser getJavaParser() {
    return new LombokPsiParser(this);
  }

  @NonNull
  @Override
  public List<File> getJavaClassFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
    // todo: implement when class files checking detectors will be available
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public List<File> getJavaLibraries(@NonNull com.android.tools.lint.detector.api.Project project) {
    // todo: implement
    return Collections.emptyList();
  }

  @Override
  @NonNull
  public String readFile(@NonNull final File file) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

    if (vFile == null) {
      LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
      return "";
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
        if (psiFile == null) {
          LOG.info("Cannot find file " + file.getPath() + " in the PSI");
          return null;
        }
        else {
          return psiFile.getText();
        }
      }
    });
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public File getSdkHome() {
    Module module = getModule();
    if (module != null) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null) {
        String path = moduleSdk.getHomePath();
        if (path != null) {
          File home = new File(path);
          if (home.exists()) {
            return home;
          }
        }
      }
    }

    File sdkHome = super.getSdkHome();
    if (sdkHome != null) {
      return sdkHome;
    }

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(m).getSdk();
      if (moduleSdk != null) {
        String path = moduleSdk.getHomePath();
        if (path != null) {
          File home = new File(path);
          if (home.exists()) {
            return home;
          }
        }
      }
    }

    return null;
  }

  @Override
  public boolean isGradleProject(com.android.tools.lint.detector.api.Project project) {
    Module module = getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && facet.isGradleProject();
    }
    return Projects.isGradleProject(myProject);
  }

  // Overridden such that lint doesn't complain about missing a bin dir property in the event
  // that no SDK is configured
  @Override
  @Nullable
  public File findResource(@NonNull String relativePath) {
    File top = getSdkHome();
    if (top != null) {
      File file = new File(top, relativePath);
      if (file.exists()) {
        return file;
      }
    }

    return null;
  }

  @Override
  public boolean isProjectDirectory(@NonNull File dir) {
    return new File(dir, Project.DIRECTORY_STORE_FOLDER).exists();
  }

  /**
   * A lint client used for in-editor single file lint analysis (e.g. background checking while user is editing.)
   * <p>
   * Since this applies only to a given file and module, it can take some shortcuts over what the general
   * {@link BatchLintClient} has to do.
   * */
  private static class EditorLintClient extends IntellijLintClient {
    private final State myState;

    public EditorLintClient(@NotNull State state) {
      super(state.getModule().getProject());
      myState = state;
    }

    @Nullable
    @Override
    protected Module getModule() {
      return myState.getModule();
    }

    @NonNull
    @Override
    protected List<Issue> getIssues() {
      return myState.getIssues();
    }

    @Override
    public void report(@NonNull Context context,
                       @NonNull Issue issue,
                       @NonNull Severity severity,
                       @Nullable Location location,
                       @NonNull String message,
                       @Nullable Object data) {
      if (location != null) {
        final File file = location.getFile();
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

        if (myState.getMainFile().equals(vFile)) {
          final Position start = location.getStart();
          final Position end = location.getEnd();

          final TextRange textRange = start != null && end != null && start.getOffset() <= end.getOffset()
                                      ? new TextRange(start.getOffset(), end.getOffset())
                                      : TextRange.EMPTY_RANGE;

          myState.getProblems().add(new ProblemData(issue, message, textRange));
        }

        Location secondary = location.getSecondary();
        if (secondary != null && myState.getMainFile().equals(LocalFileSystem.getInstance().findFileByIoFile(secondary.getFile()))) {
          reportSecondary(context, issue, severity, location, message, data);
        }
      }
    }

    @Override
    @NotNull
    public String readFile(@NonNull File file) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

      if (vFile == null) {
        LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
        return "";
      }
      final String content = getFileContent(vFile);

      if (content == null) {
        LOG.info("Cannot find file " + file.getPath() + " in the PSI");
        return "";
      }
      return content;
    }

    @Nullable
    private String getFileContent(final VirtualFile vFile) {
      if (Comparing.equal(myState.getMainFile(), vFile)) {
        return myState.getMainFileContent();
      }

      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          final Module module = myState.getModule();
          final Project project = module.getProject();
          final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);

          if (psiFile == null) {
            return null;
          }
          final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

          if (document != null) {
            final DocumentListener listener = new DocumentListener() {
              @Override
              public void beforeDocumentChange(DocumentEvent event) {
              }

              @Override
              public void documentChanged(DocumentEvent event) {
                myState.markDirty();
              }
            };
            document.addDocumentListener(listener, EditorLintClient.this);
          }
          return psiFile.getText();
        }
      });
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myState.getModule()).getSourceRoots(false);
      final List<File> result = new ArrayList<File>(sourceRoots.length);

      for (VirtualFile root : sourceRoots) {
        result.add(new File(root.getPath()));
      }
      return result;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      AndroidFacet facet = AndroidFacet.getInstance(myState.getModule());
      if (facet != null) {
        return IntellijLintUtils.getResourceDirectories(facet);
      }
      return super.getResourceFolders(project);
    }
  }

  /** Lint client used for batch operations */
  private static class BatchLintClient extends IntellijLintClient {
    private final Map<Issue, Map<File, List<ProblemData>>> myProblemMap;
    private final AnalysisScope myScope;
    private final List<Issue> myIssues;

    public BatchLintClient(@NotNull Project project,
                           @NotNull Map<Issue, Map<File, List<ProblemData>>> problemMap,
                           @NotNull AnalysisScope scope,
                           @NotNull List<Issue> issues) {
      super(project);
      myProblemMap = problemMap;
      myScope = scope;
      myIssues = issues;
    }

    @Nullable
    @Override
    protected Module getModule() {
      // No default module
      return null;
    }

    @NonNull
    @Override
    protected List<Issue> getIssues() {
      return myIssues;
    }

    @Override
    public void report(@NonNull Context context,
                       @NonNull Issue issue,
                       @NonNull Severity severity,
                       @Nullable Location location,
                       @NonNull String message,
                       @Nullable Object data) {
      VirtualFile vFile = null;
      File file = null;

      if (location != null) {
        file = location.getFile();
        vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      }
      else if (context.getProject() != null) {
        final Module module = findModuleForLintProject(myProject, context.getProject());

        if (module != null) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          vFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;

          if (vFile != null) {
            file = new File(vFile.getPath());
          }
        }
      }

      if (vFile != null && myScope.contains(vFile)) {
        file = new File(PathUtil.getCanonicalPath(file.getPath()));

        Map<File, List<ProblemData>> file2ProblemList = myProblemMap.get(issue);
        if (file2ProblemList == null) {
          file2ProblemList = new HashMap<File, List<ProblemData>>();
          myProblemMap.put(issue, file2ProblemList);
        }

        List<ProblemData> problemList = file2ProblemList.get(file);
        if (problemList == null) {
          problemList = new ArrayList<ProblemData>();
          file2ProblemList.put(file, problemList);
        }

        TextRange textRange = TextRange.EMPTY_RANGE;

        if (location != null) {
          final Position start = location.getStart();
          final Position end = location.getEnd();

          if (start != null && end != null && start.getOffset() <= end.getOffset()) {
            textRange = new TextRange(start.getOffset(), end.getOffset());
          }
        }
        problemList.add(new ProblemData(issue, message, textRange));

        if (location != null && location.getSecondary() != null) {
          reportSecondary(context, issue, severity, location, message, data);
        }
      }
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      final Module module = findModuleForLintProject(myProject, project);
      if (module == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      final List<File> result = new ArrayList<File>(sourceRoots.length);

      for (VirtualFile root : sourceRoots) {
        result.add(new File(root.getPath()));
      }
      return result;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
      final Module module = findModuleForLintProject(myProject, project);
      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          return IntellijLintUtils.getResourceDirectories(facet);
        }
      }
      return super.getResourceFolders(project);
    }
  }
}
