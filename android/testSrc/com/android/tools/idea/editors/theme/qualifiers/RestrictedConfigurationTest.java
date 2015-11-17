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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.*;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.google.common.collect.Lists;
import org.jetbrains.android.AndroidTestCase;

import java.util.ArrayList;
import java.util.List;


public class RestrictedConfigurationTest extends AndroidTestCase {

  private void checkRestrictConfigurationFor(String compatibleQualifier, String answerQualifier, String... incompatibleQualifiers) {
    ArrayList<FolderConfiguration> incompatibles = Lists.newArrayList();
    for (int i = 0; i < incompatibleQualifiers.length; ++i) {
      incompatibles.add(FolderConfiguration.getConfigForQualifierString(incompatibleQualifiers[i]));
      assertNotNull(incompatibles.get(i));
    }
    final FolderConfiguration compatible = FolderConfiguration.getConfigForQualifierString(compatibleQualifier);
    assertNotNull(compatible);
    FolderConfiguration restrictedConfiguration = RestrictedConfiguration.restrictConfiguration(compatible, incompatibles).getAny();
    assertNotNull(restrictedConfiguration);

    // folderConfiguration.getUniqueKey() returns with a "-"
    if (!answerQualifier.isEmpty()) {
      answerQualifier = "-" + answerQualifier;
    }
    assertEquals(answerQualifier, restrictedConfiguration.getUniqueKey());

    // Making sure that 'restrictedConfiguration' matches only with 'compatible'
    List<ConfiguredElement<String>> allConfigurations = Lists.newArrayList();
    allConfigurations.add(ConfiguredElement.create(compatible, ""));
    for (FolderConfiguration incompatible : incompatibles) {
      allConfigurations.add(ConfiguredElement.create(incompatible, ""));
    }

    List<Configurable> matches = restrictedConfiguration.findMatchingConfigurables(allConfigurations);
    assertEquals(1, matches.size());
    assertEquals(compatible, matches.get(0).getConfiguration());
  }

  /**
   * Tests restrictConfiguration method, which should work backward to algorithm from the following link
   * See: http://developer.android.com/guide/topics/resources/providing-resources.html
   */
  public void testRestrictConfiguration() {

    assertEquals(21, FolderConfiguration.getQualifierCount());

    checkRestrictConfigurationFor("en-v21", "en", "ldrtl", "ldltr");
    checkRestrictConfigurationFor("en", "en", "fr-v23", "fr-v19");
    // "__" - is a fake language qualifier, see: LocaleQualifier.FAKE_VALUE
    checkRestrictConfigurationFor("v11", "__-v11", "v7", "en");
    checkRestrictConfigurationFor("en", "en", "v9");
    checkRestrictConfigurationFor("fr", "fr", "en-v7", "en-v11");
    checkRestrictConfigurationFor("land-hdpi", "land-hdpi", "land");
    checkRestrictConfigurationFor("en", "en-port", "en-land", "port");
    checkRestrictConfigurationFor("en", "en-night-v19", "en-notnight-v21", "en-notnight-v21", "en-v20", "fr-night-v18");
    checkRestrictConfigurationFor("land", "land", "v21", "v19");
    checkRestrictConfigurationFor("", "__-sw599dp-v14", "v21", "de", "sw600dp", "ar", "v15", "pt-rPT", "pt-rBR", "v16");

    checkRestrictConfigurationFor("", "__", "en", "fr", "kz", "ru"); // LocaleQualifier
    checkRestrictConfigurationFor("", "ldltr", "ldrtl", "ldrtl"); // LayoutDirectionQualifier
    checkRestrictConfigurationFor("", "long", "notlong"); // ScreenRationQualifier
    checkRestrictConfigurationFor("", "notround", "round", "round"); // ScreenRoundQualifier
    checkRestrictConfigurationFor("", "land", "port", "port"); // ScreenOrientationQualifier
    checkRestrictConfigurationFor("", "notnight", "night"); // NightModeQualifier
    checkRestrictConfigurationFor("", "finger", "notouch", "stylus"); // TouchScreenQualifier
    checkRestrictConfigurationFor("", "keysexposed", "keyshidden", "keyssoft"); // KeyboardStateQualifier
    checkRestrictConfigurationFor("", "nokeys", "qwerty", "12key"); // TextInputMethodQualifier
    checkRestrictConfigurationFor("", "nonav", "dpad", "trackball", "wheel"); // NavigationMethodQualifier
    checkRestrictConfigurationFor("", "v14", "v21", "v16", "v15", "v16"); // VersionQualifier
  }
}
