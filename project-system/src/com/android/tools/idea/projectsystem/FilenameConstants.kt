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
package com.android.tools.idea.projectsystem

object FilenameConstants {
  /**
   * Name used to identify a folder containing the exploded contents of an .aar file.
   */
  const val EXPLODED_AAR = "exploded-aar"

  /**
   * Directory name for Java sources generated by AAPT, used by Gradle 3.2+.
   */
  const val NOT_NAMESPACED_R_CLASS_SOURCES = "not_namespaced_r_class_sources"

  /**
   * Directory name for generated data binding base classes (e.g. "activity_demo.xml" -> "DemoBinding.java")
   *
   * These classes are base classes implemented by impl classes (e.g. "DemoBindingImpl.java") generated
   * elsewhere, and it is expected that user code will interact with them.
   *
   * TODO(b/129543943): Investigate moving this to the data binding module
   */
  const val DATA_BINDING_BASE_CLASS_SOURCES = "data_binding_base_class_source_out"

  /**
   * Directory name for navigation arg sources generated by androidx.navigation.safeargs
   */
  const val SAFE_ARG_CLASS_SOURCES = "navigation-args"

  /**
   * Default name for Gradle task outputs. In 3.2 R.java files for libraries ended up in a directory with this name.
   */
  const val OUT = "out"

  /**
   * Directory under which AGP puts generated sources.
   */
  const val GENERATED = "generated"

  /**
   * Default Gradle build directory name.
   */
  const val BUILD = "build"
}
