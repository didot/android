// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes.*;
import com.android.tools.idea.compose.preview.util.device.parser.*;

public class DeviceSpecParentParamImpl extends DeviceSpecParamImpl implements DeviceSpecParentParam {

  public DeviceSpecParentParamImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull DeviceSpecVisitor visitor) {
    visitor.visitParentParam(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DeviceSpecVisitor) accept((DeviceSpecVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getStringT() {
    return findNotNullChildByType(STRING_T);
  }

}
