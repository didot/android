/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import java.util.List;

import static com.android.tools.idea.gradle.parser.BuildFileKeyType.*;

/**
 * Enumerates the values we know how to parse out of the build file. This includes values that only occur in one place
 * and are always rooted at the file root (e.g. android/buildToolsVersion) and values that can occur at different places (e.g.
 * signingConfig, which can occur in defaultConfig, a build type, or a flavor. When retrieving keys that are of the former type, you
 * can call {@link GradleBuildFile#getValue(BuildFileKey)}, which uses the build file itself as
 * the root; in the case of the latter, call
 * {@link GradleBuildFile#getValue(org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner, BuildFileKey)}
 * and pass in the block that is the root of the key's path.
 * <p>
 * Values have types. Scalar types like string, integer, etc. are self-explanatory, but complex closure types are supported as well. In
 * that case, you can supply a factory class that knows how to instantiate container instances for subobjects that are found within a
 * given closure in the build file.
 */
public enum BuildFileKey {
  // Buildscript block
  PLUGIN_CLASSPATH("buildscript/dependencies/classpath", STRING),
  PLUGIN_REPOSITORY("buildscript/repositories", "Android Plugin Repository", CLOSURE, Repository.getFactory()),
  PLUGIN_VERSION("buildscript/dependencies/classpath", "Android Plugin Version", STRING, null) {
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      // PLUGIN_CLASSPATH is STRING type; we're guaranteed the getValue result can be cast to String.
      String s = (String)PLUGIN_CLASSPATH.getValue(arg);
      if (s != null && s.startsWith(SdkConstants.GRADLE_PLUGIN_NAME)) {
        return s.substring(SdkConstants.GRADLE_PLUGIN_NAME.length());
      } else {
        return null;
      }
    }

    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      PLUGIN_CLASSPATH.setValue(arg, SdkConstants.GRADLE_PLUGIN_NAME + value);
    }
  },

  LIBRARY_REPOSITORY("repositories", "Library Repository", CLOSURE, Repository.getFactory()),

  // Dependencies block
  DEPENDENCIES("dependencies", null, CLOSURE, Dependency.getFactory()),

  // defaultConfig or build flavor
  MIN_SDK_VERSION("minSdkVersion", INTEGER),
  PACKAGE_NAME("packageName", STRING),
  PROGUARD_FILE("proguardFile", FILE_AS_STRING),
  SIGNING_CONFIG("signingConfig", REFERENCE),
  TARGET_SDK_VERSION("targetSdkVersion", INTEGER),
  TEST_INSTRUMENTATION_RUNNER("testInstrumentationRunner", STRING),
  TEST_PACKAGE_NAME("testPackageName", STRING),
  VERSION_CODE("versionCode", INTEGER),
  VERSION_NAME("versionName", STRING),

  // Build type
  DEBUGGABLE("debuggable", BOOLEAN),
  JNI_DEBUG_BUILD("jniDebugBuild", BOOLEAN),
  RENDERSCRIPT_DEBUG_BUILD("renderscriptDebugBuild", STRING),
  RENDERSCRIPT_OPTIM_LEVEL("renderscriptOptimLevel", STRING),
  RUN_PROGUARD("runProguard", BOOLEAN),
  PACKAGE_NAME_SUFFIX("packageNameSuffix", STRING),
  VERSION_NAME_SUFFIX("versionNameSuffix", STRING),
  ZIP_ALIGN("zipAlign", BOOLEAN),

  // Signing config
  KEY_ALIAS("keyAlias", STRING),
  KEY_PASSWORD("keyPassword", STRING),
  STORE_FILE("storeFile", FILE),
  STORE_PASSWORD("storePassword", STRING),

  // Android block
  BUILD_TOOLS_VERSION("android/buildToolsVersion", STRING),
  COMPILE_SDK_VERSION("android/compileSdkVersion", INTEGER),
  IGNORE_ASSETS_PATTERN("android/aaptOptions/ignoreAssetsPattern", STRING),
  INCREMENTAL("android/dexOptions/incremental", BOOLEAN),
  NO_COMPRESS("android/aaptOptions/noCompress", STRING), // TODO: Implement properly. This is not a simple literal.
  SOURCE_COMPATIBILITY("android/compileOptions/sourceCompatibility", STRING),  // TODO: This is an assignment, not a method call.
  TARGET_COMPATIBILITY("android/compileOptions/targetCompatibility", STRING),  // TODO: This is an assignment, not a method call.

  // These complex types are named entities that within them have key/value pairs where the keys are BuildFileKey instances themselves.
  // We can use a generic container class to deal with them.

  // It would be nice if these lists of sub-parameters were static constants, but that results in unresolvable circular references.
  SIGNING_CONFIGS("android/signingConfigs", null, CLOSURE,
                  NamedObject.getFactory(ImmutableList.of(KEY_ALIAS, KEY_PASSWORD, STORE_FILE, STORE_PASSWORD))),
  FLAVORS("android/productFlavors", null, CLOSURE,
          NamedObject.getFactory(ImmutableList.of(MIN_SDK_VERSION, PACKAGE_NAME, PROGUARD_FILE, SIGNING_CONFIG, TARGET_SDK_VERSION,
                                                  TEST_INSTRUMENTATION_RUNNER, TEST_PACKAGE_NAME, VERSION_CODE, VERSION_NAME))),
  BUILD_TYPES("android/buildTypes", null, CLOSURE,
              NamedObject.getFactory(ImmutableList
                                       .of(DEBUGGABLE, JNI_DEBUG_BUILD, SIGNING_CONFIG, RENDERSCRIPT_DEBUG_BUILD, RENDERSCRIPT_OPTIM_LEVEL,
                                           RUN_PROGUARD, PROGUARD_FILE, PACKAGE_NAME_SUFFIX, VERSION_NAME_SUFFIX, ZIP_ALIGN)));

  private final String myPath;
  private final BuildFileKeyType myType;
  private final ValueFactory myValueFactory;
  private final String myDisplayName;

  BuildFileKey(@NotNull String path, @NotNull BuildFileKeyType type) {
    this(path, null, type, null);
  }

  /**
   * @param path an XPath-like identifier to a method call which may be inside nested closures. For example, "a/b/c" will identify
   *             <code>a { b { c("value") } }</code>
   */
  BuildFileKey(@NotNull String path, @Nullable String displayName, @NotNull BuildFileKeyType type, @Nullable ValueFactory factory) {
    myPath = path;
    myType = type;
    myValueFactory = factory;

    if (displayName != null) {
      myDisplayName = displayName;
    } else {
      int lastSlash = myPath.lastIndexOf('/');
      myDisplayName = splitCamelCase(lastSlash >= 0 ? myPath.substring(lastSlash + 1) : myPath);
    }
  }

  @NotNull
  @VisibleForTesting
  static String splitCamelCase(@NotNull String string) {
    StringBuilder sb = new StringBuilder(2 * string.length());
    int n = string.length();
    boolean lastWasUpperCase = Character.isUpperCase(string.charAt(0));
    boolean capitalizeNext = true;
    for (int i = 0; i < n; i++) {
      char c = string.charAt(i);
      boolean isUpperCase = Character.isUpperCase(c);
      if (isUpperCase && !lastWasUpperCase) {
        sb.append(' ');
        capitalizeNext = true;
      }
      lastWasUpperCase = isUpperCase;
      if (capitalizeNext) {
        c = Character.toUpperCase(c);
        capitalizeNext = false;
      } else {
        c = Character.toLowerCase(c);
      }
      sb.append(c);
    }

    return sb.toString();
  }

  @NotNull
  static String escapeLiteralString(@Nullable Object o) {
    if (o == null) {
      return "";
    }
    return o.toString().replace("'", "\\'");
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  public BuildFileKeyType getType() {
    return myType;
  }

  @Nullable
  public ValueFactory getValueFactory() {
    return myValueFactory;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @Nullable
  protected Object getValue(@NotNull GroovyPsiElement arg) {
    if (myValueFactory != null && arg instanceof GrClosableBlock) {
      return myValueFactory.getValues((GrClosableBlock) arg);
    }
    return myType.getValue(arg);
  }

  protected void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
    if (myValueFactory != null && arg instanceof GrClosableBlock && value instanceof List) {
      myValueFactory.setValues((GrClosableBlock) arg, (List)value);
    } else {
      myType.setValue(arg, value);
    }
  }
}
