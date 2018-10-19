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
package com.android.tools.idea.databinding;

import static com.android.SdkConstants.ANDROIDX_DATA_BINDING_LIB_ARTIFACT;
import static com.android.SdkConstants.DATA_BINDING_LIB_ARTIFACT;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X;

import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.blame.Message;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 */
public class GeneratedCodeMatchTest extends AndroidGradleTestCase {
  @NotNull
  private ClassReader findViewDataBindingClass() throws IOException {
    File classes = null;

    AndroidModuleModel model = AndroidModuleModel.get(myAndroidFacet);
    for (AndroidLibrary lib : model.getMainArtifact().getDependencies().getLibraries()) {
      if (lib.getName().startsWith(myDataBindingLibArtifact)) {
        classes = lib.getJarFile();
      }
    }
    assert classes != null;
    assertTrue(classes.exists());

    try (JarFile classesJar = new JarFile(classes, true)) {
      ZipEntry entry = classesJar.getEntry(myDataBindingBaseBindingClass);
      assertNotNull(entry);
      return new ClassReader(classesJar.getInputStream(entry));
    }
  }

  @NotNull
  private DataBindingMode myMode;
  @NotNull
  private String myProjectName;
  @NotNull
  private String myDataBindingComponentClassName;
  @NotNull
  private String myDataBindingLibArtifact;
  @NotNull
  private String myDataBindingBaseBindingClass;

  private void init(DataBindingMode mode) {
    myMode = mode;
    myProjectName = mode == DataBindingMode.ANDROIDX ? PROJECT_WITH_DATA_BINDING_ANDROID_X : PROJECT_WITH_DATA_BINDING;
    myDataBindingComponentClassName = mode.dataBindingComponent.replace(".", "/");
    myDataBindingLibArtifact = mode == DataBindingMode.ANDROIDX ? ANDROIDX_DATA_BINDING_LIB_ARTIFACT : DATA_BINDING_LIB_ARTIFACT;
    myDataBindingBaseBindingClass = mode.viewDataBinding.replace(".", "/") + ".class";
  }

  public void testGeneratedCodeMatchAndroidX() throws Exception {
    init(DataBindingMode.ANDROIDX);
    verifyGeneratedCode();
  }

  public void testGeneratedCodeMatch() throws Exception {
    init(DataBindingMode.SUPPORT);
    verifyGeneratedCode();
  }

  private void verifyGeneratedCode() throws Exception {
    loadProject(myProjectName);
    // temporary fix until test model can detect dependencies properly
    GradleInvocationResult assembleDebug = invokeGradleTasks(getProject(), "assembleDebug");
    assertTrue(StringUtil.join(assembleDebug.getCompilerMessages(Message.Kind.ERROR), "\n"), assembleDebug.isBuildSuccessful());

    GradleSyncState syncState = GradleSyncState.getInstance(getProject());
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertEquals(ModuleDataBinding.getInstance(myAndroidFacet).getDataBindingMode(), myMode);


    // trigger initialization
    ResourceRepositoryManager.getModuleResources(myAndroidFacet);

    File classesOut = new File(getProject().getBasePath(), "/app/build/intermediates/javac//debug/compileDebugJavaWithJavac/classes");
    //noinspection unchecked
    Collection<File> classes = FileUtils.listFiles(classesOut, new String[]{"class"}, true);
    assertTrue("if we cannot find any class, something is wrong with the test", classes.size() > 0);
    ClassReader viewDataBindingClass = findViewDataBindingClass();
    Set<String> baseClassInfo = collectDescriptionSet(viewDataBindingClass, new HashSet<>());

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    Set<String> missingClasses = new HashSet<>();

    Map<String, ClassReader> klassMap = classes.stream().map((file) -> {
      try {
        return new ClassReader(FileUtils.readFileToByteArray(file));
      }
      catch (IOException e) {
        e.printStackTrace();
        fail(e.getMessage());
      }
      return null;
    }).filter(kls -> kls != null)
      .collect(Collectors.toMap(
        kls -> kls.getClassName(),
        kls -> kls
      ));

    Map<ClassReader, ClassReader> superClassLookup = klassMap.values().stream()
      .filter(kls -> klassMap.containsKey(kls.getSuperName()))
      .collect(Collectors.toMap(
        kls -> kls,
        kls -> klassMap.get(kls.getSuperName())
      ));

    int verifiedClassCount = 0;
    for (ClassReader classReader : klassMap.values()) {
      if (!shouldVerify(viewDataBindingClass, classReader)) {
        continue;
      }
      verifiedClassCount++;
      String className = classReader.getClassName();
      PsiClass psiClass = javaPsiFacade
        .findClass(className.replace("/", "."), myAndroidFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (psiClass == null) {
        missingClasses.add(className);
        continue;
      }
      assertNotNull(psiClass);
      String asmInfo = collectDescriptions(classReader, baseClassInfo);
      String psiInfo = collectDescriptions(psiClass);
      assertEquals(className, asmInfo, psiInfo);
    }
    assertTrue("test sanity, should be able to find some data binding generated classes", verifiedClassCount > 3);
    assertEquals("These classes are missing", "", StringUtil.join(missingClasses, "\n"));
  }

  private boolean shouldVerify(ClassReader viewDataBindingClass, ClassReader classReader) {
    return classReader != null &&
           (viewDataBindingClass.getClassName().equals(classReader.getSuperName()) ||
            myDataBindingComponentClassName.equals(classReader.getClassName()));
  }

  @NotNull
  private static TreeSet<String> collectDescriptionSet(@NotNull ClassReader classReader, @NotNull final Set<String> exclude) {
    final TreeSet<String> set = new TreeSet<>();
    classReader.accept(new ClassVisitor(Opcodes.ASM5) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        List<String> interfaceList = new ArrayList<>();
        Collections.addAll(interfaceList, interfaces);
        Collections.sort(interfaceList);
        set.add(name + " : " + superName + " -> " + StringUtil.join(interfaceList, ", "));
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        String info = modifierDescription(access) + " " + name + " : " + desc;
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !name.startsWith("<") && !exclude.contains(info)) {
          set.add(info);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
      }

      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        String info = modifierDescription(access) + " " + name + " : " + desc;
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !exclude.contains(info)) {
          set.add(info);
        }
        return super.visitField(access, name, desc, signature, value);
      }
    }, 0);
    return set;
  }

  @NotNull
  private static String collectDescriptions(@NotNull ClassReader classReader, @NotNull Set<String> exclude) {
    return StringUtil.join(collectDescriptionSet(classReader, exclude), "\n");
  }

  @NotNull
  private static String collectDescriptions(@NotNull PsiClass psiClass) {
    final TreeSet<String> set = new TreeSet<>();
    PsiClassType[] superTypes = psiClass.getSuperTypes();
    String superType = superTypes.length == 0 ? "java/lang/Object" : superTypes[0].getCanonicalText().replace('.', '/');
    List<String> sortedInterfaces = Arrays.stream(psiClass.getInterfaces()).map((klass) -> klass.getQualifiedName().replace('.', '/'))
      .sorted().collect(Collectors.toList());
    set.add(psiClass.getQualifiedName().replace('.', '/') + " : " + superType + " -> " + StringUtil.join(sortedInterfaces, ", "));
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
        set.add(modifierDescription(method) + " " + method.getName() + " : " + createMethodDescription(method));
      }
    }
    for (PsiField field : psiClass.getFields()) {
      if (field.getModifierList() != null && field.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
        set.add(modifierDescription(field) + " " + field.getName() + " : " + createFieldDescription(field));
      }
    }
    return StringUtil.join(set, "\n");
  }

  @NotNull
  private static String createFieldDescription(@NotNull PsiField field) {
    return typeToAsm(field.getType());
  }

  @NotNull
  private static String createMethodDescription(@NotNull PsiMethod psiMethod) {
    StringBuilder res = new StringBuilder();
    PsiType returnType = psiMethod.getReturnType();
    assertNotNull(returnType);
    res.append("(");
    for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
      res.append(typeToAsm(param.getType()));
    }
    res.append(")").append(typeToAsm(returnType));
    return res.toString();
  }

  @NotNull
  private static String typeToAsm(@NotNull PsiType psiType) {
    String conv = BASIC_ASM_TYPES.get(psiType);
    if (conv != null) {
      return conv;
    }
    if (psiType instanceof PsiArrayType) {
      PsiArrayType arrType = (PsiArrayType)psiType;
      return "[" + typeToAsm(arrType.getComponentType());
    }
    else {
      return "L" + psiType.getCanonicalText().replace(".", "/") + ";";
    }
  }

  @NotNull
  protected static String modifierDescription(@NotNull final PsiModifierListOwner owner) {
    return Joiner.on(" ").join(Iterables.filter(IMPORTANT_MODIFIERS.keySet(), new Predicate<String>() {
      @Override
      public boolean apply(String modifier) {
        //noinspection ConstantConditions
        return owner.getModifierList().hasModifierProperty(modifier);
      }
    }));
  }

  @NotNull
  protected static String modifierDescription(final int access) {
    return Joiner.on(" ").join(Iterables.filter(IMPORTANT_MODIFIERS.keySet(), new Predicate<String>() {
      @Override
      public boolean apply(String modifier) {
        //noinspection ConstantConditions
        return (IMPORTANT_MODIFIERS.get(modifier).intValue() & access) != 0;
      }
    }));
  }

  static Map<String, Integer> IMPORTANT_MODIFIERS = new ContainerUtil.ImmutableMapBuilder<String, Integer>()
    .put(PsiModifier.PUBLIC, Opcodes.ACC_PUBLIC)
    .put(PsiModifier.STATIC, Opcodes.ACC_STATIC)
    .build();

  static Map<PsiType, String> BASIC_ASM_TYPES =
    new ContainerUtil.ImmutableMapBuilder<PsiType, String>()
      .put(PsiType.VOID, "V")
      .put(PsiType.BOOLEAN, "Z")
      .put(PsiType.CHAR, "C")
      .put(PsiType.BYTE, "B")
      .put(PsiType.SHORT, "S")
      .put(PsiType.INT, "I")
      .put(PsiType.FLOAT, "F")
      .put(PsiType.LONG, "J")
      .put(PsiType.DOUBLE, "d").build();
}
