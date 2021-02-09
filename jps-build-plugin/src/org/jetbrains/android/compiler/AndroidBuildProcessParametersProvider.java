package org.jetbrains.android.compiler;

import com.android.prefs.AndroidLocation;
import com.android.tools.pixelprobe.util.Strings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.internal.InternalFutureFailureAccess;
import com.google.gson.Gson;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.istack.XMLStreamReaderToContentHandler;
import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
import java.util.ArrayList;
import java.util.List;
import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.gradle.tooling.BuildException;
import org.jetbrains.annotations.NotNull;

public class AndroidBuildProcessParametersProvider extends BuildProcessParametersProvider {

  private static final @NotNull Logger LOG = Logger.getInstance(AndroidBuildProcessParametersProvider.class);

  @NotNull
  @Override
  public List<String> getClassPath() {
    return getJarsContainingClasses(
      Gson.class,

      // guava
      ImmutableList.class,                      // guava
      InternalFutureFailureAccess.class,        // guava/failureaccess (for apkzlib)

      // bouncycastle (for apksig)
      BouncyCastleProvider.class,               // bcprov
      X509CertificateHolder.class,              // bcpkix

      //JAXB for studio.android.sdktools.sdklib on Java 11
      JAXBContext.class,                        // jaxb-api
      JAXBContextImpl.class,                    // jaxb-runtime
      XMLStreamReaderToContentHandler.class,    // istack-commons-runtime
      DataSource.class,                         // javaee.jar

      AndroidLocation.class,
      BuildException.class                      // gradle tooling
    );
  }

  @VisibleForTesting
  List<String> getJarsContainingClasses(Class<?>... classes) {
    List<String> foundItems = new ArrayList<>(classes.length);
    List<Class<?>> missingItems = new ArrayList<>(classes.length);
    for (Class<?> aClass : classes) {
      String path = PathManager.getJarPathForClass(aClass);
      if (path != null) {
        foundItems.add(path);
      }
      else {
        missingItems.add(aClass);
      }
    }

    reportMissingClasses(missingItems);
    return foundItems;
  }

  @VisibleForTesting
  void reportMissingClasses(@NotNull List<Class<?>> classesWithMissingJars) {
    if (!classesWithMissingJars.isEmpty()) {
      String message = "Could not find JARs for classes:" + Strings.join(classesWithMissingJars, ", ");
      LOG.warn(message);
      boolean unitTestMode = ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode();
      if (unitTestMode) {
        LOG.error("There is a problem with JPS classpath configuration: " + message);
      }
    }
  }
}
