// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.aidl.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AidlMethodDeclaration extends AidlDeclaration {

  @Nullable
  AidlDeclarationName getDeclarationName();

  @NotNull
  List<AidlParameter> getParameterList();

  @NotNull
  AidlType getType();

  @Nullable
  PsiElement getIdvalue();

}
