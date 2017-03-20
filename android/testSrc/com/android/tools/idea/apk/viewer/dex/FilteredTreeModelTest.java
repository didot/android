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
package com.android.tools.idea.apk.viewer.dex;

import com.android.tools.idea.apk.viewer.dex.tree.DexElementNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexPackageNode;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.proguard.ProguardUsagesMap;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.junit.Test;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;

import static com.android.tools.idea.apk.dex.DexFiles.getDexFile;
import static org.junit.Assert.assertEquals;

public class FilteredTreeModelTest {

  @Test
  public void fieldsAndMethodReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
    DexPackageNode packageTreeNode = new PackageTreeCreator(null, false).constructPackageTree(dexFile);

    DexViewFilters options = new DexViewFilters();
    options.setShowFields(true);
    options.setShowMethods(true);
    options.setShowReferencedNodes(true);

    FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
    assertEquals("root: 27,33\n" +
                 "  com: 27,27\n" +
                 "    example: 27,27\n" +
                 "      MyAbstractClas: 6,6\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "        com.example.MyAbstractClas getInstance(): 1,1\n" +
                 "        void privateMethod(): 1,1\n" +
                 "        void publicMethod(): 1,1\n" +
                 "      MyAbstractClas$1: 3,3\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "      MyClass$NonStaticInnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      BuildConfig: 2,2\n" +
                 "        void <clinit>(): 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        java.lang.String APPLICATION_ID: 0,0\n" +
                 "        java.lang.String BUILD_TYPE: 0,0\n" +
                 "        boolean DEBUG: 0,0\n" +
                 "        java.lang.String FLAVOR: 0,0\n" +
                 "        int VERSION_CODE: 0,0\n" +
                 "        java.lang.String VERSION_NAME: 0,0\n" +
                 "      MyClass$StaticClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyClass method(): 1,1\n" +
                 "      MyClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void method(): 1,1\n" +
                 "        com.example.MyClass anon: 0,0\n" +
                 "        com.example.MyAbstractClas initializedField: 0,0\n" +
                 "        int privateIntField: 0,0\n" +
                 "        java.lang.String privateString: 0,0\n" +
                 "        int publicIntField: 0,0\n" +
                 "        java.lang.String publicStringField: 0,0\n" +
                 "      MyClass$StaticClass$InnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass$StaticClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "        com.example.MyClass$StaticClass this$0: 0,0\n" +
                 "      MainActivity: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void onCreate(android.os.Bundle): 1,1\n" +
                 "      R$string: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int app_name: 0,0\n" +
                 "      MyClass$1: 1,1\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      R$color: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int colorAccent: 0,0\n" +
                 "        int colorPrimary: 0,0\n" +
                 "        int colorPrimaryDark: 0,0\n" +
                 "      R$attr: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      R$mipmap: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int ic_launcher: 0,0\n" +
                 "        int ic_launcher_round: 0,0\n" +
                 "      R: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "  java: 0,4\n" +
                 "    lang: 0,2\n" +
                 "      Object: 0,1\n" +
                 "        void <init>(): 0,1\n" +
                 "      Boolean: 0,1\n" +
                 "        boolean parseBoolean(java.lang.String): 0,1\n" +
                 "      System: 0,0\n" +
                 "        java.io.PrintStream err: 0,0\n" +
                 "        java.io.PrintStream out: 0,0\n" +
                 "    io: 0,2\n" +
                 "      PrintStream: 0,2\n" +
                 "        void println(int): 0,1\n" +
                 "        void println(java.lang.String): 0,1\n" +
                 "  android: 0,2\n" +
                 "    app: 0,2\n" +
                 "      Activity: 0,2\n" +
                 "        void <init>(): 0,1\n" +
                 "        void onCreate(android.os.Bundle): 0,1\n", sb.toString());
  }

  @Test
  public void fieldsOnlyReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
    DexPackageNode packageTreeNode = new PackageTreeCreator(null, false).constructPackageTree(dexFile);
    DexViewFilters options = new DexViewFilters();
    options.setShowFields(true);
    options.setShowMethods(false);
    options.setShowReferencedNodes(true);
    FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
    assertEquals("root: 27,33\n" +
                 "  com: 27,27\n" +
                 "    example: 27,27\n" +
                 "      MyAbstractClas: 6,6\n" +
                 "      MyAbstractClas$1: 3,3\n" +
                 "      MyClass$NonStaticInnerClass: 2,2\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      BuildConfig: 2,2\n" +
                 "        java.lang.String APPLICATION_ID: 0,0\n" +
                 "        java.lang.String BUILD_TYPE: 0,0\n" +
                 "        boolean DEBUG: 0,0\n" +
                 "        java.lang.String FLAVOR: 0,0\n" +
                 "        int VERSION_CODE: 0,0\n" +
                 "        java.lang.String VERSION_NAME: 0,0\n" +
                 "      MyClass$StaticClass: 2,2\n" +
                 "      MyClass: 2,2\n" +
                 "        com.example.MyClass anon: 0,0\n" +
                 "        com.example.MyAbstractClas initializedField: 0,0\n" +
                 "        int privateIntField: 0,0\n" +
                 "        java.lang.String privateString: 0,0\n" +
                 "        int publicIntField: 0,0\n" +
                 "        java.lang.String publicStringField: 0,0\n" +
                 "      MyClass$StaticClass$InnerClass: 2,2\n" +
                 "        com.example.MyClass$StaticClass this$0: 0,0\n" +
                 "      MainActivity: 2,2\n" +
                 "      R$string: 1,1\n" +
                 "        int app_name: 0,0\n" +
                 "      MyClass$1: 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      R$color: 1,1\n" +
                 "        int colorAccent: 0,0\n" +
                 "        int colorPrimary: 0,0\n" +
                 "        int colorPrimaryDark: 0,0\n" +
                 "      R$attr: 1,1\n" +
                 "      R$mipmap: 1,1\n" +
                 "        int ic_launcher: 0,0\n" +
                 "        int ic_launcher_round: 0,0\n" +
                 "      R: 1,1\n" +
                 "  java: 0,4\n" +
                 "    lang: 0,2\n" +
                 "      Object: 0,1\n" +
                 "      Boolean: 0,1\n" +
                 "      System: 0,0\n" +
                 "        java.io.PrintStream err: 0,0\n" +
                 "        java.io.PrintStream out: 0,0\n" +
                 "    io: 0,2\n" +
                 "      PrintStream: 0,2\n" +
                 "  android: 0,2\n" +
                 "    app: 0,2\n" +
                 "      Activity: 0,2\n", sb.toString());
  }

  @Test
  public void definedOnlyReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
    DexPackageNode packageTreeNode = new PackageTreeCreator(null, false).constructPackageTree(dexFile);
    DexViewFilters options = new DexViewFilters();
    options.setShowFields(true);
    options.setShowMethods(true);
    options.setShowReferencedNodes(false);
    FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
    assertEquals("root: 27,33\n" +
                 "  com: 27,27\n" +
                 "    example: 27,27\n" +
                 "      MyAbstractClas: 6,6\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "        com.example.MyAbstractClas getInstance(): 1,1\n" +
                 "        void privateMethod(): 1,1\n" +
                 "        void publicMethod(): 1,1\n" +
                 "      MyAbstractClas$1: 3,3\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "      MyClass$NonStaticInnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      BuildConfig: 2,2\n" +
                 "        void <clinit>(): 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        java.lang.String APPLICATION_ID: 0,0\n" +
                 "        java.lang.String BUILD_TYPE: 0,0\n" +
                 "        boolean DEBUG: 0,0\n" +
                 "        java.lang.String FLAVOR: 0,0\n" +
                 "        int VERSION_CODE: 0,0\n" +
                 "        java.lang.String VERSION_NAME: 0,0\n" +
                 "      MyClass$StaticClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyClass method(): 1,1\n" +
                 "      MyClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void method(): 1,1\n" +
                 "        com.example.MyClass anon: 0,0\n" +
                 "        com.example.MyAbstractClas initializedField: 0,0\n" +
                 "        int privateIntField: 0,0\n" +
                 "        java.lang.String privateString: 0,0\n" +
                 "        int publicIntField: 0,0\n" +
                 "        java.lang.String publicStringField: 0,0\n" +
                 "      MyClass$StaticClass$InnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass$StaticClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "        com.example.MyClass$StaticClass this$0: 0,0\n" +
                 "      MainActivity: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void onCreate(android.os.Bundle): 1,1\n" +
                 "      R$string: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int app_name: 0,0\n" +
                 "      MyClass$1: 1,1\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      R$color: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int colorAccent: 0,0\n" +
                 "        int colorPrimary: 0,0\n" +
                 "        int colorPrimaryDark: 0,0\n" +
                 "      R$attr: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      R$mipmap: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        int ic_launcher: 0,0\n" +
                 "        int ic_launcher_round: 0,0\n" +
                 "      R: 1,1\n" +
                 "        void <init>(): 1,1\n", sb.toString());
  }

  @Test
  public void methodsOnlyReferenceTree() throws IOException {
    DexBackedDexFile dexFile = getTestDexFile("Test2.dex");
    DexPackageNode packageTreeNode = new PackageTreeCreator(null, false).constructPackageTree(dexFile);
    DexViewFilters options = new DexViewFilters();
    options.setShowFields(false);
    options.setShowMethods(true);
    options.setShowReferencedNodes(true);
    FilteredTreeModel filteredTreeModel = new FilteredTreeModel<>(packageTreeNode, options);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
    assertEquals("root: 27,33\n" +
                 "  com: 27,27\n" +
                 "    example: 27,27\n" +
                 "      MyAbstractClas: 6,6\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "        com.example.MyAbstractClas getInstance(): 1,1\n" +
                 "        void privateMethod(): 1,1\n" +
                 "        void publicMethod(): 1,1\n" +
                 "      MyAbstractClas$1: 3,3\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 1,1\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 1,1\n" +
                 "      MyClass$NonStaticInnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "      BuildConfig: 2,2\n" +
                 "        void <clinit>(): 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      MyClass$StaticClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyClass method(): 1,1\n" +
                 "      MyClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void method(): 1,1\n" +
                 "      MyClass$StaticClass$InnerClass: 2,2\n" +
                 "        void <init>(com.example.MyClass$StaticClass): 1,1\n" +
                 "        com.example.MyClass methodMethod(): 1,1\n" +
                 "      MainActivity: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void onCreate(android.os.Bundle): 1,1\n" +
                 "      R$string: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      MyClass$1: 1,1\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "      R$color: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      R$attr: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      R$mipmap: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "      R: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "  java: 0,4\n" +
                 "    lang: 0,2\n" +
                 "      Object: 0,1\n" +
                 "        void <init>(): 0,1\n" +
                 "      Boolean: 0,1\n" +
                 "        boolean parseBoolean(java.lang.String): 0,1\n" +
                 "      System: 0,0\n" +
                 "    io: 0,2\n" +
                 "      PrintStream: 0,2\n" +
                 "        void println(int): 0,1\n" +
                 "        void println(java.lang.String): 0,1\n" +
                 "  android: 0,2\n" +
                 "    app: 0,2\n" +
                 "      Activity: 0,2\n" +
                 "        void <init>(): 0,1\n" +
                 "        void onCreate(android.os.Bundle): 0,1\n", sb.toString());
  }

  @Test
  public void removedNodesReferenceTree() throws IOException, ParseException {
    DexBackedDexFile dexFile = getTestDexFile("Test3.dex");

    Path mapPath = Paths.get(AndroidTestBase.getTestDataPath(), "apk/Test3_mapping.txt");
    ProguardMap map = new ProguardMap();
    map.readFromReader(Files.newBufferedReader(mapPath));

    mapPath = Paths.get(AndroidTestBase.getTestDataPath(), "apk/Test3_seeds.txt");
    ProguardSeedsMap seedsMap = ProguardSeedsMap.parse(Files.newBufferedReader(mapPath));

    mapPath = Paths.get(AndroidTestBase.getTestDataPath(), "apk/Test3_usage.txt");
    ProguardUsagesMap usageMap = ProguardUsagesMap.parse(Files.newBufferedReader(mapPath));


    ProguardMappings proguardMappings = new ProguardMappings(map, seedsMap, usageMap);
    DexPackageNode packageTreeNode = new PackageTreeCreator(proguardMappings, true).constructPackageTree(dexFile);
    DexViewFilters options = new DexViewFilters();
    options.setShowFields(true);
    options.setShowMethods(true);
    options.setShowReferencedNodes(true);
    options.setShowRemovedNodes(false);
    FilteredTreeModel filteredTreeModel = new FilteredTreeModel(packageTreeNode, options);

    StringBuffer sb = new StringBuffer(100);
    dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
    assertEquals("root: 9,14\n" +
                 "  com: 9,9\n" +
                 "    example: 9,9\n" +
                 "      MyAbstractClas: 3,3\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyAbstractClas getInstance(): 1,1\n" +
                 "        void publicMethod(): 1,1\n" +
                 "      MyClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void method(): 1,1\n" +
                 "        int publicIntField: 0,0\n" +
                 "        java.lang.String publicStringField: 0,0\n" +
                 "        com.example.MyClass anon: 0,0\n" +
                 "        int privateIntField: 0,0\n" +
                 "        com.example.MyAbstractClas initializedField: 0,0\n" +
                 "      MainActivity: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void onCreate(android.os.Bundle): 1,1\n" +
                 "      MyClass$1: 1,1\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      MyAbstractClas$1: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "  java: 0,3\n" +
                 "    io: 0,2\n" +
                 "      PrintStream: 0,2\n" +
                 "        void println(int): 0,1\n" +
                 "        void println(java.lang.String): 0,1\n" +
                 "    lang: 0,1\n" +
                 "      Object: 0,1\n" +
                 "        void <init>(): 0,1\n" +
                 "      System: 0,0\n" +
                 "        java.io.PrintStream out: 0,0\n" +
                 "  android: 0,2\n" +
                 "    app: 0,2\n" +
                 "      Activity: 0,2\n" +
                 "        void <init>(): 0,1\n" +
                 "        void onCreate(android.os.Bundle): 0,1\n", sb.toString());

    options.setShowRemovedNodes(true);
    sb.setLength(0);
    dumpTree(sb, filteredTreeModel, packageTreeNode, 0);
    assertEquals("root: 9,14\n" +
                 "  com: 9,9\n" +
                 "    example: 9,9\n" +
                 "      MyAbstractClas: 3,3\n" +
                 "        void <init>(): 1,1\n" +
                 "        com.example.MyAbstractClas getInstance(): 1,1\n" +
                 "        void publicMethod(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 0,0\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 0,0\n" +
                 "        void privateMethod(): 0,0\n" +
                 "      MyClass: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void method(): 1,1\n" +
                 "        int publicIntField: 0,0\n" +
                 "        java.lang.String publicStringField: 0,0\n" +
                 "        com.example.MyClass anon: 0,0\n" +
                 "        int privateIntField: 0,0\n" +
                 "        com.example.MyAbstractClas initializedField: 0,0\n" +
                 "        privateString: 0,0\n" +
                 "      MainActivity: 2,2\n" +
                 "        void <init>(): 1,1\n" +
                 "        void onCreate(android.os.Bundle): 1,1\n" +
                 "      MyClass$1: 1,1\n" +
                 "        void <init>(com.example.MyClass): 1,1\n" +
                 "        com.example.MyClass this$0: 0,0\n" +
                 "      MyAbstractClas$1: 1,1\n" +
                 "        void <init>(): 1,1\n" +
                 "        void abstractMethod(int,java.lang.String): 0,0\n" +
                 "        com.example.MyAbstractClas anotherAbstract(com.example.MyClass): 0,0\n" +
                 "      MyClass$NonStaticInnerClass: 0,0\n" +
                 "      R$color: 0,0\n" +
                 "      R$mipmap: 0,0\n" +
                 "      BuildConfig: 0,0\n" +
                 "      R$attr: 0,0\n" +
                 "      R$string: 0,0\n" +
                 "      MyClass$StaticClass: 0,0\n" +
                 "      R: 0,0\n" +
                 "      MyClass$StaticClass$InnerClass: 0,0\n" +
                 "  java: 0,3\n" +
                 "    io: 0,2\n" +
                 "      PrintStream: 0,2\n" +
                 "        void println(int): 0,1\n" +
                 "        void println(java.lang.String): 0,1\n" +
                 "    lang: 0,1\n" +
                 "      Object: 0,1\n" +
                 "        void <init>(): 0,1\n" +
                 "      System: 0,0\n" +
                 "        java.io.PrintStream out: 0,0\n" +
                 "  android: 0,2\n" +
                 "    app: 0,2\n" +
                 "      Activity: 0,2\n" +
                 "        void <init>(): 0,1\n" +
                 "        void onCreate(android.os.Bundle): 0,1\n", sb.toString());
  }



  @NotNull
  private static DexBackedDexFile getTestDexFile(String filename) throws IOException {
    Path dexPath = Paths.get(AndroidTestBase.getTestDataPath(), "apk/" + filename);
    return getDexFile(Files.readAllBytes(dexPath));
  }

  private static void dumpTree(StringBuffer sb, @NotNull TreeModel model, DexElementNode node, int depth) {
    sb.append(StringUtil.repeatSymbol(' ', depth * 2));
    sb.append(node.getName());
    sb.append(": ");
    sb.append(node.getDefinedMethodsCount());
    sb.append(',');
    sb.append(node.getMethodRefCount());
    sb.append('\n');

    int count = model.getChildCount(node);
    for (int i = 0; i < count; i++) {
      dumpTree(sb, model, (DexElementNode)model.getChild(node, i), depth + 1);
    }
  }
}
