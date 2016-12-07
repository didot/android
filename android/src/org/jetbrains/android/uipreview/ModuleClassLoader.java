package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.editors.theme.ThemeEditorProvider;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.rendering.RenderClassLoader;
import com.android.tools.idea.rendering.RenderSecurityManager;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.utils.SdkUtils;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.project.model.AndroidModuleModel.EXPLODED_AAR;

/**
 * Render class loader responsible for loading classes in custom views
 * and local and library classes used by those custom views (other than
 * the framework itself, which is loaded by a parent class loader via
 * layout lib.)
 */
public final class ModuleClassLoader extends RenderClassLoader {
  private static final Logger LOG = Logger.getInstance(ModuleClassLoader.class);
  public static final boolean DEBUG_CLASS_LOADING = false;

  /** The base module to use as a render context; the class loader will consult the module dependencies and library dependencies
   * of this class as well to find classes */
  private final WeakReference<Module> myModuleReference;

  /** The layout library to use as a root class loader (e.g. the place to obtain the layoutlib Android SDK view classes from */
  private final LayoutLibrary myLibrary;

  /** Map from fully qualified class name to the corresponding .class file for each class loaded by this class loader */
  private Map<String, VirtualFile> myClassFiles;
  /** Map from fully qualified class name to the corresponding last modified info for each class loaded by this class loader */
  private Map<String, ClassModificationTimestamp> myClassFilesLastModified;

  private static class ClassModificationTimestamp {
    public final long timestamp;
    public final long length;

    public ClassModificationTimestamp(long timestamp, long length) {
      this.timestamp = timestamp;
      this.length = length;
    }
  }

  private ModuleClassLoader(@NotNull LayoutLibrary library, @NotNull Module module) {
    super(library.getClassLoader(), library.getApiLevel());
    myLibrary = library;
    myModuleReference = new WeakReference<>(module);
  }

  @NotNull
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      if (!myInsideJarClassLoader) {
        final Module module = myModuleReference.get();
        if (module != null) {
          int index = name.lastIndexOf('.');
          if (index != -1 && name.charAt(index + 1) == 'R' && (index == name.length() - 2 || name.charAt(index + 2) == '$') && index > 1) {
            AppResourceRepository appResources = AppResourceRepository.getAppResources(module, false);
            if (appResources != null) {
              byte[] data = ResourceClassRegistry.get(module.getProject()).findClassDefinition(name, appResources);
              if (data != null) {
                data = convertClass(data);
                if (DEBUG_CLASS_LOADING) {
                  //noinspection UseOfSystemOutOrSystemErr
                  System.out.println("  defining class " + name + " from AAR registry");
                }
                return defineClassAndPackage(name, data, 0, data.length);
              }
            }
          }
        }
      }
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
      byte[] clazz = null;
      if (RecyclerViewHelper.CN_CUSTOM_ADAPTER.equals(name)) {
        clazz = RecyclerViewHelper.getAdapterClass();
      }
      if (RecyclerViewHelper.CN_CUSTOM_VIEW_HOLDER.equals(name)) {
        clazz = RecyclerViewHelper.getViewHolder();
      }
      if (clazz != null) {
        return defineClassAndPackage(name, clazz, 0, clazz.length);
      }
      throw e;
    }
  }

  @Nullable
  public static ClassLoader create(IAndroidTarget target, Module module) throws Exception {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    if (androidPlatform == null) {
      return null;
    }
    AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(target);
    LayoutLibrary library = targetData.getLayoutLibrary(module.getProject());
    if (library == null) {
      return null;
    }

    return get(library, module);
  }

  @NotNull
  @Override
  protected Class<?> load(String name) throws ClassNotFoundException {
    Module module = myModuleReference.get();
    if (module == null) {
      throw new ClassNotFoundException(name);
    }
    Class<?> aClass = loadClassFromModuleOrDependency(module, name, new HashSet<>());

    if (aClass == null) {
      aClass = loadClassFromJar(name);
    }

    if (aClass != null) {
      return aClass;
    }

    throw new ClassNotFoundException(name);
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    // We overload loadClass() to load a class defined in a project
    // rather than a version of it that may already be present in Studio.
    // This is an issue if Studio shares a library or classes with some android code
    // we are trying to preview; the class loaded would be the one already used by
    // Studio rather than the one packaged with the library.
    // If they end up being different (say the lib is more recent than the Studio version),
    // it will likely result in broken preview as functions would be different / not present.
    // The only known case of this at this point is ConstraintLayout (a solver library
    // is used both by the android implementation and by Android Studio).

    // FIXME: While testing this approach, we found an issue on some Windows machine where
    // class loading would be broken. Thus, we limit the fix to the impacted solver classes
    // for now, until we can investigate the problem more in depth on Windows.
    boolean loadFromProject = name.startsWith("android.support.constraint.solver");
    if (loadFromProject) {
      try {
        // Give priority to loading class from this Class Loader. This will avoid leaking classes from the plugin
        // into the project.
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
          return loadedClass;
        }
        return load(name);
      }
      catch (Exception ignore) {
        // Catch-all, defer to the parent implementation
      }
    }
    return super.loadClass(name);
  }

  @Nullable
  private Class<?> loadClassFromModuleOrDependency(Module module, String name, Set<Module> visited) {
    if (!visited.add(module)) {
      return null;
    }

    if (module.isDisposed()) {
      return null;
    }

    Class<?> aClass = loadClassFromModule(module, name);
    if (aClass != null) {
      return aClass;
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      aClass = loadClassFromModuleOrDependency(depModule, name, visited);
      if (aClass != null) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private Class<?> loadClassFromModule(Module module, String name) {
    if (module.isDisposed()) {
      return null;
    }

    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension == null) {
      return null;
    }

    VirtualFile vOutFolder = extension.getCompilerOutputPath();
    VirtualFile classFile = null;
    if (vOutFolder == null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.requiresAndroidModel()) {
        AndroidModel androidModel = facet.getAndroidModel();
        if (androidModel != null) {
          classFile = androidModel.getClassJarProvider().findModuleClassFile(name, module);
        }
      }
    } else {
      classFile = ClassJarProvider.findClassFileInPath(vOutFolder, name);
    }
    if (classFile != null) {
      return loadClassFile(name, classFile);
    }
    return null;
  }

  /**
   * Determines whether the class specified by the given qualified name has a source file in the IDE that
   * has been edited more recently than its corresponding class file.
   * <p/>This method requires the indexing to have finished
   * <p/><b>Note that this method can only answer queries for classes that this class loader has previously
   * loaded!</b>
   *
   * @param fqcn the fully qualified class name
   * @param myCredential a render sandbox credential
   * @return true if the source file has been modified, or false if not (or if the source file cannot be found)
   */
  public boolean isSourceModified(@NotNull final String fqcn, @Nullable final Object myCredential) {
    final Module module = myModuleReference.get();
    if (module == null) {
      return false;
    }
    VirtualFile classFile = getClassFile(fqcn);

    // Make sure the class file is up to date and if not, log an error
    if (classFile != null) {
      // Allow creating class loaders during rendering; may be prevented by the RenderSecurityManager
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        long classFileModified = classFile.getTimeStamp();
        if (classFileModified > 0L) {
          VirtualFile virtualFile = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
            @Nullable
            @Override
            public VirtualFile compute() {
              Project project = module.getProject();
              GlobalSearchScope scope = module.getModuleWithDependenciesScope();
              PsiManager psiManager = PsiManager.getInstance(project);
              JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
              PsiClass source = psiFacade.findClass(fqcn, scope);
              if (source != null) {
                PsiFile containingFile = source.getContainingFile();
                if (containingFile != null) {
                  return containingFile.getVirtualFile();
                }
              }
              return null;
            }
          });

          if (virtualFile != null && !FN_RESOURCE_CLASS.equals(virtualFile.getName())) { // Don't flag R.java edits; not done by user
            // Edited but not yet saved?
            boolean modified = FileDocumentManager.getInstance().isFileModified(virtualFile);
            if (!modified) {
              // Check timestamp
              File sourceFile = VfsUtilCore.virtualToIoFile(virtualFile);
              long sourceFileModified = sourceFile.lastModified();

              AndroidFacet facet = AndroidFacet.getInstance(module);
              // User modifications on the source file might not always result on a new .class file.
              // We use the project modification time instead to display the warning more reliably.
              // Also, some build systems may use a constant last modified timestamp for .class files,
              // for deterministic builds, so the project modification time is more reliable.
              long lastBuildTimestamp = classFileModified;
              if (facet != null && facet.requiresAndroidModel() && facet.getAndroidModel() != null) {
                Long projectBuildTimestamp = facet.getAndroidModel().getLastBuildTimestamp(module.getProject());
                if (projectBuildTimestamp != null) {
                  lastBuildTimestamp = projectBuildTimestamp;
                }
              }
              if (sourceFileModified > lastBuildTimestamp && lastBuildTimestamp > 0L) {
                modified = true;
              }
            }

            return modified;
          }
        }
      } finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }

    return false;
  }

  @Override
  @Nullable
  protected Class<?> loadClassFile(final String fqcn, @NotNull VirtualFile classFile) {
    if (myClassFiles == null) {
      myClassFiles = Maps.newHashMap();
      myClassFilesLastModified = Maps.newHashMap();
    }
    myClassFiles.put(fqcn, classFile);
    myClassFilesLastModified.put(fqcn,
                                 new ClassModificationTimestamp(classFile.getTimeStamp(), classFile.getLength()));

    return super.loadClassFile(fqcn, classFile);
  }

  @Override
  protected List<URL> getExternalJars() {
    final Module module = myModuleReference.get();
    if (module == null) {
      return Collections.emptyList();
    }
    final List<URL> result = new ArrayList<>();

    if (ThemeEditorProvider.THEME_EDITOR_ENABLE) {
      URL customWidgetsUrl = ThemeEditorUtils.getCustomWidgetsJarUrl();

      if (customWidgetsUrl != null) {
        result.add(customWidgetsUrl);
      }
    }
    List<VirtualFile> externalLibraries;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.requiresAndroidModel() && facet.getAndroidModel() != null) {
      AndroidModel androidModel = facet.getAndroidModel();
      externalLibraries = androidModel.getClassJarProvider().getModuleExternalLibraries(module);
    } else {
      externalLibraries = AndroidRootUtil.getExternalLibraries(module);
    }
    for (VirtualFile libFile : externalLibraries) {
      if (EXT_JAR.equals(libFile.getExtension())) {
        final File file = new File(libFile.getPath());
        if (file.exists()) {
          try {
            result.add(SdkUtils.fileToUrl(file));

            File aarDir = file.getParentFile();
            if (aarDir != null && (aarDir.getPath().endsWith(DOT_AAR) || aarDir.getPath().contains(EXPLODED_AAR))) {
              if (aarDir.getPath().contains(EXPLODED_AAR)) {
                if (aarDir.getPath().endsWith(LIBS_FOLDER)) {
                  // Some libraries recently started packaging jars inside a sub libs folder inside jars
                  aarDir = aarDir.getParentFile();
                }
                // Gradle plugin version 1.2.x and later has classes in aar-dir/jars/
                if (aarDir.getPath().endsWith(FD_JARS)) {
                  aarDir = aarDir.getParentFile();
                }
              }
              AppResourceRepository appResources = AppResourceRepository.getAppResources(module, true);
              if (appResources != null) {
                ResourceClassRegistry.get(module.getProject()).addAarLibrary(appResources, aarDir);
              }
            }
          }
          catch (MalformedURLException e) {
            LOG.error(e);
          }
        }
      }
    }
    return result;
  }

  /** Returns the path to a class file loaded for the given class, if any */
  @Nullable
  private VirtualFile getClassFile(@NotNull String className) {
    if (myClassFiles == null) {
      return null;
    }
    VirtualFile file = myClassFiles.get(className);
    if (file == null) {
      return null;
    }
    return file.isValid() ? file : null;
  }

  /** Checks whether any of the .class files loaded by this loader have changed since the creation of this class loader */
  private boolean isUpToDate() {
    if (myClassFiles != null) {
      for (Map.Entry<String, VirtualFile> entry : myClassFiles.entrySet()) {
        String className = entry.getKey();
        VirtualFile classFile = entry.getValue();
        if (!classFile.isValid()) {
          return false;
        }
        ClassModificationTimestamp lastModifiedStamp = myClassFilesLastModified.get(className);
        if (lastModifiedStamp != null) {
          long loadedModifiedTime = lastModifiedStamp.timestamp;
          long loadedModifiedLength = lastModifiedStamp.length;
          long classFileModifiedTime = classFile.getTimeStamp();
          long classFileModifiedLength = classFile.getLength();
          if ((classFileModifiedTime > 0L && loadedModifiedTime > 0L && loadedModifiedTime < classFileModifiedTime)
             || loadedModifiedLength != classFileModifiedLength) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  @NotNull
  public static ModuleClassLoader get(@NotNull LayoutLibrary library, @NotNull Module module) {
    ModuleClassLoader loader = ourCache.get(module);
    if (loader != null) {
      if (library != loader.myLibrary) {
        if (DEBUG_CLASS_LOADING) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Discarding loader because the layout library has changed");
        }
        loader = null;
      } else if (!loader.isUpToDate()) {
        if (DEBUG_CLASS_LOADING) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Discarding loader because some files have changed");
        }
        loader = null;
      } else {
        List<URL> updatedJarDependencies = loader.getExternalJars();
        if (loader.myJarClassLoader != null && !updatedJarDependencies.equals(loader.myJarClassLoader.getUrls())) {
          if (DEBUG_CLASS_LOADING) {
            //noinspection UseOfSystemOutOrSystemErr
            System.out.println("Recreating jar class loader because dependencies have changed.");
          }
          loader.myJarClassLoader = loader.createClassLoader(updatedJarDependencies);
        }
      }

      // To be correct we should also check that the dependencies have not changed. It's not
      // a problem when you add a new dependency with classes that haven't yet been resolved,
      // but if for some reason a class is defined in multiple dependencies, and we've already
      // resolved that class, changing the order could change the particular class that should
      // be loaded, and we would need to recreate the loader - but this won't detect that.
      // To solve this correctly we'd need to store a key in the cache recording the project
      // dependencies when the loader was created, and compare it to the current one. However,
      // that's a pretty unusual scenario so we won't worry about it until the loader situation
      // is solved more generally (e.g. separating out .jar loading from .class loading etc).
    }

    if (loader == null) {
      loader = new ModuleClassLoader(library, module);
      ourCache.put(module, loader);
    } else if (DEBUG_CLASS_LOADING) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Reused class loader for rendering");
    }

    return loader;
  }

  /** Flush any cached class loaders */
  public static void clearCache() {
    ourCache.clear();
  }

  /** Remove the cached class loader for the module. */
  public static void clearCache(Module module) {
    if (ourCache.containsKey(module)) {
      ourCache.remove(module);
    }
  }

  public boolean isClassLoaded(String className) {
    return findLoadedClass(className) != null;
  }

  /** Temporary hack: Store this in a weak hash map cached by modules. In the next version we should move this
   * into a proper persistent render service. */
  private static WeakHashMap<Module,ModuleClassLoader> ourCache = new WeakHashMap<>();
}
