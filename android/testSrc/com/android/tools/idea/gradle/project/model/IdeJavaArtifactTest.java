/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import static com.android.tools.idea.gradle.project.model.IdeModelTestUtils.assertEqualsOrSimilar;

import com.android.builder.model.JavaArtifact;
import com.android.tools.idea.gradle.model.IdeJavaArtifact;
import com.android.tools.idea.gradle.model.stubs.JavaArtifactStub;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCache;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCacheTesting;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeJavaArtifact}. */
public class IdeJavaArtifactTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void constructor() throws Throwable {
        JavaArtifact original = new JavaArtifactStub();
        IdeJavaArtifact copy = myModelCache.javaArtifactFrom(original);
        assertEqualsOrSimilar(original, copy);
    }
}
