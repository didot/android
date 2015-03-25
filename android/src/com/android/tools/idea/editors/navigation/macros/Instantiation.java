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
package com.android.tools.idea.editors.navigation.macros;

import com.intellij.psi.*;

import java.util.Map;

public class Instantiation {
  public static PsiElement instantiate(CodeTemplate template, final Map<String, PsiElement> bindings) {
    for (String parameter : template.getParameters()) {
      assert bindings.containsKey(parameter);
    }

    PsiElement body = template.getBody();

    PsiElement result = body.copy();

    result.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitIdentifier(PsiIdentifier identifier) {
        PsiElement newElement = bindings.get(identifier.getText());
        if (newElement != null) {
          identifier.replace(newElement);
        }
      }
    });
    return result;
  }

  public static String instantiate2(CodeTemplate template, final Map<String, String> bindings) {
    for (String parameter : template.getParameters()) {
      assert bindings.containsKey(parameter);
    }

    PsiElement body = template.getBody();
    String result = body.getText();

    result = replaceAll(result, ".$()", ""); // remove the method calling artifact

    for (Map.Entry<String, String> entry : bindings.entrySet()) {
      result = replaceAll(result, entry.getKey(), entry.getValue());
    }

    return result;
  }

  // todo use Matcher
  private static String replaceAll(String result, String key, String value) {
    while (result.contains(key)) {
      result = result.replace(key, value);
    }
    return result;
  }
}
