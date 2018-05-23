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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue

/**
 * An interface providing access to variables available in the specific scope.
 */
interface PsVariablesScope {
  /**
   * The name of the variables scope.
   */
  val name: String

  /**
   * The title of the variables scope as it should appear in the UI.
   */
  val title: String

  /**
   * Returns a list of variables available in the scope which are suitable for use with
   * [property].
   */
  fun <ValueT : Any> getAvailableVariablesFor(property: ModelPropertyContext<ValueT>): List<Annotated<ParsedValue.Set.Parsed<ValueT>>>

  /**
   * Returns a list of the variables which are currently defined in the scope.
   */
  fun getModuleVariables(): List<PsVariable>
  /**
   * Returns the specific variable scopes (including this one) from which this the list of variables available in this scope is composed.
   */
  fun getVariableScopes(): List<PsVariablesScope>

  /**
   * Returns a new not conflicting name for a new variable in this scope based on the [preferredName].
   */
  fun getNewVariableName(preferredName: String) : String

  fun getOrCreateVariable(name: String): PsVariable

  val model: PsModel

  // TODO(solodkyy): Expose list/map manipulation methods instead.
  val container: ExtModel
}
