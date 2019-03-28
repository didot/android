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
package com.android.tools.idea.model;

import com.android.SdkConstants;
import com.android.annotations.concurrency.Immutable;
import com.android.annotations.concurrency.Slow;
import com.android.builder.model.*;
import com.android.manifmerger.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.SyncTimestampUtil;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Immutable data object encapsulating the result of merging all of the manifest files related to a particular
 * Android module, including the merged manifest itself, a record of the actions the merger took, and logs related
 * to the merge that the user might find useful.
 *
 * A MergedManifestInfo is also capable of detecting when the merged manifest needs to be updated, as reported by
 * the {@link #isUpToDate} method.
 */
@Immutable
final class MergedManifestInfo {
  private static final Logger LOG = Logger.getInstance(MergedManifestInfo.class);

  @NotNull private final AndroidFacet myFacet;
  /**
   * The Java DOM document corresponding to the merged manifest (not an IntelliJ {@link com.intellij.openapi.editor.Document}).
   * If the merge failed, then this may reference the module's primary manifest instead. If the merge fails and we couldn't
   * find or parse the primary manifest, then myDomDocument will be null.
   */
  @Nullable private final Document myDomDocument;
  @Nullable private final ImmutableList<VirtualFile> myFiles;
  @NotNull private final TObjectLongHashMap<VirtualFile> myLastModifiedMap;
  private final long mySyncTimestamp;
  @Nullable private final ImmutableList<MergingReport.Record> myLoggingRecords;
  @Nullable private final Actions myActions;

  /**
   * Relevant information extracted from the result of running the manifest merger,
   * including a DOM representation of the merged manifest, the actions taken by the
   * merger to produce the manifest, and any logs related to the merge that the user
   * might find useful.
   *
   * A null document indicates that the merge was unsuccessful.
   */
  private static class ParsedMergeResult {
    @Nullable final Document document;
    @NotNull final ImmutableList<MergingReport.Record> loggingRecords;
    @NotNull final Actions actions;

    ParsedMergeResult(@Nullable Document document,
                      @NotNull ImmutableList<MergingReport.Record> loggingRecords,
                      @NotNull Actions actions) {
      this.document = document;
      this.loggingRecords = loggingRecords;
      this.actions = actions;
    }
  }

  private MergedManifestInfo(@NotNull AndroidFacet facet,
                             @Nullable Document domDocument,
                             @NotNull TObjectLongHashMap<VirtualFile> lastModifiedMap,
                             long syncTimestamp,
                             @Nullable ImmutableList<MergingReport.Record> loggingRecords,
                             @Nullable Actions actions) {
    myFacet = facet;
    myDomDocument = domDocument;
    myLastModifiedMap = lastModifiedMap;
    mySyncTimestamp = syncTimestamp;
    myLoggingRecords = loggingRecords;
    myActions = actions;

    ImmutableList.Builder<VirtualFile> files = ImmutableList.builder();
    lastModifiedMap.forEachKey(file -> {
      files.add(file);
      return true;
    });
    myFiles = files.build();
  }

  /**
   * Must be called from within a read action.
   */
  @Slow
  @NotNull
  public static MergedManifestInfo create(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();
    long syncTimestamp = SyncTimestampUtil.getLastSyncTimestamp(project);

    MergedManifestContributors contributors = MergedManifestContributors.determineFor(facet);
    TObjectLongHashMap<VirtualFile> lastModified = getFileModificationStamps(project, contributors.allFiles);

    Document document = null;
    ImmutableList<MergingReport.Record> loggingRecords = null;
    Actions actions = null;

    ParsedMergeResult result = mergeManifests(facet, contributors);
    if (result != null) {
      document = result.document;
      loggingRecords = result.loggingRecords;
      actions = result.actions;
    }

    // If the merge failed, try parsing just the primary manifest. This won't be totally correct, but it's better than nothing.
    // Even if parsing the primary manifest fails, we return a ManifestFile with a null document instead of just returning null
    // to expose the logged errors and so that callers can use isUpToDate() to see if there's been any changes that might make
    // the merge succeed if we try again.
    if (document == null && contributors.primaryManifest != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(contributors.primaryManifest);
      if (psiFile != null) {
        document = XmlUtils.parseDocumentSilently(psiFile.getText(), true);
      }
    }

    return new MergedManifestInfo(facet, document, lastModified, syncTimestamp, loggingRecords, actions);
  }

  @Slow
  @Nullable
  private static ParsedMergeResult mergeManifests(@NotNull AndroidFacet facet, @NotNull MergedManifestContributors manifests) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Project project = facet.getModule().getProject();
    if (project.isDisposed() || manifests.primaryManifest == null) {
      return null;
    }

    try {
      MergingReport mergingReport = getMergedManifest(facet,
                                                      manifests.primaryManifest,
                                                      manifests.flavorAndBuildTypeManifests,
                                                      manifests.libraryManifests,
                                                      manifests.navigationFiles);
      XmlDocument doc = mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);
      if (doc != null) {
        return new ParsedMergeResult(doc.getXml(), mergingReport.getLoggingRecords(), mergingReport.getActions());
      }
      else {
        LOG.warn("getMergedManifest failed " + mergingReport.getReportString());
        return new ParsedMergeResult(null, mergingReport.getLoggingRecords(), mergingReport.getActions());
      }
    }
    catch (ManifestMerger2.MergeFailureException ex) {
      // action cancelled
      if (ex.getCause() instanceof ProcessCanceledException) {
        return null;
      }
      // user is in the middle of editing the file
      if (ex.getCause() instanceof SAXParseException) {
        return null;
      }
      LOG.warn("getMergedManifest exception", ex);
    }
    return null;
  }

  /**
   * Must be called from within a read action.
   *
   * @return false if the merged manifest needs to be re-computed due to changes to the set of relevant manifests
   */
  public boolean isUpToDate() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (Disposer.isDisposed(myFacet)) {
      return true;
    }
    MergedManifestContributors manifests = MergedManifestContributors.determineFor(myFacet);
    if (manifests.primaryManifest == null) {
      return true;
    }
    long lastSyncTimestamp = SyncTimestampUtil.getLastSyncTimestamp(myFacet.getModule().getProject());
    if (myDomDocument == null || mySyncTimestamp != lastSyncTimestamp) {
      return false;
    }
    // TODO(b/128854237): We should use something backed with an iterator here so that we can early
    //  return without computing all the files we might care about first.
    return myLastModifiedMap.equals(getFileModificationStamps(myFacet.getModule().getProject(), manifests.allFiles));
  }

  @NotNull
  private static TObjectLongHashMap<VirtualFile> getFileModificationStamps(@NotNull Project project, @NotNull Iterable<VirtualFile> files) {
    TObjectLongHashMap<VirtualFile> modificationStamps = new TObjectLongHashMap<>();
    for (VirtualFile file : files) {
      modificationStamps.put(file, getFileModificationStamp(project, file));
    }
    return modificationStamps;
  }

  private static long getFileModificationStamp(@NotNull Project project, @NotNull VirtualFile file) {
    try {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      return psiFile == null ? file.getModificationStamp() : psiFile.getModificationStamp();
    }
    catch (ProcessCanceledException ignore) {
      return 0L;
    }
  }

  /**
   * Returns the merged manifest as a Java DOM document if available, the primary manifest if the merge was unsuccessful,
   * or null if the merge failed and we were also unable to parse the primary manifest.
   */
  @Nullable
  public Document getXmlDocument() {
    return myDomDocument;
  }

  @Nullable
  public ImmutableList<VirtualFile> getFiles() {
    return myFiles;
  }

  @NotNull
  public ImmutableList<MergingReport.Record> getLoggingRecords() {
    return myLoggingRecords == null ? ImmutableList.of() : myLoggingRecords;
  }

  @Nullable
  public Actions getActions() {
    return myActions;
  }

  @Slow
  @NotNull
  static MergingReport getMergedManifest(@NotNull AndroidFacet facet,
                                         @NotNull VirtualFile primaryManifestFile,
                                         @NotNull List<VirtualFile> flavorAndBuildTypeManifests,
                                         @NotNull List<VirtualFile> libManifests,
                                         @NotNull List<VirtualFile> navigationFiles) throws ManifestMerger2.MergeFailureException {
    ApplicationManager.getApplication().assertReadAccessAllowed();


    final File mainManifestFile = VfsUtilCore.virtualToIoFile(primaryManifestFile);

    ILogger logger = NullLogger.getLogger();
    ManifestMerger2.MergeType mergeType =
      facet.getConfiguration().isAppOrFeature() ? ManifestMerger2.MergeType.APPLICATION : ManifestMerger2.MergeType.LIBRARY;

    AndroidModel androidModel = facet.getConfiguration().getModel();
    AndroidModuleModel gradleModel = AndroidModuleModel.get(facet);

    ManifestMerger2.Invoker manifestMergerInvoker = ManifestMerger2.newMerger(mainManifestFile, logger, mergeType);
    manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.SKIP_BLAME, ManifestMerger2.Invoker.Feature.SKIP_XML_STRING);
    manifestMergerInvoker.addFlavorAndBuildTypeManifests(VfsUtilCore.virtualToIoFiles(flavorAndBuildTypeManifests).toArray(new File[0]));
    manifestMergerInvoker.addNavigationFiles(VfsUtilCore.virtualToIoFiles(navigationFiles));

    List<Pair<String, File>> libraryManifests = new ArrayList<>();
    for (VirtualFile file : libManifests) {
      libraryManifests.add(Pair.of(file.getName(), VfsUtilCore.virtualToIoFile(file)));
    }
    manifestMergerInvoker.addBundleManifests(libraryManifests);

    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      if (minSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion.getApiString());
      }
      AndroidVersion targetSdkVersion = androidModel.getTargetSdkVersion();
      if (targetSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion.getApiString());
      }
      Integer versionCode = androidModel.getVersionCode();
      if (versionCode != null && versionCode > 0) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.VERSION_CODE, String.valueOf(versionCode));
      }
      String packageOverride = androidModel.getApplicationId();
      if (!Strings.isNullOrEmpty(packageOverride)) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.PACKAGE, packageOverride);
      }
    }

    if (gradleModel != null) {
      BuildTypeContainer buildTypeContainer = gradleModel.findBuildType(gradleModel.getSelectedVariant().getBuildType());
      assert buildTypeContainer != null;
      BuildType buildType = buildTypeContainer.getBuildType();

      ProductFlavor mergedProductFlavor = gradleModel.getSelectedVariant().getMergedFlavor();
      // copy-paste from {@link VariantConfiguration#getManifestPlaceholders()}
      Map<String, Object> placeHolders = new HashMap<>(mergedProductFlavor.getManifestPlaceholders());
      placeHolders.putAll(buildType.getManifestPlaceholders());
      manifestMergerInvoker.setPlaceHolderValues(placeHolders);


      // @deprecated maxSdkVersion has been ignored since Android 2.1 (API level 7)
      Integer maxSdkVersion = mergedProductFlavor.getMaxSdkVersion();
      if (maxSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
      }

      // TODO we should have version Name for non-gradle projects
      // copy-paste from {@link VariantConfiguration#getVersionName()}
      String versionName = mergedProductFlavor.getVersionName();
      String flavorVersionNameSuffix = null;
      if (gradleModel.getFeatures().isProductFlavorVersionSuffixSupported()) {
        flavorVersionNameSuffix = getVersionNameSuffix(mergedProductFlavor);
      }
      String versionNameSuffix = Joiner.on("").skipNulls().join(flavorVersionNameSuffix, getVersionNameSuffix(buildType));
      if (!Strings.isNullOrEmpty(versionName) || !Strings.isNullOrEmpty(versionNameSuffix)) {
        if (Strings.isNullOrEmpty(versionName)) {
          Manifest manifest = facet.getManifest();
          if (manifest != null) {
            versionName = manifest.getXmlTag().getAttributeValue(SdkConstants.ATTR_VERSION_NAME, ANDROID_URI);
          }
        }
        if (!Strings.isNullOrEmpty(versionNameSuffix)) {
          versionName = Strings.nullToEmpty(versionName) + versionNameSuffix;
        }
        manifestMergerInvoker.setOverride(ManifestSystemProperty.VERSION_NAME, versionName);
      }

    }

    if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
      manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
    }

    final Module module = facet.getModule();
    final Project project = module.getProject();

    manifestMergerInvoker.withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
      @Override
      protected InputStream getInputStream(@NotNull File file) throws FileNotFoundException {
        VirtualFile vFile;
        if (file == mainManifestFile) {
          // Some tests use VirtualFile files (e.g. temp:///src/AndroidManifest.xml) for the main manifest
          vFile = primaryManifestFile;
        }
        else {
          vFile = VfsUtil.findFileByIoFile(file, false);
        }
        if (vFile == null) {
          // Gracefully handle case where file doesn't exist; this can happen for example
          // when a Gradle sync is needed after version control etc (see issue 65541477)
          //noinspection ZeroLengthArrayAllocation
          return new ByteArrayInputStream("<manifest/>".getBytes(StandardCharsets.UTF_8));
        }

        // We do not want to do this check if we have no library manifests.
        // findModuleForFile does not work for other build systems (e.g. bazel)
        if (!libManifests.isEmpty()) {
          Module moduleContainingManifest = getAndroidModuleForManifest(vFile);
          if (moduleContainingManifest != null && !module.equals(moduleContainingManifest)) {
            MergedManifestSnapshot manifest = MergedManifestManager.getSnapshot(moduleContainingManifest);

            Document document = manifest.getDocument();
            if (document != null) { // normally the case, but can fail on merge fail
              // This is not very efficient. Consider enhancing the manifest merger API
              // such that I can pass back a fully merged DOM document instead of
              // an XML string since it will need to turn around and parse it anyway.
              String text = XmlUtils.toXml(document);
              return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
            }
          }
        }

        try {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
          if (psiFile != null) {
            String text = psiFile.getText();
            return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
          }
        }
        catch (ProcessCanceledException ignore) {
          // During startup we may receive a progress canceled exception here,
          // but we don't *need* to read from PSI; we can read directly from
          // disk. PSI is useful when the file has been modified, but that's not
          // the case in the typical scenario where we hit process canceled.
        }
        return super.getInputStream(file);
      }

      @Nullable
      private Module getAndroidModuleForManifest(@NotNull VirtualFile vFile) {
        // See https://code.google.com/p/android/issues/detail?id=219141
        // Earlier, we used to get the module containing a manifest by doing: ModuleUtilCore.findModuleForFile(vFile, project)
        // This method of getting the module simply returns the module that contains this file. However, if the manifest sources are
        // remapped, this could be incorrect. i.e. for a project with the following structure:
        //     root
        //       |--- modules/a
        //       |       |------- build.gradle
        //       |--- external/a
        //               |------- AndroidManifest.xml
        // where the build.gradle remaps the sources to point to $root/external/a/AndroidManifest.xml, obtaining the module containing the
        // file will return root where it should have been "a". So the correct scheme is to actually iterate through all the modules in the
        // project and look at their source providers
        for (Module m : ModuleManager.getInstance(project).getModules()) {
          AndroidFacet androidFacet = AndroidFacet.getInstance(m);
          if (androidFacet == null) {
            continue;
          }

          List<VirtualFile> manifestFiles = IdeaSourceProvider.getManifestFiles(androidFacet);
          for (VirtualFile manifestFile : manifestFiles) {
            if (vFile.equals(manifestFile)) {
              return m;
            }
          }
        }

        return null;
      }
    });


    return manifestMergerInvoker.merge();
  }

  // TODO: Remove once Android plugin v. 2.3 is the "recommended" version.
  @Nullable
  @Deprecated
  // TODO replace with IdeBaseConfig#getVersionNameSuffix
  private static String getVersionNameSuffix(@NotNull BaseConfig config) {
    try {
      return config.getVersionNameSuffix();
    }
    catch (UnsupportedOperationException e) {
      LOG.warn("Method 'getVersionNameSuffix' not found", e);
      return null;
    }
  }
}