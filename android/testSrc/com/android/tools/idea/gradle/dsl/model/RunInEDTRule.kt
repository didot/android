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
package com.android.tools.idea.gradle.dsl.model

import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ThrowableRunnable
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RunInEDTRule : TestRule {
  override fun apply(base: Statement?, description: Description?): Statement {
    return object : Statement() {
      override fun evaluate() {
        EdtTestUtil.runInEdtAndWait(ThrowableRunnable { base?.evaluate() })
      }
    }
  }
}