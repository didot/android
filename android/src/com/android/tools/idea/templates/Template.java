/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.templates.FreemarkerUtils.processFreemarkerTemplate;
import static com.android.tools.idea.templates.TemplateManager.getTemplateRootFolder;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_IS_INSTANT_MODULE;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_KOTLIN_VERSION;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_LANGUAGE;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API_LEVEL;
import static com.android.tools.idea.templates.TemplateAttributes.ATTR_TARGET_API;
import static com.android.tools.idea.templates.TemplateMetadata.TAG_FORM_FACTOR;
import static com.android.tools.idea.templates.TemplateUtils.hasExtension;
import static com.android.tools.idea.templates.parse.SaxUtils.getPath;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.Language;
import com.google.common.annotations.VisibleForTesting;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateUserVisibleException;
import com.android.tools.idea.templates.recipe.Recipe;
import com.android.tools.idea.templates.recipe.RecipeExecutor;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer;
import com.google.wireless.android.sdk.stats.KotlinSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Handler which manages instantiating FreeMarker templates, copying resources
 * and merging into existing files
 */
public class Template {
  /**
   * Reserved filename which describes each template
   */
  public static final String TEMPLATE_XML_NAME = "template.xml";
  // Various tags and attributes used in template.xml
  public static final String TAG_EXECUTE = "execute";
  public static final String TAG_GLOBALS = "globals";
  public static final String TAG_GLOBAL = "global";
  public static final String TAG_PARAMETER = "parameter";
  public static final String TAG_THUMB = "thumb";
  public static final String TAG_THUMBS = "thumbs";
  public static final String ATTR_FORMAT = "format";
  public static final String ATTR_VALUE = "value";
  public static final String ATTR_DEFAULT = "default";
  public static final String ATTR_SUGGEST = "suggest";
  public static final String ATTR_ID = "id";
  public static final String ATTR_NAME = "name";
  public static final String ATTR_DESCRIPTION = "description";
  public static final String ATTR_TYPE = "type";
  public static final String ATTR_HELP = "help";
  public static final String ATTR_FILE = "file";
  public static final String ATTR_CONSTRAINTS = "constraints";
  public static final String ATTR_VISIBILITY = "visibility";
  public static final String ATTR_ENABLED = "enabled";
  public static final String CATEGORY_ACTIVITIES = "activities";
  public static final String CATEGORY_PROJECTS = "gradle-projects";
  public static final String CATEGORY_OTHER = "other";
  public static final String CATEGORY_APPLICATION = "Application";
  public static final String ANDROID_MODULE_TEMPLATE = "Android Module";
  public static final String ANDROID_PROJECT_TEMPLATE = "Android Project";
  public static final String IMPORT_ECLIPSE_PROJECT_TEMPLATE = "Import Eclipse ADT Project";
  public static final String IMPORT_GRADLE_PROJECT_TEMPLATE = "Import Gradle Project";

  /**
   * Highest supported format; templates with a higher number will be skipped
   * <p/>
   * <ul>
   * <li> 1: Initial format, supported by ADT 20 and up.
   * <li> 2: ADT 21 and up. Boolean variables that have a default value and are not
   * edited by the user would end up as strings in ADT 20; now they are always
   * proper Booleans. Templates which rely on this should specify format >= 2.
   * <li> 3: The wizard infrastructure passes the {@code isNewProject} boolean variable
   * to indicate whether a wizard is created as part of a new blank project
   * <li> 4: Constraint type app_package ({@link Constraint#APP_PACKAGE}), provides
   * srcDir, resDir and manifestDir variables for locations of files
   * <li> 5: All files are relative to the template instead of using an implicit "root" folder.
   * </ul>
   */
  static final int CURRENT_FORMAT = 5;

  /**
   * Templates from this version and up use relative (from this template) path names.
   * Recipe files from older versions uses an implicit "root" folder.
   */
  static final int RELATIVE_FILES_FORMAT = 5;

  private static final int MAX_WARNINGS = 10;

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.Template");

  /**
   * Path to the directory containing the templates
   */
  private final File myTemplateRoot;

  private TemplateMetadata myMetadata;

  private Template(@NotNull File templateRoot) {
    myTemplateRoot = templateRoot;
  }

  /**
   * Creates a new {@link Template} for the given root path
   */
  @NotNull
  public static Template createFromPath(@NotNull File rootPath) {
    return new Template(rootPath);
  }

  /**
   * Creates a new {@link Template} for the template name, which should
   * be relative to the templates directory
   */
  @NotNull
  public static Template createFromName(@NotNull String category, @NotNull String name) {
    TemplateManager manager = TemplateManager.getInstance();

    // Use the TemplateManager iteration which should merge contents between the
    // extras/templates/ and tools/templates folders and pick the most recent version
    List<File> templates = manager.getTemplates(category);
    for (File file : templates) {
      if (file.getName().equals(name) && category.equals(file.getParentFile().getName())) {
        return new Template(file);
      }
    }

    return new Template(new File(getTemplateRootFolder(), category + File.separator + name));
  }

  /**
   * Iterate through parameters and ensure the given map has the correct for each
   * parameter.
   */
  private static void enforceParameterTypes(@NotNull TemplateMetadata metadata, @NotNull Map<String, Object> args) {
    for (Parameter p : metadata.getParameters()) {
      Object o = args.get(p.id);
      if (o == null) {
        continue;
      }
      switch (p.type) {
        case STRING:
          if (!(o instanceof String)) {
            args.put(p.id, o.toString());
          }
          break;
        case BOOLEAN:
          if (!(o instanceof Boolean)) {
            args.put(p.id, Boolean.parseBoolean(o.toString()));
          }
          break;
        case ENUM:
          break;
        case SEPARATOR:
          break;
      }
    }
    convertApisToInt(args);
  }

  public static void convertApisToInt(@NotNull Map<String, Object> args) {
    convertToInt(ATTR_BUILD_API, args);
    convertToInt(ATTR_MIN_API_LEVEL, args);
    convertToInt(ATTR_TARGET_API, args);
  }

  private static void convertToInt(@NotNull String key, @NotNull Map<String, Object> args) {
    Object value = args.get(key);
    if (value instanceof String) {
      int result;
      try {
        result = Integer.parseInt((String)value);
      }
      catch (NumberFormatException e) {
        result = SdkVersionInfo.getApiByPreviewName((String)value, true /* Recognize Unknowns */);
      }
      args.put(key, result);
    }
  }

  /**
   * Executes the template, rendering it to output files under the directory context.getModuleRoot()
   *
   * @return true if the template was rendered without finding any errors and there are no warnings
   * or the user selected to proceed with warnings.
   */
  public boolean render(@NotNull final RenderingContext context, final boolean dryRun) {
    final Project project = context.getProject();

    boolean success = project.isInitialized() ?
      doRender(context) : PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> doRender(context));

    String title = myMetadata.getTitle();
    if (!dryRun && title != null) {
      Map<String, Object> paramMap = context.getParamMap();
      boolean kotlinSupport = Language.KOTLIN.toString().equals(paramMap.get(ATTR_LANGUAGE));
      Object kotlinVersion = paramMap.get(ATTR_KOTLIN_VERSION);
      AndroidStudioEvent.Builder aseBuilder =
        AndroidStudioEvent.newBuilder()
                          .setCategory(EventCategory.TEMPLATE)
                          .setKind(AndroidStudioEvent.EventKind.TEMPLATE_RENDER)
                          .setTemplateRenderer(titleToTemplateRenderer(title, myMetadata.getFormFactor()))
                          .setKotlinSupport(
                            KotlinSupport.newBuilder()
                                         .setIncludeKotlinSupport(kotlinSupport)
                                         .setKotlinSupportVersion(kotlinVersion instanceof String ? (String)kotlinVersion : "unknown"));
      UsageTracker.log(UsageTrackerUtils.withProjectId(aseBuilder, project));

      if (Boolean.TRUE.equals(paramMap.get(ATTR_DYNAMIC_IS_INSTANT_MODULE))) {
        aseBuilder.setTemplateRenderer(TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE);
        UsageTracker.log(UsageTrackerUtils.withProjectId(aseBuilder, project));
      }
    }

    if (context.shouldReformat()) {
      StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(() -> TemplateUtils.reformatAndRearrange(project, context.getTargetFiles()));
    }

    ApplicationManager.getApplication().invokeAndWait(PsiDocumentManager.getInstance(project)::commitAllDocuments);

    return success;
  }

  @VisibleForTesting
  static TemplateRenderer titleToTemplateRenderer(String title, @Nullable String formFactor) {
    switch (title) {
      case "":
        return TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER;
      case ANDROID_MODULE_TEMPLATE:
        return TemplateRenderer.ANDROID_MODULE;
      case ANDROID_PROJECT_TEMPLATE:
        return TemplateRenderer.ANDROID_PROJECT;
      case "Empty Activity":
        return TemplateRenderer.EMPTY_ACTIVITY;
      case "Blank Activity":
        return FormFactor.WEAR.id.equals(formFactor) ? TemplateRenderer.BLANK_WEAR_ACTIVITY : TemplateRenderer.BLANK_ACTIVITY;
      case "Layout XML File":
        return TemplateRenderer.LAYOUT_XML_FILE;
      case "Fragment (Blank)":
        return TemplateRenderer.FRAGMENT_BLANK;
      case "Navigation Drawer Activity":
        return TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY;
      case "Values XML File":
        return TemplateRenderer.VALUES_XML_FILE;
      case "Google Maps Activity":
        return TemplateRenderer.GOOGLE_MAPS_ACTIVITY;
      case "Login Activity":
        return TemplateRenderer.LOGIN_ACTIVITY;
      case "Assets Folder":
        return TemplateRenderer.ASSETS_FOLDER;
      case "Tabbed Activity":
        return TemplateRenderer.TABBED_ACTIVITY;
      case "Scrolling Activity":
        return TemplateRenderer.SCROLLING_ACTIVITY;
      case "Fullscreen Activity":
        return TemplateRenderer.FULLSCREEN_ACTIVITY;
      case "Service":
        return TemplateRenderer.SERVICE;
      case "Java Library":
        return TemplateRenderer.JAVA_LIBRARY;
      case "Settings Activity":
        return TemplateRenderer.SETTINGS_ACTIVITY;
      case "Fragment (List)":
        return TemplateRenderer.FRAGMENT_LIST;
      case "Master/Detail Flow":
        return TemplateRenderer.MASTER_DETAIL_FLOW;
      case "Wear OS Module":
        return TemplateRenderer.ANDROID_WEAR_MODULE;
      case "Broadcast Receiver":
        return TemplateRenderer.BROADCAST_RECEIVER;
      case "AIDL File":
        return TemplateRenderer.AIDL_FILE;
      case "Service (IntentService)":
        return TemplateRenderer.INTENT_SERVICE;
      case "JNI Folder":
        return TemplateRenderer.JNI_FOLDER;
      case "Java Folder":
        return TemplateRenderer.JAVA_FOLDER;
      case "Custom View":
        return TemplateRenderer.CUSTOM_VIEW;
      case "Android TV Module":
        return TemplateRenderer.ANDROID_TV_MODULE;
      case "Google AdMob Ads Activity":
        return TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY;
      case "Always On Wear Activity":
        return TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY;
      case "Res Folder":
        return TemplateRenderer.RES_FOLDER;
      case "Android TV Activity":
        return TemplateRenderer.ANDROID_TV_ACTIVITY;
      case "Basic Activity":
        return TemplateRenderer.BASIC_ACTIVITIY;
      case "App Widget":
        return TemplateRenderer.APP_WIDGET;
      case "Instant App Project":
        return TemplateRenderer.ANDROID_INSTANT_APP_PROJECT;
      case "Instant App":
        return TemplateRenderer.ANDROID_INSTANT_APP_MODULE;
      case "Dynamic Feature (Instant App)":
        return TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE;
      case "Benchmark Module":
        return TemplateRenderer.BENCHMARK_LIBRARY_MODULE;
      case "Fullscreen Fragment":
        return TemplateRenderer.FRAGMENT_FULLSCREEN;
      case "Google AdMob Ads Fragment":
        return TemplateRenderer.FRAGMENT_GOOGLE_ADMOB_ADS;
      case "Google Maps Fragment":
        return TemplateRenderer.FRAGMENT_GOOGLE_MAPS;
      case "Login Fragment":
        return TemplateRenderer.FRAGMENT_LOGIN;
      case "Modal Bottom Sheet":
        return TemplateRenderer.FRAGMENT_MODAL_BOTTOM_SHEET;
      case "Scrolling Fragment":
        return TemplateRenderer.FRAGMENT_SCROLL;
      case "Settings Fragment":
        return TemplateRenderer.FRAGMENT_SETTINGS;
      case "Fragment (with ViewModel)":
        return TemplateRenderer.FRAGMENT_VIEWMODEL;
      case "Empty Compose Activity":
        return TemplateRenderer.COMPOSE_EMPTY_ACTIVITY;
      default:
        return TemplateRenderer.CUSTOM_TEMPLATE_RENDERER;
    }
  }

  /**
   * Version of runWriteCommandAction missing in {@link WriteCommandAction}.
   */
  private static <T, E extends Throwable> T runWriteCommandAction(@NotNull Project project,
                                                                  @NotNull String commandName,
                                                                  @NotNull final ThrowableComputable<T, E> computable) throws E {
    RunResult<T> result = new WriteCommandAction<T>(project, commandName) {
      @Override
      protected void run(@NotNull Result<T> result) throws Throwable {
        result.setResult(computable.compute());
      }
    }.execute();
    return result.throwException().getResultObject();
  }

  /**
   * Render the template.
   * Warnings are only generated during a dry run i.e. no files are changed yet.
   * The user may select to proceed anyway in which case we expect another call
   * to render with dry run set to false.
   * Errors may be shown regardless of the dry run flag.
   */
  private boolean doRender(@NotNull RenderingContext context) {
    TemplateMetadata metadata = getMetadata();
    assert metadata != null;

    enforceParameterTypes(metadata, context.getParamMap());

    try {
      runWriteCommandAction(context.getProject(), context.getCommandName(), () -> {
        processFile(context, new File(TEMPLATE_XML_NAME));
        return null;
      });

      if (context.getWarnings().isEmpty()) {
        return true;
      }
      if (!context.showWarnings()) {
        LOG.warn("WARNING: " + context.getWarnings());
        return true;
      }
      AtomicBoolean result = new AtomicBoolean();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        int userReply = Messages.showOkCancelDialog(
          context.getProject(),
          formatWarningMessage(context),
          String.format("%1$s %2$s", context.getCommandName(), StringUtil.pluralize("Warning")),
          "Proceed Anyway", "Cancel", Messages.getWarningIcon());
        result.set(userReply == Messages.OK);
      });
      return result.get();
    }
    catch (TemplateUserVisibleException e) {
      if (context.showErrors()) {
        ApplicationManager.getApplication().invokeAndWait(() -> Messages.showErrorDialog(
          context.getProject(),
          formatErrorMessage(context, e),
          String.format("%1$s Failed", context.getCommandName())));
      }
      else {
        throw new RuntimeException(e);
      }
      return false;
    }
    catch (TemplateProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String formatWarningMessage(@NotNull RenderingContext context) {
    int warningCount = context.getWarnings().size();
    List<String> messages = Lists.newArrayList(context.getWarnings());
    if (warningCount > MAX_WARNINGS + 1) {  // +1 such that the message can say "warnings" in plural...
      // Guard against too many warnings (the dialog may become larger than the screen size)
      messages = messages.subList(0, MAX_WARNINGS);
      messages.add(String.format(Locale.US, "And %1$d more warnings...", warningCount - MAX_WARNINGS));
    }
    messages.add("\nIf you proceed the resulting project may not compile or not work as intended.");
    return Joiner.on("\n\n").join(messages);
  }

  /**
   * If this is not a dry run, we may have created/changed some files and the project
   * may no longer compile. Let the user know about undo.
   */
  private static String formatErrorMessage(@NotNull RenderingContext context, @NotNull TemplateUserVisibleException ex) {
    if (!context.canCausePartialRendering()) {
      return ex.getMessage();
    }
    //noinspection StringBufferReplaceableByString
    return new StringBuilder()
      .append(ex.getMessage())
      .append(String.format("\n\n%1$s was only partially completed.", context.getCommandName()))
      .append("\nYour project may not compile.")
      .append("\nYou may want to Undo to get back to the original state.")
      .toString();
  }

  @NotNull
  public File getRootPath() {
    return myTemplateRoot;
  }

  @Nullable
  public TemplateMetadata getMetadata() {
    if (myMetadata == null) {
      myMetadata = TemplateManager.getInstance().getTemplateMetadata(myTemplateRoot);
    }

    return myMetadata;
  }

  /**
   * Read the given xml file and, if it uses freemarker syntax (indicated by its file extension),
   * process the variable definitions
   */
  private void processFile(@NotNull final RenderingContext context, @NotNull File file) throws TemplateProcessingException {
    String xml;
    if (hasExtension(file, DOT_XML)) {
      // Just read the file
      xml = TemplateUtils.readTextFromDisk(getTemplateFile(file));
      if (xml == null) {
        return;
      }
      processXml(context, xml);
    }
    else {
      processFreemarkerTemplate(context, file, new FreemarkerUtils.TemplatePostProcessor() {
        @Override
        public void process(@NotNull String xml) throws TemplateProcessingException {
          processXml(context, xml);
        }
      });
    }
  }

  private void processXml(@NotNull final RenderingContext context, @NotNull String xml) throws TemplateProcessingException {
    try {
      xml = XmlUtils.stripBom(xml);
      InputSource inputSource = new InputSource(new StringReader(xml));
      SAXParserFactory.newInstance().newSAXParser().parse(inputSource, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
          try {
            Map<String, Object> paramMap = context.getParamMap();
            if (TAG_PARAMETER.equals(name)) {
              String id = attributes.getValue(ATTR_ID);
              if (!paramMap.containsKey(id)) {
                String value = attributes.getValue(ATTR_DEFAULT);
                Object mapValue = value;
                if (value != null && !value.isEmpty()) {
                  String type = attributes.getValue(ATTR_TYPE);
                  if ("boolean".equals(type)) {
                    mapValue = Boolean.valueOf(value);
                  }
                }
                paramMap.put(id, mapValue);
              }
            }
            else if (TAG_GLOBAL.equals(name)) {
              String id = attributes.getValue(ATTR_ID);
              if (!paramMap.containsKey(id)) {
                paramMap.put(id, TypedVariable.parseGlobal(attributes));
              }
            }
            else if (TAG_GLOBALS.equals(name)) {
              // Handle evaluation of variables
              File globalsFile = getPath(attributes, ATTR_FILE);
              if (globalsFile != null) {
                processFile(context, globalsFile);
              } // else: <globals> root element
            }
            else if (TAG_EXECUTE.equals(name)) {
              File recipeFile = getPath(attributes, ATTR_FILE);
              if (recipeFile != null) {
                executeRecipeFile(context, recipeFile);
              }
            }
            else if (!name.equals("template") &&
                     !name.equals("category") &&
                     !name.equals("option") &&
                     !name.equals(TAG_THUMBS) &&
                     !name.equals(TAG_THUMB) &&
                     !name.equals(TAG_FORM_FACTOR)) {
              LOG.error("WARNING: Unknown template directive " + name);
            }
          }
          catch (TemplateProcessingException e) {
            throw new SAXException(e);
          }
        }
      });
    }
    catch (SAXException ex) {
      if (ex.getCause() instanceof TemplateProcessingException) {
        throw (TemplateProcessingException)ex.getCause();
      }
      throw new TemplateProcessingException(ex);
    }
    catch (ParserConfigurationException ex) {
      throw new TemplateProcessingException(ex);
    }
    catch (IOException ex) {
      throw new TemplateProcessingException(ex);
    }
  }

  /**
   * Executes the given recipe file: copying, merging, instantiating, opening files etc
   */
  private void executeRecipeFile(@NotNull final RenderingContext context, @NotNull File fileRecipe) throws TemplateProcessingException {
    processFreemarkerTemplate(context, fileRecipe, new FreemarkerUtils.TemplatePostProcessor() {
      @Override
      public void process(@NotNull String xml) throws TemplateProcessingException {
        try {
          xml = XmlUtils.stripBom(xml);

          Recipe recipe = Recipe.parse(new StringReader(xml));
          RecipeExecutor recipeExecutor = context.getRecipeExecutor();
          TemplateMetadata metadata = getMetadata();
          assert metadata != null;
          if (!metadata.useImplicitRootFolder()) {
            recipe.execute(recipeExecutor);
          }
          else {
            StudioTemplateLoader loader = context.getLoader();
            try {
              loader.pushTemplateFolder(new File(getRootPath(), "root"));
              recipe.execute(recipeExecutor);
            }
            finally {
              loader.popTemplateFolder();
            }
          }
        }
        catch (JAXBException ex) {
          throw new TemplateProcessingException(ex);
        }
      }
    });
  }

  @NotNull
  private File getTemplateFile(@NotNull File relativeFile) {
    return new File(myTemplateRoot, relativeFile.getPath());
  }
}
