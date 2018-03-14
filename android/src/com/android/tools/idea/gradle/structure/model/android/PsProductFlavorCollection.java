/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.annotations.NonNull;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class PsProductFlavorCollection implements PsModelCollection<PsProductFlavor> {
  @NotNull private final Map<String, PsProductFlavor> myProductFlavorsByName = Maps.newLinkedHashMap();
  @NonNull private final PsAndroidModule myParent;

  PsProductFlavorCollection(@NonNull PsAndroidModule parent) {
    myParent = parent;
    Map<String, ProductFlavor> productFlavorsFromGradle = Maps.newHashMap();
    for (ProductFlavorContainer container : parent.getGradleModel().getAndroidProject().getProductFlavors()) {
      ProductFlavor productFlavor = container.getProductFlavor();
      productFlavorsFromGradle.put(productFlavor.getName(), productFlavor);
    }

    GradleBuildModel parsedModel = parent.getParsedModel();
    if (parsedModel != null) {
      AndroidModel android = parsedModel.android();
      if (android != null) {
        List<? extends ProductFlavorModel> parsedProductFlavors = android.productFlavors();
        for (ProductFlavorModel parsedProductFlavor : parsedProductFlavors) {
          String name = parsedProductFlavor.name();
          ProductFlavor fromGradle = productFlavorsFromGradle.remove(name);

          PsProductFlavor model = new PsProductFlavor(parent, fromGradle, parsedProductFlavor);
          myProductFlavorsByName.put(name, model);
        }
      }
    }

    if (!productFlavorsFromGradle.isEmpty()) {
      for (ProductFlavor productFlavor : productFlavorsFromGradle.values()) {
        PsProductFlavor model = new PsProductFlavor(parent, productFlavor, null);
        myProductFlavorsByName.put(productFlavor.getName(), model);
      }
    }
  }

  @Override
  public void forEach(@NotNull Consumer<PsProductFlavor> consumer) {
    myProductFlavorsByName.values().forEach(consumer);
  }

  @Nullable
  public PsProductFlavor findElement(@NotNull String name) {
    return myProductFlavorsByName.get(name);
  }

  @NotNull
  public PsProductFlavor addNew(@NotNull String name) {
    assert myParent.getParsedModel() != null;
    AndroidModel androidModel = myParent.getParsedModel().android();
    assert androidModel != null;
    androidModel.addProductFlavor(name);
    List<ProductFlavorModel> productFlavors = androidModel.productFlavors();
    PsProductFlavor model =
      new PsProductFlavor(myParent, null, productFlavors.stream().filter(it -> it.name().equals(name)).collect(MoreCollectors.onlyElement()));
    myProductFlavorsByName.put(name, model);
    myParent.setModified(true);
    return model;
  }

  public void remove(@NotNull String name) {
    assert myParent.getParsedModel() != null;
    AndroidModel androidModel = myParent.getParsedModel().android();
    assert androidModel != null;
    androidModel.removeProductFlavor(name);
    myProductFlavorsByName.remove(name);
    myParent.setModified(true);
  }
}
