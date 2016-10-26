/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.crash;

import com.android.tools.analytics.Anonymizer;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * {@link GoogleCrash} provides APIs to upload crash reports to Google crash reporting service.
 * @see <a href="http://go/studio-g3doc/implementation/crash">Crash Backend</a> for more information.
 */
public class GoogleCrash implements CrashReporter {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;
  private static final boolean DEBUG_BUILD = !UNIT_TEST_MODE && ApplicationManager.getApplication().isInternal();

  // Send crashes during development to the staging backend
  private static final String CRASH_URL =
    (UNIT_TEST_MODE || DEBUG_BUILD) ? "https://clients2.google.com/cr/staging_report" : "https://clients2.google.com/cr/report";

  @Nullable
  private static final String ANONYMIZED_UID = getAnonymizedUid();
  private static final String LOCALE = Locale.getDefault() == null ? "unknown" : Locale.getDefault().toString();

  // The standard keys expected by crash backend. The product id and version are required, others are optional.
  static final String KEY_PRODUCT_ID = "productId";
  static final String KEY_VERSION = "version";
  static final String KEY_EXCEPTION_INFO = "exception_info";

  private final String myCrashUrl;

  @Nullable
  private static String getAnonymizedUid() {
    if (UNIT_TEST_MODE) {
      return "UnitTest";
    }

    try {
      return Anonymizer.anonymizeUtf8(new NullLogger(), UpdateChecker.getInstallationUID(PropertiesComponent.getInstance()));
    }
    catch (IOException e) {
      return null;
    }
  }

  GoogleCrash() {
    this(CRASH_URL);
  }

  @VisibleForTesting
  GoogleCrash(@NotNull String crashUrl) {
    myCrashUrl = crashUrl;
  }

  @Override
  @NotNull
  public CompletableFuture<String> submit(@NotNull CrashReport report) {
    Map<String, String> parameters = getDefaultParameters();
    if (report.version != null) {
      parameters.put(KEY_VERSION, report.version);
    }
    parameters.put(KEY_PRODUCT_ID, report.productId);

    MultipartEntityBuilder builder = newMultipartEntityBuilderWithKv(parameters);
    report.serialize(builder);
    return submit(builder.build());
  }

  @NotNull
  @Override
  public CompletableFuture<String> submit(@NotNull Map<String, String> kv) {
    Map<String, String> parameters = getDefaultParameters();
    kv.forEach(parameters::put);
    return submit(newMultipartEntityBuilderWithKv(parameters).build());
  }

  @NotNull
  @Override
  public CompletableFuture<String> submit(@NotNull final HttpEntity requestEntity) {
    CompletableFuture<String> future = new CompletableFuture<>();
    ForkJoinPool.commonPool().submit(() -> {
      try {
        HttpClient client = HttpClients.createDefault();

        HttpEntity entity = requestEntity;
        if (!UNIT_TEST_MODE) {
          // The test server used in testing doesn't handle gzip compression (netty requires jcraft jzlib for gzip decompression)
          entity = new GzipCompressingEntity(requestEntity);
        }

        HttpPost post = new HttpPost(myCrashUrl);
        post.setEntity(entity);

        HttpResponse response = client.execute(post);
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() >= 300) {
          future.completeExceptionally(new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase()));
          return;
        }

        entity = response.getEntity();
        if (entity == null) {
          future.completeExceptionally(new NullPointerException("Empty response entity"));
          return;
        }

        String reportId = EntityUtils.toString(entity);
        if (DEBUG_BUILD) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Report submitted: http://go/crash-staging/" + reportId);
        }
        future.complete(reportId);
      }
      catch (IOException e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  @NotNull
  private static MultipartEntityBuilder newMultipartEntityBuilderWithKv(@NotNull Map<String, String> kv) {
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    kv.forEach(builder::addTextBody);
    return builder;
  }

  @NotNull
  private static Map<String, String> getDefaultParameters() {
    Map<String, String> map = new HashMap<>();
    ApplicationInfo applicationInfo = getApplicationInfo();

    if (ANONYMIZED_UID != null) {
      map.put("guid", ANONYMIZED_UID);
    }
    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    map.put("ptime", Long.toString(runtimeMXBean.getUptime()));

    // product specific key value pairs
    map.put(KEY_VERSION, applicationInfo == null ? "0.0.0.0" : applicationInfo.getStrictVersion());
    map.put(KEY_PRODUCT_ID, CrashReport.PRODUCT_ANDROID_STUDIO); // must match registration with Crash
    map.put("fullVersion", applicationInfo == null ? "0.0.0.0" : applicationInfo.getFullVersion());

    map.put("osName", StringUtil.notNullize(SystemInfo.OS_NAME));
    map.put("osVersion", StringUtil.notNullize(SystemInfo.OS_VERSION));
    map.put("osArch", StringUtil.notNullize(SystemInfo.OS_ARCH));
    map.put("locale", StringUtil.notNullize(LOCALE));

    map.put("vmName", StringUtil.notNullize(runtimeMXBean.getVmName()));
    map.put("vmVendor", StringUtil.notNullize(runtimeMXBean.getVmVendor()));
    map.put("vmVersion", StringUtil.notNullize(runtimeMXBean.getVmVersion()));

    MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    map.put("heapUsed", Long.toString(usage.getUsed()));
    map.put("heapCommitted", Long.toString(usage.getCommitted()));
    map.put("heapMax", Long.toString(usage.getMax()));

    return map;
  }

  @Nullable
  private static ApplicationInfo getApplicationInfo() {
    // We obtain the ApplicationInfo only if running with an application instance. Otherwise, a call to a ServiceManager never returns..
    return ApplicationManager.getApplication() == null ? null : ApplicationInfo.getInstance();
  }
}
