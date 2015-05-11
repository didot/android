/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.services;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;

/**
 * A class used by plugins to expose their service resources to Android Studio.
 * <p/>
 * Each subclass of this class should be associated with a single service, providing access to the
 * content files (service.xml, recipe.xml, code files, etc.) that make up their service. This
 * allows this base class to instantiate an associated {@link DeveloperService}
 */
public abstract class DeveloperServiceCreator {
  /**
   * Reserved filename for the xml file which defines a service.
   */
  private static final String SERVICE_XML = "service.xml";

  /**
   * Plugins return resources via InputStreams. We create a temporary directory and create Files
   * for each of the InputStreams, which is important because the template parsing APIs we work
   * with require them.
   */
  @NotNull private final File myRootPath;

  public DeveloperServiceCreator() {
    try {
      // TODO: Here, we copy all resources out from the initializer and create local File copies
      // of them, because our template code (see Template.java, RecipeXmlParser.java) requires
      // working with them. A longer term solution is to make our template code work with
      // InputStreams. The biggest obstacle is our template code currently supports recursive
      // directory operations (e.g. copy src/* to dest/), and InputStreams can't point to
      // directories or allow walking a directory.
      myRootPath = new File(FileUtil.generateRandomTemporaryPath(), getResourceRoot());
      myRootPath.deleteOnExit();

      for (String name : getResources()) {
        assert !name.contains("..") : "Initializer resource can't specify relative path";
        File file = new File(myRootPath, name);

        Files.createParentDirs(file);
        assert file.createNewFile();
        String fullName = new File(getResourceRoot(), name).getPath();
        URL resource = getClass().getResource(fullName);
        if (resource == null) {
          throw new FileNotFoundException(String.format("Could not find service file %1$s", fullName));
        }
        Resources.asByteSource(resource).copyTo(Files.asByteSink(file));
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a new {@link DeveloperService}, which can be used to install the service into the
   * associated {@link Module}. Returns {@code null} if the initialization fails for any reason.
   */
  @Nullable
  public final DeveloperService createService(@NotNull Module module) {
    final ServiceContext context = createContext();
    if (AndroidFacet.getInstance(module) == null) {
      throw new IllegalArgumentException(
        String.format("Developer service cannot be associated with non-Android module %s", module.getName()));
    }

    final ServiceXmlParser serviceParser = new ServiceXmlParser(module, myRootPath, context);

    try {
      final InputStream serviceXml = new FileInputStream(new File(myRootPath, SERVICE_XML));
      try {
        WriteCommandAction.runWriteCommandAction(module.getProject(), new Runnable() {
          @Override
          public void run() {
            try {
              SAXParserFactory.newInstance().newSAXParser().parse(serviceXml, serviceParser);
            }
            catch (ParserConfigurationException e) {
              throw new RuntimeException(e);
            }
            catch (SAXException e) {
              throw new RuntimeException(e);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
      finally {
        serviceXml.close();
      }
    }
    catch (Exception e) {
      Logger.getInstance(getClass())
        .warn(String.format("Skipping over invalid service in module %1$s: %2$s", module.getName(), e.getMessage()));
      return null;
    }

    return new DeveloperService(serviceParser);
  }

  /**
   * Returns the root path that all resource paths returned by {@link #getResources()} live under.
   */
  @NotNull
  protected abstract String getResourceRoot();

  /**
   * Returns the path to all resources required by this service, so that the external code loading
   * this plugin can find them. One of the resources MUST be named "service.xml".
   */
  // TODO: Revisit this and eliminate if possible. It exists so we can fetch all resources
  // under getResourceRoot() as input streams and convert them to files.
  @NotNull
  protected abstract String[] getResources();

  /**
   * Create a context (a package of methods and variables, essentially) for the service UI to be
   * able to bind to.
   */
  @NotNull
  protected abstract ServiceContext createContext();
}
