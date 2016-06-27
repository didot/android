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
package com.android.tools.idea.gradle.run;

import com.android.ide.common.resources.ResourceUrl;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.intellij.lang.annotations.Language;
import org.junit.Test;
import org.w3c.dom.Document;

import java.util.Set;

import static org.junit.Assert.*;

public class GradleInstantRunContextTest {
  @Test
  public void multiProcessCheck() {
    @Language("XML") String manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertFalse(GradleInstantRunContext.manifestSpecifiesMultiProcess(manifest, ImmutableSet.<String>of()));

    manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process = \":foo\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertTrue(GradleInstantRunContext.manifestSpecifiesMultiProcess(manifest, ImmutableSet.<String>of()));

    manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process=\":leakcanary\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertFalse(GradleInstantRunContext.manifestSpecifiesMultiProcess(manifest, ImmutableSet.of(":leakcanary")));

    manifest =
      "     <application>\n" +
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process=\":leakcanary\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n" +
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process =\":foo\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n" +
      "    </application>\n";
    assertTrue(GradleInstantRunContext.manifestSpecifiesMultiProcess(manifest, ImmutableSet.of(":leakcanary")));
  }

  @Test
  public void getAppResourceReferences() throws Exception {
    @Language("XML") String manifest =
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "          package=\"com.google.samples.apps.topeka\">\n" +
      "\n" +
      "    <application android:allowBackup=\"@bool/allowBackup\"\n" +
      "                 android:icon=\"@mipmap/ic_launcher\"\n" +
      "                 android:label=\"@string/app_name\"\n" +
      "                 android:supportsRtl=\"false\"\n" +
      "                 android:theme=\"@style/Topeka\"\n" +
      "                 android:name=\".MyApplication\">\n" +
      "\n" +
      "        <activity android:name=\".activity.SignInActivity\"\n" +
      "                  android:theme=\"@style/Topeka.SignInActivity\">\n" +
      "            <intent-filter>\n" +
      "                <action android:name=\"android.intent.action.MAIN\" />\n" +
      "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
      "            </intent-filter>\n" +
      "        </activity>\n" +
      "\n" +
      "        <activity android:name=\".activity.QuizActivity\"\n" +
      "                  android:theme=\"@style/Topeka.QuizActivity\"/>\n" +
      "\n" +
      "    </application>\n" +
      "</manifest>";

    Document document = XmlUtils.parseDocument(manifest, false);
    Set<ResourceUrl> appResourceReferences = GradleInstantRunContext.getAppResourceReferences(document.getDocumentElement());
    //noinspection ConstantConditions
    assertEquals(ImmutableList.copyOf(appResourceReferences), ImmutableList.of(
      ResourceUrl.parse("@bool/allowBackup"),
      ResourceUrl.parse("@mipmap/ic_launcher"),
      ResourceUrl.parse("@string/app_name"),
      ResourceUrl.parse("@style/Topeka"),
      ResourceUrl.parse("@style/Topeka.QuizActivity"),
      ResourceUrl.parse("@style/Topeka.SignInActivity")));
  }
}
