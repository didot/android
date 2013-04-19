package org.jetbrains.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.maven.AndroidFacetImporter2;
import org.jetbrains.android.maven.AndroidFacetImporterBase;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.FacetImporterTestCase;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.Arrays;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetImporterTest extends FacetImporterTestCase<AndroidFacet, AndroidFacetType> {

  private Sdk myJdk;

  @Override
  protected FacetImporter<AndroidFacet, ?, AndroidFacetType> createImporter() {
    return new AndroidFacetImporter2();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myJdk = IdeaTestUtil.getMockJdk17();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(myJdk);
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    final ProjectJdkTable table = ProjectJdkTable.getInstance();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (Sdk sdk : table.getAllJdks()) {
          table.removeJdk(sdk);
        }
      }
    });
  }

  public void testNoSdk() throws Exception {
    importProject(getPomContent("apk", "module", ""));
    assertModules("module");
    assertNull(ModuleRootManager.getInstance(getModule("module")).getSdk());
  }

  public void testNewSdk1() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      final VirtualFile pom1 = createModulePom("module1", getPomContent("apk", "module1", ""));
      final VirtualFile pom2 = createModulePom("module2", getPomContent("apk", "module2", ""));
      importProjects(pom1, pom2);

      assertModules("module1", "module2");
      final Sdk sdk1 = ModuleRootManager.getInstance(getModule("module1")).getSdk();
      final Sdk sdk2 = ModuleRootManager.getInstance(getModule("module2")).getSdk();
      assertEquals(sdk1, sdk2);
      checkSdk(sdk1);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testNewSdk2() throws Exception {
    final Sdk sdk = AndroidTestCase.createAndroidSdk(AndroidTestCase.getTestSdkPath());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });

    importProject(getPomContent("apk", "module", ""));
    assertModules("module");
    final Module module = getModule("module");
    final Sdk mavenSdk = ModuleRootManager.getInstance(module).getSdk();
    assertFalse(sdk.equals(mavenSdk));
    checkSdk(mavenSdk);
  }

  public void testFacetProperties1() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", ""));

      assertModules("module");
      final Module module = getModule("module");
      checkSdk(ModuleRootManager.getInstance(module).getSdk());
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      final JpsAndroidModuleProperties properties = facet.getProperties();
      assertEquals(false, properties.LIBRARY_PROJECT);
      assertEquals("/res", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("", properties.CUSTOM_COMPILER_MANIFEST);
      assertEquals("/libs", properties.LIBS_FOLDER_RELATIVE_PATH);
      assertEquals("/assets", properties.ASSETS_FOLDER_RELATIVE_PATH);
      assertEquals("/target/generated-sources/aidl", properties.GEN_FOLDER_RELATIVE_PATH_AIDL);
      assertEquals("/target/generated-sources/r", properties.GEN_FOLDER_RELATIVE_PATH_APT);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testFacetProperties2() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      importProject(getPomContent(
        "apklib", "module",
        "<androidManifestFile>${project.build.directory}/manifest/AndroidManifest.xml</androidManifestFile>" +
        "<resourceDirectory>${project.build.directory}/resources</resourceDirectory>" +
        "<assetsDirectory>${project.build.directory}/assets</assetsDirectory>" +
        "<nativeLibrariesDirectory>${project.build.directory}/nativeLibs</nativeLibrariesDirectory>"));

      assertModules("module");
      final Module module = getModule("module");
      checkSdk(ModuleRootManager.getInstance(module).getSdk());
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      final JpsAndroidModuleProperties properties = facet.getProperties();
      assertEquals(true, properties.LIBRARY_PROJECT);
      assertEquals("/target/resources", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/target/manifest/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(false, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("", properties.CUSTOM_COMPILER_MANIFEST);
      assertEquals("/target/nativeLibs", properties.LIBS_FOLDER_RELATIVE_PATH);
      assertEquals("/target/assets", properties.ASSETS_FOLDER_RELATIVE_PATH);
      assertEquals("/target/generated-sources/aidl", properties.GEN_FOLDER_RELATIVE_PATH_AIDL);
      assertEquals("/target/generated-sources/r", properties.GEN_FOLDER_RELATIVE_PATH_APT);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testFacetProperties3() throws Exception {
    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      final String projectRootPath = myProjectRoot.getPath();

      // need to have at least one resource file matched to filtered resource pattern
      final File layoutDir = new File(projectRootPath, "res/layout");
      assertTrue(layoutDir.mkdirs());
      assertTrue(new File(layoutDir, "main.xml").createNewFile());

      // need for existing manifest file
      assertTrue(new File(projectRootPath, "AndroidManifest.xml").createNewFile());

      importProject(getPomContent(
        "apk", "module",
        "<androidManifestFile>${project.build.directory}/filtered-manifest/AndroidManifest.xml</androidManifestFile>" +
        "<resourceDirectory>${project.build.directory}/filtered-res</resourceDirectory>",

        "<plugin>" +
        "  <artifactId>maven-resources-plugin</artifactId>" +
        "  <executions>" +
        "    <execution>" +
        "      <phase>initialize</phase>" +
        "      <goals>" +
        "        <goal>resources</goal>" +
        "      </goals>" +
        "    </execution>" +
        "  </executions>" +
        "</plugin>",

        "<resources>" +
        "  <resource>" +
        "    <directory>${project.basedir}</directory>" +
        "    <filtering>true</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-manifest</targetPath>" +
        "    <includes>" +
        "      <include>AndroidManifest.xml</include>" +
        "    </includes>" +
        "  </resource>" +
        "  <resource>" +
        "    <directory>${project.basedir}/res</directory>" +
        "    <filtering>true</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-res</targetPath>" +
        "    <includes>" +
        "      <include>**/*.xml</include>" +
        "    </includes>" +
        "  </resource>" +
        "  <resource>" +
        "    <directory>${project.basedir}/res</directory>" +
        "    <filtering>false</filtering>" +
        "    <targetPath>${project.build.directory}/filtered-res</targetPath>" +
        "    <excludes>" +
        "      <exclude>**/*.xml</exclude>" +
        "    </excludes>" +
        "  </resource>" +
        "</resources>"));


      assertModules("module");
      final Module module = getModule("module");
      checkSdk(ModuleRootManager.getInstance(module).getSdk());
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      final JpsAndroidModuleProperties properties = facet.getProperties();
      assertEquals("/res", properties.RES_FOLDER_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/target/filtered-res", properties.CUSTOM_APK_RESOURCE_FOLDER);
      assertEquals("/AndroidManifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH);
      assertEquals(true, properties.USE_CUSTOM_COMPILER_MANIFEST);
      assertEquals("/target/filtered-manifest/AndroidManifest.xml", properties.CUSTOM_COMPILER_MANIFEST);
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testExternalApklib1() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib"), new File(getRepositoryPath(), "com/myapklib/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "  </dependency>" +
                    "</dependencies>");

      assertModules("module", "~apklib-com_myapklib_1.0");
      final Module module = getModule("module");
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assertNotNull(facet);
      assertFalse(facet.getProperties().LIBRARY_PROJECT);

      final Module apklibModule = getModule("~apklib-com_myapklib_1.0");
      final AndroidFacet apklibFacet = AndroidFacet.getInstance(apklibModule);
      assertNotNull(apklibFacet);
      assertTrue(apklibFacet.getProperties().LIBRARY_PROJECT);

      final Library jarLib = checkAppModuleDeps(module, apklibModule);
      checkApklibModule(apklibModule, jarLib);
      final VirtualFile[] apklibRoots = ModuleRootManager.getInstance(apklibModule).getContentRoots();
      assertEquals(1, apklibRoots.length);
      assertEquals(FileUtil.toCanonicalPath(myDir.getPath() + "/project/gen-external-apklibs/com_myapklib_1.0"), apklibRoots[0].getPath());
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testExternalApklib2() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib"), new File(getRepositoryPath(), "com/myapklib/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      importProject(getPomContent("apk", "module", "") +
                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>com</groupId>\n" +
                    "    <artifactId>myapklib</artifactId>\n" +
                    "    <version>1.0</version>\n" +
                    "    <type>apklib</type>" +
                    "    <scope>provided</scope>" +
                    "  </dependency>" +
                    "</dependencies>");
      assertModules("module");
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  public void testExternalApklib3() throws Exception {
    setRepositoryPath(new File(myDir, "__repo").getPath());
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myapklib"), new File(getRepositoryPath(), "com/myapklib/1.0"));
    FileUtil.copyDir(new File(AndroidTestCase.getTestDataPath() + "/maven/myjar"), new File(getRepositoryPath(), "com/myjar/1.0"));

    AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = AndroidTestCase.getTestSdkPath();
    try {
      final VirtualFile pom1 = createModulePom("module1", getPomContent("apk", "module1", "") +
                                                          "<dependencies>" +
                                                          "  <dependency>" +
                                                          "    <groupId>com</groupId>\n" +
                                                          "    <artifactId>myapklib</artifactId>\n" +
                                                          "    <version>1.0</version>\n" +
                                                          "    <type>apklib</type>" +
                                                          "  </dependency>" +
                                                          "</dependencies>");
      final VirtualFile pom2 = createModulePom("module2", getPomContent("apk", "module2", "") +
                                                          "<dependencies>" +
                                                          "  <dependency>" +
                                                          "    <groupId>com</groupId>\n" +
                                                          "    <artifactId>myapklib</artifactId>\n" +
                                                          "    <version>1.0</version>\n" +
                                                          "    <type>apklib</type>" +
                                                          "  </dependency>" +
                                                          "</dependencies>");
      importProjects(pom1, pom2);
      assertModules("module1", "module2", "~apklib-com_myapklib_1.0");

      final Module module1 = getModule("module1");
      final AndroidFacet facet1 = AndroidFacet.getInstance(module1);
      assertNotNull(facet1);
      assertFalse(facet1.getProperties().LIBRARY_PROJECT);

      final Module module2 = getModule("module1");
      final AndroidFacet facet2 = AndroidFacet.getInstance(module2);
      assertNotNull(facet2);
      assertFalse(facet2.getProperties().LIBRARY_PROJECT);

      final Module apklibModule = getModule("~apklib-com_myapklib_1.0");
      final AndroidFacet apklibFacet = AndroidFacet.getInstance(apklibModule);
      assertNotNull(apklibFacet);
      assertTrue(apklibFacet.getProperties().LIBRARY_PROJECT);

      final Library jarLib1 = checkAppModuleDeps(module1, apklibModule);
      final Library jarLib2 = checkAppModuleDeps(module2, apklibModule);
      assertEquals(jarLib1, jarLib2);
      checkApklibModule(apklibModule, jarLib1);
      final VirtualFile[] apklibRoots = ModuleRootManager.getInstance(apklibModule).getContentRoots();
      assertEquals(1, apklibRoots.length);
      final String apklibRootPath = apklibRoots[0].getPath();
      assertTrue(
        apklibRootPath.equals(FileUtil.toCanonicalPath(myDir.getPath() + "/project/module1/gen-external-apklibs/com_myapklib_1.0")) ||
        apklibRootPath.equals(FileUtil.toCanonicalPath(myDir.getPath() + "/project/module2/gen-external-apklibs/com_myapklib_1.0")));
    }
    finally {
      AndroidFacetImporterBase.ANDROID_SDK_PATH_TEST = null;
    }
  }

  private static void checkApklibModule(Module apklibModule, Library jarLib) {
    final OrderEntry[] apklibDeps = ModuleRootManager.getInstance(apklibModule).getOrderEntries();
    assertEquals(2, apklibDeps.length);
    assertInstanceOf(apklibDeps[0], ModuleSourceOrderEntry.class);
    assertInstanceOf(apklibDeps[1], LibraryOrderEntry.class);
    final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)apklibDeps[1];
    assertEquals(jarLib, libraryOrderEntry.getLibrary());
  }

  private static Library checkAppModuleDeps(Module module, Module apklibModule) {
    final OrderEntry[] deps = ModuleRootManager.getInstance(module).getOrderEntries();
    assertEquals(4, deps.length);
    assertInstanceOf(deps[0], ModuleJdkOrderEntry.class);
    assertEquals("< Maven Android 4.2 Platform >", deps[0].getPresentableName());
    assertInstanceOf(deps[1], ModuleSourceOrderEntry.class);
    assertInstanceOf(deps[2], LibraryOrderEntry.class);
    assertEquals(deps[2].getPresentableName(), "Maven: com:myjar:1.0");
    final Library jarLib = ((LibraryOrderEntry)deps[2]).getLibrary();
    assertNotNull(jarLib);
    final String[] dep3Urls = jarLib.getUrls(OrderRootType.CLASSES);
    assertEquals(1, dep3Urls.length);
    assertTrue(dep3Urls[0].endsWith("com/myjar/1.0/myjar-1.0.jar!/"));
    assertInstanceOf(deps[3], ModuleOrderEntry.class);
    assertEquals(apklibModule, ((ModuleOrderEntry)deps[3]).getModule());
    return jarLib;
  }

  private static String getPomContent(final String packaging, final String artifactId, final String androidPluginConfig) {
    return getPomContent(packaging, artifactId, androidPluginConfig, "", "");
  }

  private static String getPomContent(final String packaging,
                                      final String artifactId,
                                      final String androidConfig,
                                      String plugins,
                                      final String build) {
    return "<groupId>test</groupId>" +
           "<artifactId>" + artifactId + "</artifactId>" +
           "<version>1</version>" +
           "<packaging>" + packaging + "</packaging>" +
           "<build>" +
           "  <plugins>" +
           "    <plugin>" +
           "      <groupId>com.jayway.maven.plugins.android.generation2</groupId>" +
           "      <artifactId>android-maven-plugin</artifactId>" +
           "      <configuration>" +
           "        <sdk>" +
           "          <platform>17</platform>" +
           "        </sdk>" +
           androidConfig +
           "      </configuration>" +
           "    </plugin>" +
           plugins +
           "  </plugins>" +
           build +
           "</build>";
  }

  private void checkSdk(Sdk sdk) {
    assertNotNull(sdk);
    assertEquals("Maven Android 4.2 Platform", sdk.getName());
    assertTrue(FileUtil.pathsEqual(AndroidTestCase.getTestSdkPath(), sdk.getHomePath()));
    assertEquals(AndroidSdkType.getInstance(), sdk.getSdkType());
    final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
    assertNotNull(additionalData);
    assertInstanceOf(additionalData, AndroidSdkAdditionalData.class);
    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)additionalData;
    assertEquals("android-17", data.getBuildTargetHashString());
    assertEquals(myJdk, data.getJavaSdk());
    final HashSet<String> urls = new HashSet<String>(Arrays.asList(sdk.getRootProvider().getUrls(OrderRootType.CLASSES)));
    assertTrue(urls.containsAll(Arrays.asList(myJdk.getRootProvider().getUrls(OrderRootType.CLASSES))));
  }
}
