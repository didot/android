/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.analytics.crash.GoogleCrashReporter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.hamcrest.core.SubstringMatcher;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class StudioCrashReporterTest {
  public static final String STACK_TRACE =
    "\tat com.android.tools.analytics.crash.GoogleCrashReporter.submit(GoogleCrashReporter.java:161)\n" +
    "\tat com.android.tools.analytics.crash.GoogleCrashReporter.submit(GoogleCrashReporter.java:145)\n" +
    "\tat com.android.tools.analytics.crash.GoogleCrashReporter.submit(GoogleCrashReporter.java:123)\n" +
    "\tat com.android.tools.analytics.crash.GoogleCrashReporterTest.main(GoogleCrashReporterTest.java:131)\n";

  private static final String SAMPLE_EXCEPTION =
    "java.lang.RuntimeException: This is a test exception message\n" +
    STACK_TRACE;

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private static final Throwable ourException =
    createExceptionFromDesc(SAMPLE_EXCEPTION, new RuntimeException("This is a test exception message"));


  @Test
  public void serializeNonGracefulExit() throws Exception {
    CrashReport report =
      new StudioCrashReport.Builder()
        .setDescriptions(Lists.newArrayList("1.2.3.4\n1.8.0_152-release-1136-b01"))
        .build();

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    HttpEntity httpEntity = builder.build();
    String content = new String(ByteStreams.toByteArray(httpEntity.getContent()), Charset.defaultCharset());

    assertRequestContainsField(content, "exception_info",
                               "com.android.tools.idea.diagnostics.crash.exception.NonGracefulExitException");
  }

  @Test
  public void serializeJvmCrash() throws Exception {
    CrashReport report =
      new StudioCrashReport.Builder()
        .setDescriptions(Lists.newArrayList("1.2.3.4\n1.8.0_152-release-1136-b01"))
        .setIsJvmCrash(true)
        .build();

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    HttpEntity httpEntity = builder.build();
    String request = new String(ByteStreams.toByteArray(httpEntity.getContent()), Charset.defaultCharset());

    assertRequestContainsField(request, "exception_info",
                               "com.android.tools.idea.diagnostics.crash.exception.JvmCrashException");
  }

  @Test
  public void serializeKotlinException() throws Exception {
    final String exceptionWithKotlinString =
      "java.lang.RuntimeException: Kotlin message\n" +
      "\tat com.intellij.util.UniqueResultsQuery.forEach(UniqueResultsQuery.java:57)\n" +
      "\tat org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection$hasReferences$referenceUsed$2.invoke(UnusedSymbolInspection.kt:268)\n" +
      "\tat org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection$hasReferences$referenceUsed$2.invoke(UnusedSymbolInspection.kt:74)\n" +
      "\tat kotlin.SynchronizedLazyImpl.getValue(Lazy.kt:131)\n";
    StudioExceptionReport report = spy(
      new StudioExceptionReport.Builder()
        .setThrowable(createExceptionFromDesc(exceptionWithKotlinString, null))
        .build());

    doReturn("1.2.3.4").when(report).getKotlinPluginVersionDescription();

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    HttpEntity httpEntity = builder.build();
    String request = new String(ByteStreams.toByteArray(httpEntity.getContent()), Charset.defaultCharset());

    assertRequestContainsField(request, "kotlinVersion", "1.2.3.4");
  }

  private static void assertRequestContainsField(final String requestBody, final String name, final String value) {
    assertThat(requestBody, new RegexMatcher(
      "(?s).*\r?\nContent-Disposition: form-data; name=\"" + Pattern.quote(name) + "\"\r?\n" +
      "Content-Type: .*?\r?\n" +
      "Content-Transfer-Encoding: 8bit\r?\n" +
      "\r?\n" +
      Pattern.quote(value) + "\r?\n.*"
    ));
  }

  @Test
  public void serializePerformanceReportInvalidThreadDump() throws Exception {
    CrashReport report =
      new StudioPerformanceWatcherReport.Builder()
        .setFile("threadDump.txt")
        .setThreadDump("Not a thread dump")
        .build();

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    HttpEntity httpEntity = builder.build();
    String request = new String(ByteStreams.toByteArray(httpEntity.getContent()), Charset.defaultCharset());

    assertRequestContainsField(request, "exception_info", "com.android.ApplicationNotResponding: ");
  }

  @Test
  public void serializePerformanceReportValidThreadDump() throws Exception {
    CrashReport report =
      new StudioPerformanceWatcherReport.Builder()
        .setFile("threadDump.txt")
        .setThreadDump("\"AWT-EventQueue-0 2.3#__BUILD_NUMBER__ Studio, eap:true, os:Linux 3.13.0-93-generic\" prio=0 tid=0x0 nid=0x0 waiting on condition\n" +
                       "     java.lang.Thread.State: WAITING\n" +
                       " on java.util.concurrent.FutureTask@12345678\n" +
                       "\tat sun.misc.Unsafe.park(Native Method)\n\n")
        .build();

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    HttpEntity httpEntity = builder.build();
    String request = new String(ByteStreams.toByteArray(httpEntity.getContent()), Charset.defaultCharset());

    assertRequestContainsField(request, "exception_info",
                                "com.android.ApplicationNotResponding: AWT-EventQueue-0 WAITING on java.util.concurrent.FutureTask@12345678\n" +
                                "\tat sun.misc.Unsafe.park(Native Method)");
  }

  public static void main(String[] args) {
    GoogleCrashReporter crash = new StudioCrashReporter();

    submit(crash, new StudioExceptionReport.Builder().setThrowable(ourException).build());
    submit(
      crash,
      new StudioCrashReport.Builder()
        .setDescriptions(ImmutableList.of("Test description."))
        .setIsJvmCrash(true)
        .setUptimeInMs(123456)
        .build());
    submit(crash, new StudioPerformanceWatcherReport.Builder().setFile("fileName").setThreadDump("threadDump").build());
  }

  private static void submit(@NonNull GoogleCrashReporter reporter, @NonNull CrashReport report) {
    CompletableFuture<String> response = reporter.submit(report, true);
    try {
      String reportId = response.get(20, TimeUnit.SECONDS);
      System.out.println("View report at http://go/crash-staging/" + reportId);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Copied from RenderErrorPanelTest
  @SuppressWarnings("ThrowableInstanceNeverThrown")
  private static Throwable createExceptionFromDesc(String desc, @Nullable Throwable throwable) {
    // First line: description and type
    Iterator<String> iterator = Arrays.asList(desc.split("\n")).iterator(); // Splitter.on('\n').split(desc).iterator();
    assertTrue(iterator.hasNext());
    final String first = iterator.next();
    assertTrue(iterator.hasNext());
    String message = null;
    String exceptionClass;
    int index = first.indexOf(':');
    if (index != -1) {
      exceptionClass = first.substring(0, index).trim();
      message = first.substring(index + 1).trim();
    }
    else {
      exceptionClass = first.trim();
    }

    if (throwable == null) {
      try {
        @SuppressWarnings("unchecked")
        Class<Throwable> clz = (Class<Throwable>)Class.forName(exceptionClass);
        if (message == null) {
          throwable = clz.newInstance();
        }
        else {
          Constructor<Throwable> constructor = clz.getConstructor(String.class);
          throwable = constructor.newInstance(message);
        }
      }
      catch (Throwable t) {
        if (message == null) {
          throwable = new Throwable() {
            @Override
            public String getMessage() {
              return first;
            }

            @Override
            public String toString() {
              return first;
            }
          };
        }
        else {
          throwable = new Throwable(message);
        }
      }
    }

    List<StackTraceElement> frames = new ArrayList<StackTraceElement>();
    Pattern outerPattern = Pattern.compile("\tat (.*)\\.([^.]*)\\((.*)\\)");
    Pattern innerPattern = Pattern.compile("(.*):(\\d*)");
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.isEmpty()) {
        break;
      }
      Matcher outerMatcher = outerPattern.matcher(line);
      if (!outerMatcher.matches()) {
        fail("Line " + line + " does not match expected stactrace pattern");
      }
      else {
        String clz = outerMatcher.group(1);
        String method = outerMatcher.group(2);
        String inner = outerMatcher.group(3);
        if (inner.equals("Native Method")) {
          frames.add(new StackTraceElement(clz, method, null, -2));
        }
        else if (inner.equals("Unknown Source")) {
          frames.add(new StackTraceElement(clz, method, null, -1));
        }
        else {
          Matcher innerMatcher = innerPattern.matcher(inner);
          if (!innerMatcher.matches()) {
            fail("Trace parameter list " + inner + " does not match expected pattern");
          }
          else {
            String file = innerMatcher.group(1);
            int lineNum = Integer.parseInt(innerMatcher.group(2));
            frames.add(new StackTraceElement(clz, method, file, lineNum));
          }
        }
      }
    }

    throwable.setStackTrace(frames.toArray(new StackTraceElement[frames.size()]));

    // Dump stack back to string to make sure we have the same exception
    assertEquals(desc, getStackTrace(throwable));

    return throwable;
  }

  @NonNull
  private static String getStackTrace(@NonNull Throwable t) {
    final StringWriter stringWriter = new StringWriter();
    try (PrintWriter writer = new PrintWriter(stringWriter)) {
      t.printStackTrace(writer);
    }

    return stringWriter.toString().replace("\r", "");
  }

  private static class RegexMatcher extends SubstringMatcher {

    private Pattern myPattern;

    public RegexMatcher(@NonNull String patternString) {
      super(patternString);
      myPattern = Pattern.compile(patternString);
    }

    @Override
    protected boolean evalSubstringOf(String string) {
      return myPattern.matcher(string).matches();
    }

    @Override
    protected String relationship() {
      return "matches regular expression";
    }
  }
}
