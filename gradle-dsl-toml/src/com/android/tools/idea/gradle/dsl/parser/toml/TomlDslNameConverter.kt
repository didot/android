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
package com.android.tools.idea.gradle.dsl.parser.toml

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.TOML
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment

interface TomlDslNameConverter: GradleDslNameConverter {
  override fun getKind() = TOML

  @JvmDefault
  override fun psiToName(element: PsiElement): String = when(element) {
    is TomlKeySegment -> GradleNameElement.escape(element.name ?: element.text)
    is TomlKey -> element.segments.let { segments ->
      GradleNameElement.join(segments.map { segment -> segment.name ?: return@let null })
    } ?: GradleNameElement.escape(element.text)
    else -> GradleNameElement.escape(element.text)
  }
}