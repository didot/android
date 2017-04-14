/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.InstantRun;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.InstantRunStub;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import java.io.Serializable;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.deserialize;
import static com.android.tools.idea.gradle.project.model.ide.android.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IdeInstantRun}.
 */
public class IdeInstantRunTest {
  @Test
  public void serializable() {
    assertThat(IdeInstantRun.class).isAssignableTo(Serializable.class);
  }

  @Test
  public void serialization() throws Exception {
    IdeInstantRun instantRun = new IdeInstantRun(new InstantRunStub());
    byte[] bytes = serialize(instantRun);
    Object o = deserialize(bytes);
    assertEquals(instantRun, o);
  }

  @Test
  public void constructor() throws Throwable {
    InstantRun original = new InstantRunStub();
    assertEqualsOrSimilar(original, new IdeInstantRun(original));
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeInstantRun.class).verify();
  }
}