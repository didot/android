/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_TEMPLATES;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API_STRING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_APPLICATION_THEME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_JAVA_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API_LEVEL;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_RES_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SOURCE_PROVIDER_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API_STRING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_THEME_EXISTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.getBuildApiString;
import static com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy;
import static com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories;
import static com.android.tools.idea.wizard.WizardConstants.MODULE_TEMPLATE_NAME;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.lang.annotation.ElementType.METHOD;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;

import com.android.SdkConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.testutils.TestUtils;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.lint.LintIdeIssueRegistry;
import com.android.tools.idea.lint.LintIdeRequest;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.KotlinSupport;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.WaitFor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

/**
 * Test for template instantiation.
 * <p>
 * Remaining work on templates:
 * <ul>
 * <li>Make the project templates work for API=1 (currently requires API 7); with API 1
 * you get a build error because included libraries have targetSdkVersion higher than 1</li>
 * <li>Fix type conversion, to make the service and fragment templates work</li>
 * </ul>
 *
 * Remaining work on template test:
 * <ul>
 * <li>Add mechanism to ensure that test coverage is comprehensive (made difficult by </li>
 * <li>Start using new NewProjectModel etc to initialise TemplateParameters and set parameter values</li>
 * <li>Fix clean model syncing, and hook up clean lint checks</li>
 * <li>We should test more combinations of parameters</li>
 * <li>We should test all combinations of build tools</li>
 * <li>Test creating a project <b>without</b> a template</li>
 * </ul>
 */
@SuppressWarnings("deprecation") // We need to move away from the old Wizard framework usage
public class TemplateTest extends AndroidGradleTestCase {
  /**
   * A UsageTracker implementation that allows introspection of logged metrics in tests.
   */
  private TestUsageTracker myUsageTracker;

  /**
   * Whether we should run comprehensive tests or not. This flag allows a simple run to just check a small set of
   * template combinations, and when the flag is set on the build server, a much more comprehensive battery of
   * checks to be performed.
   */
  private static final boolean COMPREHENSIVE =
    Boolean.parseBoolean(System.getProperty("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE")) ||
    Boolean.TRUE.toString().equals(System.getenv("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE"));

  /**
   * Whether we should run these tests or not.
   */
  private static final boolean DISABLED =
    Boolean.parseBoolean(System.getProperty("DISABLE_STUDIO_TEMPLATE_TESTS")) ||
    Boolean.TRUE.toString().equals(System.getenv("DISABLE_STUDIO_TEMPLATE_TESTS"));

  /**
   * Whether we should enforce that lint passes cleanly on the projects
   */
  private static final boolean CHECK_LINT = false; // Needs work on closing projects cleanly

  /**
   * Manual sdk version selections
   **/
  private static final int MANUAL_BUILD_API =
    Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_BUILD_API", "-1"));
  private static final int MANUAL_MIN_API =
    Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_MIN_API", "-1"));
  private static final int MANUAL_TARGET_API =
    Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_TARGET_API", "-1"));

  /**
   * The following templates are known to be broken! We need to work through these and fix them such that tests
   * on them can be re-enabled.
   */
  private static boolean isBroken(@NotNull String templateName) {
    // See http://b.android.com/253296
    if (SystemInfo.isWindows) {
      if ("AidlFile".equals(templateName)) return true;
    }
    return false;
  }

  /**
   * The following templates parameters are not very interesting (change only one small bit of text etc). We can skip them when not running
   * in comprehensive mode.
   */
  private static final Set<String> SKIPPABLE_PARAMETERS = ImmutableSet.of(
    "instantAppActivityRouteType",
    "enableProGuard" // not exposed in UI
  );

  /**
   * Flags used to quickly check each template once (for one version), to get
   * quicker feedback on whether something is broken instead of waiting for
   * all the versions for each template first
   */
  private static final boolean TEST_FEWER_API_VERSIONS = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_MIN_SDK = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_BUILD_TARGET = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_TARGET_SDK_VERSION = !COMPREHENSIVE;

  private static boolean ourValidatedTemplateManager;

  private final StringEvaluator myStringEvaluator = new StringEvaluator();

  // TODO: this is used only in TemplateTest. We should pass this value without changing template values.
  final static String ATTR_CREATE_ACTIVITY = "createActivity";

  public TemplateTest() {
  }

  @Override
  protected boolean createDefaultProject() {
    // We'll be creating projects manually except for the following tests
    String testName = getName();
    return testName.equals("testTemplateFormatting")
           || testName.equals("testCreateGradleWrapper");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    VirtualTimeScheduler scheduler = new VirtualTimeScheduler();
    myUsageTracker = new TestUsageTracker(scheduler);
    UsageTracker.setWriterForTest(myUsageTracker);
    myApiSensitiveTemplate = true;
    if (!ourValidatedTemplateManager) {
      ourValidatedTemplateManager = true;
      File templateRootFolder = TemplateManager.getTemplateRootFolder();
      if (templateRootFolder == null) {
        AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
        if (sdkData == null) {
          fail("Couldn't find SDK manager");
        }
        else {
          System.out.println("getTestSdkPath= " + TestUtils.getSdk());
          System.out.println("getPlatformDir=" + TestUtils.getLatestAndroidPlatform());
          String location = sdkData.getLocation().getPath();
          System.out.println("Using SDK at " + location);
          VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(location);
          System.out.println("Version check=" + result.getRevision());
          File file = new File(location);
          if (!file.exists()) {
            System.out.println("SDK doesn't exist");
          }
          else {
            File folder = new File(location, FD_TOOLS + File.separator + FD_TEMPLATES);
            boolean exists = folder.exists();
            System.out.println("Template folder exists=" + exists + " for " + folder);
          }
        }
      }
    }
    // Replace the default RepositoryUrlManager with one that enables repository checks in tests. (myForceRepositoryChecksInTests)
    // This is necessary to fully resolve dynamic gradle coordinates such as ...:appcompat-v7:+ => appcompat-v7:25.3.1
    // keeping it exactly the same as they are resolved within the NPW flow.
    new IdeComponents(null, getTestRootDisposable()).replaceApplicationService(
      RepositoryUrlManager.class,
      new RepositoryUrlManager(IdeGoogleMavenRepository.INSTANCE, OfflineIdeGoogleMavenRepository.INSTANCE, true));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myUsageTracker.close();
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * If true, check this template with all the interesting (
   * {@link #isInterestingApiLevel(int, int)}) api versions
   */
  private boolean myApiSensitiveTemplate;


  /**
   * Is the given api level interesting for testing purposes? This is used to
   * skip gaps, such that we for example only check say api 8, 9, 11, 14, etc
   * -- versions where the <b>templates</b> are doing conditional changes. To
   * be EXTRA comprehensive, occasionally try returning true unconditionally
   * here to test absolutely everything.
   */
  private boolean isInterestingApiLevel(int api, int manualApi) {
    // If a manual api version was specified then accept only that version:
    if (manualApi > 0) {
      return api == manualApi;
    }

    // For templates that aren't API sensitive, only test with latest API
    if (!myApiSensitiveTemplate) {
      // Use HIGHEST_KNOWN_STABLE_API rather than HIGHEST_KNOWN_API which
      // can point to preview releases.
      return api == SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    }

    // Always accept the highest known version
    if (api == SdkVersionInfo.HIGHEST_KNOWN_STABLE_API) {
      return true;
    }

    // Relevant versions, used to prune down the set of targets we need to
    // check on. This is determined by looking at the minApi and minBuildApi
    // versions found in the template.xml files.
    switch (api) {
      case 14:
      case 16:
      case 21:
      case 23:
        return true;
      case 25:
      case 28:
        return !TEST_FEWER_API_VERSIONS;
      default:
        return false;
    }
  }

  private final ProjectStateCustomizer withKotlin = ((templateMap, projectMap) -> {
    projectMap.put(ATTR_KOTLIN_VERSION, TestUtils.getKotlinVersionForTests());
    projectMap.put(ATTR_LANGUAGE, Language.KOTLIN.toString());
    templateMap.put(ATTR_LANGUAGE, Language.KOTLIN.toString());
    templateMap.put(ATTR_PACKAGE_NAME, "test.pkg.in"); // Add in a Kotlin keyword ("in") in the package name to trigger escape code too
  });

  //--- Activity templates ---

  @TemplateCheck
  public void testNewBasicActivity() throws Exception {
    checkCreateTemplate("activities", "BasicActivity", false);
  }

  @TemplateCheck
  public void testNewBasicActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "BasicActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithBasicActivity() throws Exception {
    checkCreateTemplate("activities", "BasicActivity", true);
  }

  @TemplateCheck
  public void testNewThingsActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidThingsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithThingsActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidThingsActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithThingsActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "AndroidThingsActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewEmptyActivity() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", false);
  }

  @TemplateCheck
  public void testNewEmptyActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithEmptyActivity() throws Exception {
    checkCreateTemplate("activities", "EmptyActivity", true);
  }

  @TemplateCheck
  public void testNewViewModelActivity() throws Exception {
    checkCreateTemplate("activities", "ViewModelActivity", false);
  }

  @TemplateCheck
  public void testNewViewModelActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "ViewModelActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithViewModelActivity() throws Exception {
    checkCreateTemplate("activities", "ViewModelActivity", true);
  }

  @TemplateCheck
  public void testNewTabbedActivity() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithTabbedActivity() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithTabbedActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "TabbedActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewBlankWearActivity() throws Exception {
    checkCreateTemplate("activities", "BlankWearActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithBlankWearActivity() throws Exception {
    checkCreateTemplate("activities", "BlankWearActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithBlankWearActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "BlankWearActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewNavigationDrawerActivity() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithNavigationDrawerActivity() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", true);
  }

  @TemplateCheck
  public void testNewNavigationDrawerActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "NavigationDrawerActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewMasterDetailFlow() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", false);
  }

  @TemplateCheck
  public void testNewProjectWithMasterDetailFlow() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", true);
  }

  @TemplateCheck
  public void testNewProjectWithMasterDetailFlowWithKotlin() throws Exception {
    checkCreateTemplate("activities", "MasterDetailFlow", true, withKotlin);
  }

  @TemplateCheck
  public void testNewFullscreenActivity() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithFullscreenActivity() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithFullscreenActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "FullscreenActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewLoginActivity() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithLoginActivity() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithLoginActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "LoginActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewScrollActivity() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithScrollActivity() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithScrollActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "ScrollActivity", true,
                        (templateMap, projectMap) -> {
                          withKotlin.customize(templateMap, projectMap);
                          templateMap.put("menuName", "menu_scroll_activity");
                        });
  }

  @TemplateCheck
  public void testNewSettingsActivity() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithSettingsActivity() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithSettingsActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "SettingsActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testBottomNavigationActivity() throws Exception {
    checkCreateTemplate("activities", "BottomNavigationActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithBottomNavigationActivity() throws Exception {
    checkCreateTemplate("activities", "BottomNavigationActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithBottomNavigationActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "BottomNavigationActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewTvActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", false);
  }

  @TemplateCheck
  public void testNewTvActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", false, withKotlin);
  }

  @TemplateCheck
  public void testNewProjectWithTvActivity() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithTvActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "AndroidTVActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testGoogleAdMobAdsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleAdMobAdsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleAdMobAdsActivity", true);
  }

  @TemplateCheck
  public void testGoogleMapsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleMapsActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsActivity", true);
  }

  @TemplateCheck
  public void testGoogleMapsWearActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", false);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleMapsWearActivity() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true);
  }

  @TemplateCheck
  public void testNewProjectWithGoogleMapsWearActivityWithKotlin() throws Exception {
    checkCreateTemplate("activities", "GoogleMapsWearActivity", true, withKotlin);
  }

  @TemplateCheck
  public void testNewAutomotiveProjectWithMediaService() throws Exception {
    checkCreateTemplate("other", "AutomotiveMediaService", true);
  }

  @TemplateCheck
  public void testNewAutomotiveProjectWithMediaServiceWithKotlin() throws Exception {
    checkCreateTemplate("other", "AutomotiveMediaService", true, withKotlin);
  }

  //--- Non-activity templates ---

  @TemplateCheck
  public void testNewBroadcastReceiver() throws Exception {
    // No need to try this template with multiple platforms, one is adequate
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "BroadcastReceiver");
  }

  @TemplateCheck
  public void testNewBroadcastReceiverWithKotlin() throws Exception {
    // No need to try this template with multiple platforms, one is adequate
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "BroadcastReceiver", false, withKotlin);
  }

  @TemplateCheck
  public void testNewContentProvider() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ContentProvider");
  }

  @TemplateCheck
  public void testNewContentProviderWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ContentProvider", false, withKotlin);
  }

  @TemplateCheck
  public void testNewSliceProvider() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "SliceProvider", false);
  }

  @TemplateCheck
  public void testNewSliceProviderWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "SliceProvider", false, withKotlin);
  }

  @TemplateCheck
  public void testNewCustomView() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "CustomView");
  }

  @TemplateCheck
  public void testNewIntentService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "IntentService");
  }

  @TemplateCheck
  public void testNewIntentServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "IntentService", false, withKotlin);
  }

  @TemplateCheck
  public void testNewListFragment() throws Exception {
    myApiSensitiveTemplate = true;
    checkCreateTemplate("fragments", "ListFragment");
  }

  @TemplateCheck
  public void testNewListFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = true;
    checkCreateTemplate("fragments", "ListFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewModalBottomSheet() throws Exception {
    myApiSensitiveTemplate = true;
    checkCreateTemplate("fragments", "ModalBottomSheet");
  }

  @TemplateCheck
  public void testNewAppWidget() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AppWidget");
  }

  @TemplateCheck
  public void testNewBlankFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "BlankFragment");
  }

  @TemplateCheck
  public void testNewBlankFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "BlankFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewSettingsFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "SettingsFragment", true);
  }

  @TemplateCheck
  public void testNewSettingsFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "SettingsFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewViewModelFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ViewModelFragment");
  }

  @TemplateCheck
  public void testNewViewModelFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ViewModelFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewScrollFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ScrollFragment");
  }

  @TemplateCheck
  public void testNewScrollFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "ScrollFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewFullscreenFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "FullscreenFragment");
  }

  @TemplateCheck
  public void testNewFullscreenFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "FullscreenFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewGoogleMapsFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleMapsFragment");
  }

  @TemplateCheck
  public void testNewGoogleMapsFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleMapsFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewGoogleAdMobFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment");
  }

  @TemplateCheck
  public void testNewGoogleAdMobFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "GoogleAdMobAdsFragment", false, withKotlin);
  }

  public void testLoginFragment() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "LoginFragment");
  }

  @TemplateCheck
  public void testLoginFragmentWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("fragments", "LoginFragment", false, withKotlin);
  }

  @TemplateCheck
  public void testNewService() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Service");
  }

  @TemplateCheck
  public void testNewServiceWithKotlin() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "Service", false, withKotlin);
  }

  @TemplateCheck
  public void testNewAidlFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AidlFile");
  }

  @TemplateCheck
  public void testNewAidlFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AidlFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "foo"));
  }

  @TemplateCheck
  public void testAndroidManifest() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AndroidManifest", false,
                        (t, p) -> t.put("newLocation", "src/foo/AndroidManifest.xml"));
  }

  @TemplateCheck
  public void testAssetsFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AssetsFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/assets/"));
  }

  @TemplateCheck
  public void testJavaAndJniFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "JavaFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/java"));
    checkCreateTemplate("other", "JniFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/jni"));
  }

  @TemplateCheck
  public void testFontFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "FontFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/res/font"));
  }

  @TemplateCheck
  public void testRawFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "RawFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/res/raw"));
  }

  @TemplateCheck
  public void testXmlFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "XmlFolder", false,
                        (templateMap, projectMap) -> templateMap.put("newLocation", "src/main/res/xml"));
  }

  @TemplateCheck
  public void testRenderSourceFolder() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "RsFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/rs"));
    checkCreateTemplate("other", "ResFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/res"));
    checkCreateTemplate("other", "ResourcesFolder", false,
                        (t, p) -> t.put("newLocation", "src/main/res"));
  }

  @TemplateCheck
  public void testNewLayoutResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "LayoutResourceFile");
  }

  @TemplateCheck
  public void testNewAppActionsResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "AppActionsResourceFile");
  }

  @TemplateCheck
  public void testAutomotiveMediaService() throws Exception {
    checkCreateTemplate("other", "AutomotiveMediaService", false);
  }

  @TemplateCheck
  public void testAutomotiveMediaServiceWithKotlin() throws Exception {
    checkCreateTemplate("other", "AutomotiveMediaService", false, withKotlin);
  }

  @TemplateCheck
  public void testAutomotiveMessagingService() throws Exception {
    checkCreateTemplate("other", "AutomotiveMessagingService");
  }

  @TemplateCheck
  public void testAutomotiveMessagingServiceWithKotlin() throws Exception {
    checkCreateTemplate("other", "AutomotiveMessagingService", false , withKotlin);
  }

  @TemplateCheck
  public void testWatchFaceService() throws Exception {
    checkCreateTemplate("other", "WatchFaceService");
  }

  @TemplateCheck
  public void testWatchFaceServiceWithKotlin() throws Exception {
    checkCreateTemplate("other", "WatchFaceService", true, withKotlin);
  }

  @TemplateCheck
  public void testNewValueResourceFile() throws Exception {
    myApiSensitiveTemplate = false;
    checkCreateTemplate("other", "ValueResourceFile");
  }

  public void testAllTemplatesCovered() throws Exception {
    if (DISABLED) {
      return;
    }

    new CoverageChecker().testAllTemplatesCovered();
  }


  //--- Special cases ---

  public void testJdk7() throws Exception {
    if (DISABLED) {
      return;
    }
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    if (IdeSdks.getInstance().isJdk7Supported(sdkData)) {
      IAndroidTarget[] targets = sdkData.getTargets();
      IAndroidTarget target = targets[targets.length - 1];
      Map<String, Object> overrides = new HashMap<>();
      overrides.put(ATTR_JAVA_VERSION, "1.7");
      TestNewProjectWizardState state = createNewProjectState(true, sdkData, getDefaultModuleTemplate());

      // TODO: Allow null activity state!
      File activity = findTemplate("activities", "BasicActivity");
      TestTemplateWizardState activityState = state.getActivityTemplateState();
      assertNotNull(activity);
      activityState.setTemplateLocation(activity);

      checkApiTarget(19, 19, target, state, "Test17", null, overrides, null);
    }
    else {
      System.out.println("JDK 7 not supported by current SDK manager: not testing");
    }
  }

  public void testJdk5() throws Exception {
    if (DISABLED) {
      return;
    }
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    IAndroidTarget[] targets = sdkData.getTargets();
    IAndroidTarget target = targets[targets.length - 1];
    Map<String, Object> overrides = new HashMap<>();
    overrides.put(ATTR_JAVA_VERSION, "1.5");
    TestNewProjectWizardState state = createNewProjectState(true, sdkData, getDefaultModuleTemplate());

    // TODO: Allow null activity state!
    File activity = findTemplate("activities", "BasicActivity");
    TestTemplateWizardState activityState = state.getActivityTemplateState();
    assertNotNull(activity);
    activityState.setTemplateLocation(activity);

    checkApiTarget(14, 18, target, state, "Test15", null, overrides, null);
  }

  public void testTemplateFormatting() throws Exception {
    Template template = Template.createFromPath(new File(getTestDataPath(), FileUtil.join("templates", "TestTemplate")).getCanonicalFile());
    RenderingContext context = createRenderingContext(template,
                                                      myFixture.getProject(),
                                                      new File(myFixture.getTempDirPath()),
                                                      new File("dummy"),
                                                      null);
    template.render(context, false);
    FileDocumentManager.getInstance().saveAllDocuments();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile desired = fileSystem.findFileByIoFile(new File(getTestDataPath(),
                                                               FileUtil.join("templates", "TestTemplate", "MergedStringsFile.xml")));
    assertNotNull(desired);
    VirtualFile actual = fileSystem.findFileByIoFile(new File(myFixture.getTempDirPath(),
                                                              FileUtil.join("values", "TestTargetResourceFile.xml")));
    assertNotNull(actual);
    desired.refresh(false, false);
    actual.refresh(false, false);
    PlatformTestUtil.assertFilesEqual(desired, actual);
  }

  public void testRelatedParameters() {
    Template template = Template.createFromPath(new File(getTestDataPath(), FileUtil.join("templates", "TestTemplate")));
    TemplateMetadata templateMetadata = template.getMetadata();
    assertNotNull(templateMetadata);
    Parameter layoutName = templateMetadata.getParameter("layoutName");
    Parameter activityClass = templateMetadata.getParameter("activityClass");
    Parameter mainFragment = templateMetadata.getParameter("mainFragment");
    Parameter activityTitle = templateMetadata.getParameter("activityTitle");
    Parameter detailsActivity = templateMetadata.getParameter("detailsActivity");
    Parameter detailsLayoutName = templateMetadata.getParameter("detailsLayoutName");
    assertSameElements(templateMetadata.getRelatedParams(layoutName), detailsLayoutName);
    assertSameElements(templateMetadata.getRelatedParams(activityClass), detailsActivity, mainFragment);
    assertSameElements(templateMetadata.getRelatedParams(mainFragment), detailsActivity, activityClass);
    assertEmpty(templateMetadata.getRelatedParams(activityTitle));
    assertSameElements(templateMetadata.getRelatedParams(detailsActivity), activityClass, mainFragment);
    assertSameElements(templateMetadata.getRelatedParams(detailsLayoutName), layoutName);
  }

  //--- Test support code ---

  /**
   * Checks the given template in the given category, adding it to an existing project
   */
  private void checkCreateTemplate(String category, String name) throws Exception {
    checkCreateTemplate(category, name, false);
  }

  private void checkCreateTemplate(String category, String name, boolean createWithProject) throws Exception {
    checkCreateTemplate(category, name, createWithProject, null);
  }

  /**
   * Checks the given template in the given category
   *
   * @param category          the template category
   * @param name              the template name
   * @param createWithProject whether the template should be created as part of creating the project (which should
   *                          only be done for activities), or whether it should be added as as a separate template
   *                          into an existing project (which is created first, followed by the template)
   * @param customizer        An instance of {@link ProjectStateCustomizer} used for providing template and project overrides.
   * @throws Exception
   */
  protected void checkCreateTemplate(String category, String name, boolean createWithProject,
                                     @Nullable ProjectStateCustomizer customizer) throws Exception {
    if (DISABLED) {
      return;
    }
    File templateFile = findTemplate(category, name);
    assertNotNull(templateFile);
    if (isBroken(templateFile.getName())) {
      return;
    }
    Stopwatch stopwatch = Stopwatch.createStarted();
    if (customizer == null) {
      checkTemplate(templateFile, createWithProject);
    }
    else {
      checkTemplate(templateFile, createWithProject, customizer);
    }
    stopwatch.stop();
    System.out.println("Checked " + templateFile.getName() + " successfully in " + stopwatch.toString());
  }

  private File findTemplate(String category, String name) {
    ensureSdkManagerAvailable();
    File templateRootFolder = TemplateManager.getTemplateRootFolder();
    assertNotNull(templateRootFolder);
    File file = new File(templateRootFolder, category + File.separator + name);
    assertTrue(file.getPath(), file.exists());
    return file;
  }

  private static TestNewProjectWizardState createNewProjectState(boolean createWithProject, AndroidSdkData sdkData, Template moduleTemplate) {
    TestNewProjectWizardState projectState = new TestNewProjectWizardState(moduleTemplate);
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    Template.convertApisToInt(moduleState.getParameters());
    moduleState.put(ATTR_CREATE_ACTIVITY, createWithProject);
    moduleState.put(ATTR_MODULE_NAME, "TestModule");
    moduleState.put(ATTR_PACKAGE_NAME, "test.pkg");
    new TemplateValueInjector(moduleState.getParameters())
      .addGradleVersions(null);

    BuildToolInfo buildTool = sdkData.getLatestBuildTool();
    if (buildTool != null) {
      moduleState.put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    return projectState;
  }

  private void checkTemplate(File templateFile, boolean createWithProject) throws Exception {
    checkTemplate(templateFile, createWithProject, null, null);
  }

  private void checkTemplate(File templateFile, boolean createWithProject, @NotNull ProjectStateCustomizer customizer) throws Exception {
    Map<String, Object> templateOverrides = new HashMap<>();
    Map<String, Object> projectOverrides = new HashMap<>();
    customizer.customize(templateOverrides, projectOverrides);
    checkTemplate(templateFile, createWithProject, templateOverrides, projectOverrides);
  }

  private void checkTemplate(File templateFile,
                             boolean createWithProject,
                             @Nullable Map<String, Object> overrides,
                             @Nullable Map<String, Object> projectOverrides) throws Exception {
    if (isBroken(templateFile.getName())) {
      return;
    }

    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    TestNewProjectWizardState projectState = createNewProjectState(createWithProject, sdkData, getModuleTemplateForFormFactor(templateFile));

    String projectNameBase = templateFile.getName();

    TestTemplateWizardState activityState = projectState.getActivityTemplateState();
    activityState.setTemplateLocation(templateFile);

    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();

    // Iterate over all (valid) combinations of build target, minSdk and targetSdk
    // TODO: Assert that the SDK manager has a minimum set of SDKs installed needed to be certain
    // the test is comprehensive
    // For now make sure there's at least one
    boolean ranTest = false;
    int lowestMinApiForProject = Math.max(Integer.parseInt((String)moduleState.get(ATTR_MIN_API)), moduleState.getTemplateMetadata().getMinSdk());

    IAndroidTarget[] targets = sdkData.getTargets();
    for (int i = targets.length - 1; i >= 0; i--) {
      IAndroidTarget target = targets[i];
      if (!target.isPlatform()) {
        continue;
      }
      if (!isInterestingApiLevel(target.getVersion().getApiLevel(), MANUAL_BUILD_API)) {
        continue;
      }

      TemplateMetadata activityMetadata = activityState.getTemplateMetadata();
      TemplateMetadata moduleMetadata = moduleState.getTemplateMetadata();

      int lowestSupportedApi = Math.max(lowestMinApiForProject, activityMetadata.getMinSdk());

      for (int minSdk = lowestSupportedApi;
           minSdk <= SdkVersionInfo.HIGHEST_KNOWN_API;
           minSdk++) {
        // Don't bother checking *every* single minSdk, just pick some interesting ones
        if (!isInterestingApiLevel(minSdk, MANUAL_MIN_API)) {
          continue;
        }

        for (int targetSdk = minSdk;
             targetSdk <= SdkVersionInfo.HIGHEST_KNOWN_API;
             targetSdk++) {
          if (!isInterestingApiLevel(targetSdk, MANUAL_TARGET_API)) {
            continue;
          }

          String status = validateTemplate(moduleMetadata, minSdk, target.getVersion().getApiLevel());
          if (status != null) {
            continue;
          }

          // Also make sure activity is enabled for these versions
          status = validateTemplate(activityMetadata, minSdk, target.getVersion().getApiLevel());
          if (status != null) {
            continue;
          }

          // Iterate over all new new project templates

          // should I try all options of theme with all platforms?
          // or just try all platforms, with one setting for each?
          // doesn't seem like I need to multiply
          // just pick the best setting that applies instead for each platform
          Collection<Parameter> parameters = moduleMetadata.getParameters();
          // Does it have any enums?
          boolean hasEnums = parameters.stream().anyMatch(p -> p.type == Parameter.Type.ENUM);
          if (!hasEnums || overrides != null) {
            String base = projectNameBase
                          + "_min_" + minSdk
                          + "_target_" + targetSdk
                          + "_build_" + target.getVersion().getApiLevel();
            if (overrides != null) {
              base += "_overrides";
            }
            checkApiTarget(minSdk, targetSdk, target, projectState, base, activityState, overrides, projectOverrides);
            ranTest = true;
          }
          else {
            // Handle all enums here. None of the projects have this currently at this level
            // so we will bite the bullet when we first encounter it.
            fail("Not expecting enums at the root level");
          }

          if (TEST_JUST_ONE_TARGET_SDK_VERSION) {
            break;
          }
        }

        if (TEST_JUST_ONE_MIN_SDK) {
          break;
        }
      }

      if (TEST_JUST_ONE_BUILD_TARGET) {
        break;
      }
    }
    assertTrue("Didn't run any tests! Make sure you have the right platforms installed.", ranTest);
  }

  /**
   * Checks creating the given project and template for the given SDK versions
   */
  private void checkApiTarget(
    int minSdk,
    int targetSdk,
    @NotNull IAndroidTarget target,
    @NotNull TestNewProjectWizardState projectState,
    @NotNull String projectNameBase,
    @Nullable TestTemplateWizardState activityState,
    @Nullable Map<String, Object> overrides,
    @Nullable Map<String, Object> projectOverrides) throws Exception {

    TestTemplateWizardState moduleState =  projectState.getModuleTemplateState();
    Boolean createActivity = (Boolean)moduleState.get(ATTR_CREATE_ACTIVITY);
    if (createActivity == null) {
      createActivity = true;
    }
    TestTemplateWizardState templateState = createActivity ? projectState.getActivityTemplateState() : activityState;
    assertNotNull(templateState);

    moduleState.put(ATTR_MIN_API, Integer.toString(minSdk));
    moduleState.put(ATTR_MIN_API_LEVEL, minSdk);
    moduleState.put(ATTR_TARGET_API, targetSdk);
    moduleState.put(ATTR_TARGET_API_STRING, Integer.toString(targetSdk));
    moduleState.put(ATTR_BUILD_API, target.getVersion().getApiLevel());
    moduleState.put(ATTR_BUILD_API_STRING, getBuildApiString(target.getVersion()));

    // Next check all other parameters, cycling through booleans and enums.
    Template templateHandler = templateState.getTemplate();
    assertNotNull(templateHandler);
    TemplateMetadata template = templateHandler.getMetadata();
    assertNotNull(template);
    Iterable<Parameter> parameters = template.getParameters();

    if (!createActivity) {
      templateState.setParameterDefaults();
    }
    else {
      TemplateMetadata moduleMetadata = moduleState.getTemplate().getMetadata();
      assertNotNull(moduleMetadata);
      parameters = Iterables.concat(parameters, moduleMetadata.getParameters());
    }

    if (overrides != null) {
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        templateState.put(entry.getKey(), entry.getValue());
      }
    }
    if (projectOverrides != null) {
      for (Map.Entry<String, Object> entry : projectOverrides.entrySet()) {
        moduleState.put(entry.getKey(), entry.getValue());
      }
    }

    String projectName;
    for (Parameter parameter : parameters) {
      if (parameter.type == Parameter.Type.SEPARATOR || parameter.type == Parameter.Type.STRING) {
        // TODO: Consider whether we should attempt some strings here
        continue;
      }

      // Skip parameters that don't do much
      if (!COMPREHENSIVE && SKIPPABLE_PARAMETERS.contains(parameter.id)) {
        continue;
      }

      if (overrides != null && overrides.containsKey(parameter.id)) {
        continue;
      }

      assertNotNull(parameter.id);

      // The initial (default value); revert to this one after cycling,
      Object initial = templateState.get(parameter.id);
      if (initial == null) {
        if (parameter.type == Parameter.Type.BOOLEAN) {
          initial = Boolean.valueOf(parameter.initial);
        }
        else {
          initial = parameter.initial;
        }
      }

      if (parameter.type == Parameter.Type.ENUM) {
        List<Element> options = parameter.getOptions();
        for (Element element : options) {
          Option option = Option.get(element);
          String optionId = option.id;
          int optionMinSdk = option.minSdk;
          int optionMinBuildApi = option.minBuild;
          int projectMinApi = moduleState.getInt(ATTR_MIN_API_LEVEL);
          int projectBuildApi = moduleState.getInt(ATTR_BUILD_API);
          if (projectMinApi >= optionMinSdk &&
              projectBuildApi >= optionMinBuildApi &&
              !optionId.equals(initial)) {
            templateState.put(parameter.id, optionId);
            projectName = projectNameBase + "_" + parameter.id + "_" + optionId;
            checkProject(projectName, projectState, activityState);
            if (!COMPREHENSIVE) {
              break;
            }
          }
        }
      }
      else {
        assert parameter.type == Parameter.Type.BOOLEAN;
        if (parameter.id.equals(ATTR_IS_LAUNCHER) && createActivity) {
          // Skipping this one: always true when launched from new project
          continue;
        }
        boolean initialValue = (boolean)initial;
        // For boolean values, only run checkProject in the non-default setting.
        // The default value is already used when running checkProject in the default state for all variables.
        boolean value = !initialValue;
        templateState.put(parameter.id, value);
        projectName = projectNameBase + "_" + parameter.id + "_" + value;
        checkProject(projectName, projectState, activityState);
      }
      templateState.put(parameter.id, initial);
    }
    projectName = projectNameBase + "_default";
    checkProject(projectName, projectState, activityState);
  }

  private static class Option {
    private final String id;
    private final int minSdk;
    private final int minBuild;

    private Option(String id, int minSdk, int minBuild) {
      this.id = id;
      this.minSdk = minSdk;
      this.minBuild = minBuild;
    }

    private static Option get(Element option) {
      String optionId = option.getAttribute(ATTR_ID);
      String minApiString = option.getAttribute(ATTR_MIN_API);
      int optionMinSdk = 1;
      if (minApiString != null && !minApiString.isEmpty()) {
        try {
          optionMinSdk = Integer.parseInt(minApiString);
        }
        catch (NumberFormatException e) {
          // Templates aren't allowed to contain codenames, should
          // always be an integer
          optionMinSdk = 1;
          fail(e.toString());
        }
      }
      String minBuildApiString = option.getAttribute(ATTR_MIN_BUILD_API);
      int optionMinBuildApi = 1;
      if (minBuildApiString != null && !minBuildApiString.isEmpty()) {
        try {
          optionMinBuildApi = Integer.parseInt(minBuildApiString);
        }
        catch (NumberFormatException e) {
          // Templates aren't allowed to contain codenames, should
          // always be an integer
          optionMinBuildApi = 1;
          fail(e.toString());
        }
      }

      return new Option(optionId, optionMinSdk, optionMinBuildApi);
    }
  }

  private void checkProject(@NotNull String projectName,
                            @NotNull TestNewProjectWizardState projectState,
                            @Nullable TestTemplateWizardState activityState) throws Exception {

    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    boolean checkLib = false;
    if (activityState != null) {
      Template template = activityState.getTemplate();
      assert (template != null);
      TemplateMetadata templateMetadata = template.getMetadata();
      assert (templateMetadata != null);
      checkLib = "Activity".equals(templateMetadata.getCategory()) &&
                 "Mobile".equals(templateMetadata.getFormFactor()) &&
                 !moduleState.getBoolean(ATTR_CREATE_ACTIVITY);

      if (templateMetadata.getAndroidXRequired()) {
        setAndroidSupport(true, moduleState, activityState);
      }
    }

    if (!Boolean.TRUE.equals(moduleState.get(ATTR_ANDROIDX_SUPPORT))) {
      // Make sure we test all templates against androidx
      setAndroidSupport(true, moduleState, activityState);
      checkProjectNow(projectName + "_x", projectState, activityState);
      setAndroidSupport(false, moduleState, activityState);
    }

    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, false);
      activityState.put(ATTR_IS_LIBRARY_MODULE, false);
      activityState.put(ATTR_HAS_APPLICATION_THEME, true);
    }
    checkProjectNow(projectName, projectState, activityState);

    // check that new Activities can be created on lib modules as well as app modules.
    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, true);
      activityState.put(ATTR_IS_LIBRARY_MODULE, true);
      activityState.put(ATTR_HAS_APPLICATION_THEME, false);
      // For a library project a theme doesn't exist. This is derived in the IDE using FmgetApplicationThemeMethod
      moduleState.put(ATTR_THEME_EXISTS, false);
      checkProjectNow(projectName + "_lib", projectState, activityState);
    }
  }

  private void checkProjectNow(@NotNull String projectName,
                               @NotNull TestNewProjectWizardState projectState,
                               @Nullable TestTemplateWizardState activityState) throws Exception {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    // Do not add non-unicode characters on Windows
    String modifiedProjectName = getModifiedProjectName(projectName, activityState);

    assertNull(myFixture);

    File projectDir = null;
    try {
      moduleState.put(ATTR_MODULE_NAME, modifiedProjectName);
      IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
      TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = factory.createFixtureBuilder(modifiedProjectName);
      myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
      myFixture.setUp();

      Project project = getProject();
      new IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor.class, mock(PostProjectBuildTasksExecutor.class));
      AndroidGradleTests.setUpSdks(myFixture, findSdkPath());
      projectDir = getBaseDirPath(project);
      moduleState.put(ATTR_TOP_OUT, projectDir.getPath());

      System.out.println("Checking project " + projectName + " in " + project.getBaseDir());
      createProject(projectState, CHECK_LINT);

      File projectRoot = virtualToIoFile(project.getBaseDir());
      if (activityState != null && !moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
        activityState.put(ATTR_TOP_OUT, projectDir.getPath());
        ApplicationManager.getApplication().runWriteAction(() -> {
          Template template = activityState.getTemplate();
          assert template != null;
          File moduleRoot = new File(projectRoot, modifiedProjectName);
          activityState.put(ATTR_MODULE_NAME, moduleRoot.getName());
          activityState.put(ATTR_SOURCE_PROVIDER_NAME, "main");
          activityState.populateDirectoryParameters();
          RenderingContext context = createRenderingContext(template, project, moduleRoot, moduleRoot, activityState.getParameters());
          template.render(context, false);
          // Add in icons if necessary
          if (activityState.getTemplateMetadata() != null && activityState.getTemplateMetadata().getIconName() != null) {
            File drawableFolder = new File(FileUtil.join(activityState.getString(ATTR_RES_OUT)),
                                           FileUtil.join("drawable"));
            //noinspection ResultOfMethodCallIgnored
            drawableFolder.mkdirs();
            String fileName = myStringEvaluator.evaluate(activityState.getTemplateMetadata().getIconName(),
                                                         activityState.getParameters());
            File iconFile = new File(drawableFolder, fileName + DOT_XML);
            File sourceFile = new File(getTestDataPath(), FileUtil.join("drawables", "progress_horizontal.xml"));
            try {
              FileUtil.copy(sourceFile, iconFile);
            }
            catch (IOException e) {
              fail(e.getMessage());
            }
          }
        });
      }

      assertNotNull(project);

      // Verify that a newly created kotlin project does not have any java files
      // and has only kotlin files.
      if (getTestName(false).endsWith("WithKotlin")) {
        Path rootPath = projectDir.toPath();
        // Note: Files.walk() stream needs to be closed (or consumed completely), otherwise it will leave locked directories on Windows
        List<Path> allPaths = Files.walk(rootPath).collect(toList());
        assertFalse(allPaths.stream().anyMatch(path -> path.toString().endsWith(".java")));
        assertTrue(allPaths.stream().anyMatch(path -> path.toString().endsWith(".kt")));
      }

      GradleConnector connector = GradleConnector.newConnector();
      connector.forProjectDirectory(projectRoot);
      ((DefaultGradleConnector)connector).daemonMaxIdleTime(10000, TimeUnit.MILLISECONDS);
      ProjectConnection connection = connector.connect();
      BuildLauncher buildLauncher = connection.newBuild().forTasks("assembleDebug");

      List<String> commandLineArguments = new ArrayList<>();
      GradleInitScripts initScripts = GradleInitScripts.getInstance();
      initScripts.addLocalMavenRepoInitScriptCommandLineArg(commandLineArguments);
      buildLauncher.withArguments(ArrayUtil.toStringArray(commandLineArguments));
      @SuppressWarnings("resource")
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
        buildLauncher.setStandardOutput(baos).setStandardError(baos).run();
      }
      //// Use the following commented out code to debug the generated project in case of a failure.
      catch (Exception e) {
        //  File tmpDir = new File("/tmp", "Test-Dir-" + projectName);
        //  FileUtil.copyDir(new File(projectDir, ".."), tmpDir);
        //  System.out.println("Failed project copied to: " + tmpDir.getAbsolutePath());
        throw new Exception(baos.toString("UTF-8"), e);
      }
      finally {
        connection.close();

        // Windows work-around: After closing the gradle connection, it's possible that some files (eg local.properties) are locked
        // for a bit of time. It is also possible that there are Virtual Files that are still synchronizing to the File System, this will
        // break tear-down, when it tries to delete the project.
        if (SystemInfo.isWindows) {
          System.out.println("Windows: Attempting to delete project Root - " + projectRoot);
          new WaitFor(60000) {
            @Override
            protected boolean condition() {
              if (!FileUtil.delete(projectRoot)) {
                System.out.println("Windows: delete project Root failed - time = " + System.currentTimeMillis());
              }
              return projectRoot.mkdir();
            }
          };
        }
      }

      if (CHECK_LINT) {
        assertLintsCleanly(project, Severity.INFORMATIONAL, Collections.singleton(ManifestDetector.TARGET_NEWER));
        // TODO: Check for other warnings / inspections, such as unused imports?
      }
    }
    finally {
      if (myFixture != null) {
        myFixture.tearDown();
        myFixture = null;
      }

      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      assertTrue(openProjects.length <= 1); // 1: the project created by default by the test case

      // Clean up; ensure that we don't bleed contents through to the next iteration
      if (projectDir != null && projectDir.exists()) {
        FileUtil.delete(projectDir);
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
      }
    }
  }

  private static void setAndroidSupport(boolean setAndroidx,
                                        @NotNull TestTemplateWizardState moduleState,
                                        @Nullable TestTemplateWizardState activityState) {
    moduleState.put(ATTR_ANDROIDX_SUPPORT, setAndroidx);
    if (activityState != null) {
      activityState.put(ATTR_ANDROIDX_SUPPORT, setAndroidx);
    }
  }

  private static String getModifiedProjectName(@NotNull String projectName, @Nullable TestTemplateWizardState activityState) {
    if (SystemInfo.isWindows) {
      return "app";
    }
    // Bug 137161906
    if (projectName.startsWith("BasicActivity") &&
        activityState != null &&
        Language.KOTLIN.toString().equals(activityState.getString(ATTR_LANGUAGE))) {
      return projectName;
    }

    String specialChars = "!@#$^&()_+=-.`~";
    String nonAsciiChars = "你所有的基地都属于我们";
    return projectName + specialChars + ',' + nonAsciiChars;
  }

  @SuppressWarnings("SameParameterValue")
  private void createProject(@NotNull TestNewProjectWizardState projectState, boolean syncProject) throws Exception {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    ApplicationManager.getApplication().runWriteAction(() -> {
      int minSdkVersion = Integer.parseInt((String)moduleState.get(ATTR_MIN_API));
      IconGenerator iconGenerator = new LauncherIconGenerator(myFixture.getProject(), minSdkVersion, null);
      try {
        iconGenerator.outputName().set("ic_launcher");
        iconGenerator.sourceAsset().setValue(new ImageAsset());
        createProject(projectState, myFixture.getProject(), iconGenerator);
      } finally {
        Disposer.dispose(iconGenerator);
      }
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    // Update to latest plugin / gradle and sync model
    File projectRoot = new File(moduleState.getString(ATTR_TOP_OUT));
    assertEquals(projectRoot, virtualToIoFile(myFixture.getProject().getBaseDir()));
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION);

    File gradleFile = new File(projectRoot, SdkConstants.FN_BUILD_GRADLE);
    String origContent = com.google.common.io.Files.toString(gradleFile, UTF_8);
    String newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy());
    if (!newContent.equals(origContent)) {
      com.google.common.io.Files.write(newContent, gradleFile, UTF_8);
    }

    refreshProjectFiles();
    if (syncProject) {
      assertEquals(moduleState.getString(ATTR_MODULE_NAME), getProject().getName());
      assertEquals(projectRoot, getBaseDirPath(getProject()));
      importProject();
    }
  }

  private void createProject(@NotNull TestNewProjectWizardState projectState, @NotNull Project project,
                             @Nullable IconGenerator iconGenerator) {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    List<String> errors = new ArrayList<>();
    try {
      moduleState.populateDirectoryParameters();
      String moduleName = moduleState.getString(ATTR_MODULE_NAME);
      String projectPath = moduleState.getString(ATTR_TOP_OUT);
      File projectRoot = new File(projectPath);
      AndroidModuleTemplate paths = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectPath, moduleName).getPaths();
      if (FileUtilRt.createDirectory(projectRoot)) {
        if (iconGenerator != null) {
          // TODO test the icon generator
        }
        projectState.updateParameters();

        File moduleRoot = paths.getModuleRoot();
        assert moduleRoot != null;

        // If this is a new project, instantiate the project-level files
        Template projectTemplate = projectState.getProjectTemplate();
        final RenderingContext projectContext =
            createRenderingContext(projectTemplate, project, projectRoot, moduleRoot, moduleState.getParameters());
        projectTemplate.render(projectContext, false);
        // check usage tracker after project render
        verifyLastLoggedUsage(Template.titleToTemplateRenderer(projectTemplate.getMetadata().getTitle()), projectContext.getParamMap());
        AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot);

        final RenderingContext moduleContext =
          createRenderingContext(moduleState.getTemplate(), project, projectRoot, moduleRoot, moduleState.getParameters());
        Template moduleTemplate = moduleState.getTemplate();
        moduleTemplate.render(moduleContext, false);
        // check usage tracker after module render
        verifyLastLoggedUsage(Template.titleToTemplateRenderer(moduleTemplate.getMetadata().getTitle()), moduleContext.getParamMap());
        if (moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
          TestTemplateWizardState activityTemplateState = projectState.getActivityTemplateState();
          Template activityTemplate = activityTemplateState.getTemplate();
          assert activityTemplate != null;
          final RenderingContext activityContext =
            createRenderingContext(activityTemplate, project, moduleRoot, moduleRoot, activityTemplateState.getParameters());
          activityTemplate.render(activityContext, false);
          // check usage tracker after activity render
          verifyLastLoggedUsage(Template.titleToTemplateRenderer(activityTemplate.getMetadata().getTitle()), activityContext.getParamMap());
          moduleContext.getFilesToOpen().addAll(activityContext.getFilesToOpen());
        }
      }
      else {
        errors.add(String.format("Unable to create directory '%1$s'.", projectRoot.getPath()));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertEmpty(errors);
  }

  @NotNull
  private static RenderingContext createRenderingContext(@NotNull Template projectTemplate,
                                                         @NotNull Project project,
                                                         @NotNull File projectRoot,
                                                         @NotNull File moduleRoot,
                                                         @Nullable Map<String, Object> parameters) {
    RenderingContext.Builder builder = RenderingContext.Builder.newContext(projectTemplate, project)
      .withOutputRoot(projectRoot)
      .withModuleRoot(moduleRoot);

    if (parameters != null) {
      builder.withParams(parameters);
    }

    return builder.build();
  }

  /**
   * Validates this template to make sure it's supported
   *
   * @param currentMinSdk the minimum SDK in the project, or -1 or 0 if unknown (e.g. codename)
   * @param buildApi      the build API, or -1 or 0 if unknown (e.g. codename)
   * @return an error message, or null if there is no problem
   */
  @Nullable
  private static String validateTemplate(TemplateMetadata metadata, int currentMinSdk, int buildApi) {
    if (!metadata.isSupported()) {
      return "This template requires a more recent version of Android Studio. Please update.";
    }
    int templateMinSdk = metadata.getMinSdk();
    if (templateMinSdk > currentMinSdk && currentMinSdk >= 1) {
      return String.format("This template requires a minimum SDK version of at least %1$d, and the current min version is %2$d",
                           templateMinSdk, currentMinSdk);
    }
    int templateMinBuildApi = metadata.getMinBuildApi();
    if (templateMinBuildApi > buildApi && buildApi >= 1) {
      return String.format("This template requires a build target API version of at least %1$d, and the current version is %2$d",
                           templateMinBuildApi, buildApi);
    }

    return null;
  }

  private static void assertLintsCleanly(@NotNull Project project, @NotNull Severity maxSeverity, @NotNull Set<Issue> ignored) {
    BuiltinIssueRegistry registry = new LintIdeIssueRegistry();
    Map<Issue, Map<File, List<ProblemData>>> map = new HashMap<>();
    LintIdeClient client = LintIdeClient.forBatch(project, map, new AnalysisScope(project), new HashSet<>(registry.getIssues()));
    List<Module> modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
    LintRequest request = new LintIdeRequest(client, project, null, modules, false);
    EnumSet<Scope> scope = EnumSet.allOf(Scope.class);
    scope.remove(Scope.CLASS_FILE);
    scope.remove(Scope.ALL_CLASS_FILES);
    scope.remove(Scope.JAVA_LIBRARIES);
    request.setScope(scope);
    LintDriver driver = new LintDriver(registry, client, request);
    driver.analyze();
    if (!map.isEmpty()) {
      for (Map<File, List<ProblemData>> fileListMap : map.values()) {
        for (Map.Entry<File, List<ProblemData>> entry : fileListMap.entrySet()) {
          File file = entry.getKey();
          List<ProblemData> problems = entry.getValue();
          for (ProblemData problem : problems) {
            Issue issue = problem.getIssue();
            if (ignored.contains(issue)) {
              continue;
            }
            if (issue.getDefaultSeverity().compareTo(maxSeverity) < 0) {
              fail("Found lint issue " + issue.getId() + " with severity " + issue.getDefaultSeverity() + " in " + file + " at " +
                   problem.getTextRange() + ": " + problem.getMessage());
            }
          }
        }
      }
    }
  }

  @NotNull
  private static Template getModuleTemplateForFormFactor(@NotNull File templateFile) {
    Template activityTemplate = Template.createFromPath(templateFile);
    Template moduleTemplate = getDefaultModuleTemplate();
    TemplateMetadata activityMetadata = activityTemplate.getMetadata();
    assertNotNull(activityMetadata);
    String activityFormFactorName = activityMetadata.getFormFactor();
    if (activityFormFactorName != null) {
      FormFactor activityFormFactor = FormFactor.get(activityFormFactorName);
      TemplateManager manager = TemplateManager.getInstance();
      List<File> applicationTemplates = manager.getTemplatesInCategory(CATEGORY_APPLICATION);
      for (File formFactorTemplateFile : applicationTemplates) {
        TemplateMetadata metadata = manager.getTemplateMetadata(formFactorTemplateFile);
        if (metadata != null && metadata.getFormFactor() != null && FormFactor.get(metadata.getFormFactor()) == activityFormFactor) {
          moduleTemplate = Template.createFromPath(formFactorTemplateFile);
          break;
        }
      }
    }
    return moduleTemplate;
  }

  @NotNull
  private static Template getDefaultModuleTemplate() {
    return Template.createFromName(CATEGORY_PROJECTS, MODULE_TEMPLATE_NAME);
  }

  /**
   * Checks that the most recent log in myUsageTracker is a AndroidStudioEvent.EventKind.TEMPLATE_RENDER event with expected info.
   *
   * @param templateRenderer the expected value of usage.getStudioEvent().getTemplateRenderer(),
   *                         where usage is the most recent logged usage
   * @param paramMap         the paramMap, containing kotlin support info for template render event
   */
  private void verifyLastLoggedUsage(@NotNull AndroidStudioEvent.TemplateRenderer templateRenderer, @NotNull Map<String, Object> paramMap) {
    List<LoggedUsage> usages = myUsageTracker.getUsages();
    assertFalse(usages.isEmpty());
    // get last logged usage
    LoggedUsage usage = usages.get(usages.size() - 1);
    assertEquals(AndroidStudioEvent.EventKind.TEMPLATE_RENDER, usage.getStudioEvent().getKind());
    assertEquals(templateRenderer, usage.getStudioEvent().getTemplateRenderer());
    assertTrue(paramMap.getOrDefault(ATTR_KOTLIN_VERSION, "unknown") instanceof String);
    assertEquals(
      KotlinSupport.newBuilder()
        .setIncludeKotlinSupport(paramMap.getOrDefault(ATTR_LANGUAGE, "Java").equals(Language.KOTLIN.toString()))
        .setKotlinSupportVersion((String)paramMap.getOrDefault(ATTR_KOTLIN_VERSION, "unknown")).build(),
      usage.getStudioEvent().getKotlinSupport());
  }

  //--- Interfaces ---

  public interface ProjectStateCustomizer {
    void customize(@NotNull Map<String, Object> templateMap, @NotNull Map<String, Object> projectMap);
  }

  //--- Annotations ---

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({METHOD})
  public @interface TemplateCheck {
  }

  //--- Helper classes ---

  // Create a dummy version of this class that just collects all the templates it will test when it is run.
  // It is important that this class is not run by JUnit!
  @SuppressWarnings("JUnitTestClassNamingConvention")
  public static class CoverageChecker extends TemplateTest {
    @Override
    protected boolean shouldRunTest() {
      return false;
    }

    // Set of templates tested with unit test
    private final Set<String> myTemplatesChecked = new HashSet<>();

    private static String getCheckKey(String category, String name, boolean createWithProject) {
      return category + ':' + name + ':' + createWithProject;
    }

    private void gatherMissedTests(File templateFile, boolean createWithProject, ArrayList<String> failures) {
      String category = templateFile.getParentFile().getName();
      String name = templateFile.getName();
      if (!isBroken(name) && !myTemplatesChecked.contains(getCheckKey(category, name, createWithProject))) {
        failures.add("\nCategory: \"" + category + "\" Name: \"" + name + "\" createWithProject: " + createWithProject);
      }
    }

    @Override
    protected void checkCreateTemplate(String category, String name, boolean createWithProject,
                                       @Nullable ProjectStateCustomizer customizer) {
      myTemplatesChecked.add(getCheckKey(category, name, createWithProject));
    }

    // The actual implementation of the test
    @Override
    public void testAllTemplatesCovered() throws Exception {
      for (Method method : getClass().getMethods()) {
        if (method.getAnnotation(TemplateCheck.class) != null && method.getName().startsWith("test")) {
          method.invoke(this);
        }
      }

      ArrayList<String> failureMessages = new ArrayList<>();
      TemplateManager manager = TemplateManager.getInstance();
      for (File templateFile : manager.getTemplates("other")) {
        gatherMissedTests(templateFile, false, failureMessages);
      }

      // Also try creating templates, not as part of creating a project
      for (File templateFile : manager.getTemplates("activities")) {
        gatherMissedTests(templateFile, true, failureMessages);
        gatherMissedTests(templateFile, false, failureMessages);
      }

      String failurePrefix = "\nThe following templates were not covered by TemplateTest. Please ensure that tests are added to cover\n" +
                             "these templates and that they are annotated with @TemplateCheck.\n\n";
      assertWithMessage(failurePrefix).that(failureMessages).isEmpty();
    }
  }
}
