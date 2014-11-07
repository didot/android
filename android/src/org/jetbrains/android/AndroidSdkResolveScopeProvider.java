package org.jetbrains.android;

import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.SdkResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.augment.AndroidInternalRClass;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkResolveScopeProvider extends SdkResolveScopeProvider {
  @Override
  public GlobalSearchScope getScope(@NotNull Project project, @NotNull JdkOrderEntry entry) {
    final Sdk sdk = entry.getJdk();

    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }

    if (sdk.getRootProvider().getFiles(OrderRootType.SOURCES).length == 0) {
      return null;
    }
    return new MyJdkScope(project, entry);
  }

  public static class MyJdkScope extends JdkScope {
    private final Sdk mySdk;

    private MyJdkScope(Project project, @NotNull JdkOrderEntry jdkOrderEntry) {
      super(project, jdkOrderEntry);
      mySdk = jdkOrderEntry.getJdk();
    }

    @Override
    public boolean isForceSearchingInLibrarySources() {
      return true;
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      final boolean inSources1 = myIndex.isInLibrarySource(file1);

      if (inSources1 != myIndex.isInLibrarySource(file2)) {
        // consider class A implements Runnable
        //super class Object for class A is found in cls, super interface Runnable is found in cls as well (class A resolve scope is simple modules scope with dependencies)
        //but super class Object for interface Runnable is already found in android sources due to condition above

        //problems with class A extends ArrayList implements List are hidden because they should be shown for class ArrayList
        //but when we open ArrayList, it is already ArrayList from android sources 
        if (!CommonClassNames.JAVA_LANG_OBJECT_SHORT.equals(file1.getNameWithoutExtension())) {
          return inSources1 ? 1 : -1;
        }
      }
      return super.compare(file1, file2);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return super.contains(file) || AndroidInternalRClass.isAndroidInternalR(file, mySdk);
    }
  }
}
