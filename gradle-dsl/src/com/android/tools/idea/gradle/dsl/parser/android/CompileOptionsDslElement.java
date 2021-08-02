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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.CompileOptionsModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class CompileOptionsDslElement extends BaseCompileOptionsDslElement {

  public static final ExternalToModelMap ktsToModelNameMap =
    Stream.concat(
      BaseCompileOptionsDslElement.ktsToModelNameMap.getEntrySet().stream().map(data -> new Object[]{
        data.surfaceSyntaxDescription.name, data.surfaceSyntaxDescription.arity,
        data.modelEffectDescription.property, data.modelEffectDescription.semantics,
        data.versionConstraint
      }),
      Stream.of(new Object[][]{
        {"encoding", property, ENCODING, VAR},
        {"incremental", property, INCREMENTAL, VAR}
      })).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap =
    Stream.concat(
      BaseCompileOptionsDslElement.groovyToModelNameMap.getEntrySet().stream().map(data -> new Object[]{
        data.surfaceSyntaxDescription.name, data.surfaceSyntaxDescription.arity,
        data.modelEffectDescription.property, data.modelEffectDescription.semantics,
        data.versionConstraint
      }),
      Stream.of(new Object[][]{
        {"encoding", property, ENCODING, VAR},
        {"encoding", exactly(1), ENCODING, SET},
        {"incremental", property, INCREMENTAL, VAR},
        {"incremental", exactly(1), INCREMENTAL, SET},
      })).collect(toModelMap());
  public static final PropertiesElementDescription<CompileOptionsDslElement> COMPILE_OPTIONS =
    new PropertiesElementDescription<>("compileOptions", CompileOptionsDslElement.class, CompileOptionsDslElement::new);

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap);
  }

  public CompileOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
