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
package com.android.tools.profilers.stacktrace;

import com.google.common.collect.Iterators;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.List;

public final class CodeLocation {
  public static final int INVALID_LINE_NUMBER = -1;
  @Nullable
  private final String myClassName;
  @Nullable
  private final String myFileName;
  @Nullable
  private final String myMethodName;
  /**
   * See {@link Builder#setMethodSignature(String)} for details about this field.
   */
  @Nullable
  private final String mySignature;
  /**
   * See {@link Builder#setMethodParameters(String)} for details about this field.
   */
  @Nullable
  private final List<String> myMethodParameters;

  private final int myLineNumber;
  private final boolean myNativeCode;
  @Nullable
  private final String myNativeModuleName;
  private final int myHashcode;

  private CodeLocation(@NotNull Builder builder) {
    myClassName = builder.myClassName;
    myFileName = builder.myFileName;
    myMethodName = builder.myMethodName;
    mySignature = builder.mySignature;
    myMethodParameters = builder.myMethodParameters;
    myLineNumber = builder.myLineNumber;
    myNativeCode = builder.myNativeCode;
    myNativeModuleName = builder.myNativeModuleName;
    myHashcode = Arrays.hashCode(new int[]{
      myClassName == null ? 0 : myClassName.hashCode(),
      myFileName == null ? 0 : myFileName.hashCode(),
      myMethodName == null ? 0 : myMethodName.hashCode(),
      mySignature == null ? 0 : mySignature.hashCode(),
      myNativeModuleName == null ? 0 : myNativeModuleName.hashCode(),
      Integer.hashCode(myLineNumber)});
  }

  @TestOnly
  @NotNull
  public static CodeLocation stub() {
    return new CodeLocation.Builder("").build();
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }

  /**
   * Convenience method for stripping all inner classes (e.g. anything following the first "$")
   * from {@link #getClassName()}. If this code location's class is already an outer class, its
   * name is returned as is.
   */
  @Nullable
  public String getOuterClassName() {
    if (myClassName == null) {
      return null;
    }
    int innerCharIndex = myClassName.indexOf('$');
    if (innerCharIndex < 0) {
      innerCharIndex = myClassName.length();
    }
    return myClassName.substring(0, innerCharIndex);
  }

  @Nullable
  public String getFileName() {
    return myFileName;
  }

  @Nullable
  public String getMethodName() {
    return myMethodName;
  }

  /**
   * @param lineNumber 0-based line number
   */
  public int getLineNumber() {
    return myLineNumber;
  }

  /**
   * See {@link Builder#setMethodSignature(String)} for details about this value.
   */
  @Nullable
  public String getSignature() {
    return mySignature;
  }

  @Nullable
  public List<String> getMethodParameters() {
    return myMethodParameters;
  }

  public boolean isNativeCode() {
    return myNativeCode;
  }

  @Nullable
  public String getNativeModuleName() {
    return myNativeModuleName;
  }

  @Override
  public int hashCode() {
    return myHashcode;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CodeLocation)) {
      return false;
    }

    CodeLocation other = (CodeLocation)obj;
    return StringUtil.equals(myClassName, other.myClassName) &&
           StringUtil.equals(myFileName, other.myFileName) &&
           StringUtil.equals(myMethodName, other.myMethodName) &&
           StringUtil.equals(mySignature, other.mySignature) &&
           StringUtil.equals(myNativeModuleName, other.myNativeModuleName) &&
           myLineNumber == other.myLineNumber &&
           myNativeCode == other.myNativeCode;
  }

  public static final class Builder {
    @Nullable private final String myClassName;
    @Nullable String myFileName;
    @Nullable String myMethodName;
    @Nullable String mySignature;
    @Nullable List<String> myMethodParameters;
    int myLineNumber = INVALID_LINE_NUMBER;
    boolean myNativeCode;
    String myNativeModuleName;

    public Builder(@Nullable String className) {
      myClassName = className;
    }

    public Builder(@NotNull CodeLocation rhs) {
      myClassName = rhs.getClassName();
      myFileName = rhs.getFileName();
      myMethodName = rhs.getMethodName();
      mySignature = rhs.getSignature();
      myMethodParameters = rhs.getMethodParameters();
      myLineNumber = rhs.getLineNumber();
      myNativeCode = rhs.myNativeCode;
      myNativeModuleName = rhs.myNativeModuleName;
    }

    @NotNull
    public Builder setFileName(@Nullable String fileName) {
      myFileName = StringUtil.nullize(fileName); // "" is an invalid name and should be converted to null
      return this;
    }

    @NotNull
    public Builder setMethodName(@Nullable String methodName) {
      myMethodName = StringUtil.nullize(methodName); // "" is an invalid name and should be converted to null
      return this;
    }

    /**
     * Signature of a method, encoded by java type encoding
     *
     * For example:
     * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)}
     * the signature is (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
     *
     * Java encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
     */
    @NotNull
    public Builder setMethodSignature(@NotNull String signature) {
      mySignature = signature;
      return this;
    }

    /**
     * Parameters of a method or function.
     *
     * For example, {@code int aMethod(int a, float b} produces {@code ["int", "float"]} as the list of parameters.
     */
    @NotNull
    public Builder setMethodParameters(@NotNull List<String> methodParameters) {
      myMethodParameters = methodParameters;
      return this;
    }

    /**
     * @param lineNumber 0-based line number.
     */
    @NotNull
    public Builder setLineNumber(int lineNumber) {
      myLineNumber = lineNumber;
      return this;
    }

    public Builder setNativeCode(boolean nativeCode) {
      myNativeCode = nativeCode;
      return this;
    }

    /**
     * Name of a native library (like libunity.so).
     */
    public Builder setNativeModuleName(String name) {
      myNativeModuleName = name;
      return this;
    }

    @NotNull
    public CodeLocation build() {
      return new CodeLocation(this);
    }
  }
}
