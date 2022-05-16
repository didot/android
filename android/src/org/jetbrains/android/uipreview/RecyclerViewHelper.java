/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_V7;
import static com.android.SdkConstants.CLASS_RECYCLER_VIEW_VIEW_HOLDER;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.jetbrains.org.objectweb.asm.Opcodes.ALOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.ARETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.ASTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.BIPUSH;
import static org.jetbrains.org.objectweb.asm.Opcodes.CHECKCAST;
import static org.jetbrains.org.objectweb.asm.Opcodes.DUP;
import static org.jetbrains.org.objectweb.asm.Opcodes.GETFIELD;
import static org.jetbrains.org.objectweb.asm.Opcodes.GOTO;
import static org.jetbrains.org.objectweb.asm.Opcodes.ICONST_0;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFEQ;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFLE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IFNE;
import static org.jetbrains.org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.jetbrains.org.objectweb.asm.Opcodes.ILOAD;
import static org.jetbrains.org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.jetbrains.org.objectweb.asm.Opcodes.IRETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.ISTORE;
import static org.jetbrains.org.objectweb.asm.Opcodes.NEW;
import static org.jetbrains.org.objectweb.asm.Opcodes.POP;
import static org.jetbrains.org.objectweb.asm.Opcodes.PUTFIELD;
import static org.jetbrains.org.objectweb.asm.Opcodes.RETURN;
import static org.jetbrains.org.objectweb.asm.Opcodes.V1_6;
import static org.jetbrains.org.objectweb.asm.Opcodes.V1_7;

import com.android.support.AndroidxName;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.FieldVisitor;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

/**
 * Some constants that are needed to render the RecyclerView correctly.
 */
@SuppressWarnings("SpellCheckingInspection")
public class RecyclerViewHelper {
  private static final String SUPPORT_PACKAGE_NAME = "com.android.layoutlib.bridge.android.support";
  public static final String CN_SUPPORT_CUSTOM_ADAPTER = SUPPORT_PACKAGE_NAME + ".Adapter";
  private static final String CN_SUPPORT_CUSTOM_VIEW_HOLDER = SUPPORT_PACKAGE_NAME + ".Adapter$ViewHolder";

  private static final String ANDROIDX_PACKAGE_NAME = "com.android.layoutlib.bridge.android.androidx";
  public static final String CN_ANDROIDX_CUSTOM_ADAPTER = ANDROIDX_PACKAGE_NAME + ".Adapter";
  private static final String CN_ANDROIDX_CUSTOM_VIEW_HOLDER = ANDROIDX_PACKAGE_NAME + ".Adapter$ViewHolder";

  public enum AdapterNamespace {
    ANDROIDX,
    SUPPORT
  }

  // Lazily initialized.
  private static final Map<String, byte[]> ourClassesCache = new HashMap<>();

  /**
   * Converts the given {@link AndroidxName} to the VM binary class name representation using slashes.
   *
   * @param nameFunction One of either {@link AndroidxName#newName()} or One of either {@link AndroidxName#oldName()} depending on whether
   *                     the androidx or support namespaces repectively need to be used.
   * @param androidxName The {@link AndroidxName} to convert.
   */
  @NotNull
  private static String nameToBinaryRepresentation(@NotNull Function<AndroidxName, String> nameFunction, @NotNull AndroidxName androidxName) {
    return nameFunction.apply(androidxName).replaceAll("\\.", "/");
  }

  /**
   * If the name matches any of the adapter helper classes (adapter or view holder), it will return the class definition for that helper
   * class. This method will return null if the name does not match any of the helper classes.
   */
  public static byte[] getAdapterHelperClass(@NotNull String name) {
    byte[] clazz = null;
    switch (name) {
      case CN_ANDROIDX_CUSTOM_ADAPTER:
        clazz = getAdapterClass(CN_ANDROIDX_CUSTOM_ADAPTER, AdapterNamespace.ANDROIDX);
        break;
      case CN_ANDROIDX_CUSTOM_VIEW_HOLDER:
        clazz = getViewHolder(CN_ANDROIDX_CUSTOM_ADAPTER, AdapterNamespace.ANDROIDX);
        break;
      case CN_SUPPORT_CUSTOM_ADAPTER:
        clazz = getAdapterClass(CN_SUPPORT_CUSTOM_ADAPTER, AdapterNamespace.SUPPORT);
        break;
      case CN_SUPPORT_CUSTOM_VIEW_HOLDER:
        clazz = getViewHolder(CN_SUPPORT_CUSTOM_ADAPTER, AdapterNamespace.SUPPORT);
        break;
    }
    return clazz;
  }

  /**
   * Generates the custom RecyclerView adapter that allows using sample data from the Layout Editor.
   *
   * @param customAdapterName the name of the custom adapter. Different combinations of recyclerViewName/viewHolderName/adapterName must
   *                          have a different customAdapterName so they do not collide. Usually there will be one for androidx and one for
   *                          the old support library.
   * @param adapterNamespace defined the namespaced that must be used for the generated adapter class (either androidx or support).
   * @return the byte array containing the new class.
   */
  private static byte[] getAdapterClass(@NotNull final String customAdapterName,
                                        @NotNull RecyclerViewHelper.AdapterNamespace adapterNamespace) {
    return ourClassesCache.computeIfAbsent(customAdapterName, (adapterClassName) -> {
      Function<AndroidxName, String> nameFunction =
        adapterNamespace == AdapterNamespace.ANDROIDX ? AndroidxName::newName : AndroidxName::oldName;
      String recyclerViewName = nameToBinaryRepresentation(nameFunction, CLASS_RECYCLER_VIEW_V7);
      String viewHolderName = nameToBinaryRepresentation(nameFunction, CLASS_RECYCLER_VIEW_VIEW_HOLDER);
      String adapterName = nameToBinaryRepresentation(nameFunction, CLASS_RECYCLER_VIEW_ADAPTER);

      return getAdapterClassDump(adapterClassName.replaceAll("\\.", "/"),
                                 recyclerViewName,
                                 viewHolderName,
                                 adapterName);
    });
  }

  /**
   * Generates the custom RecyclerView adapter ViewHolder that allows using sample data from the Layout Editor.
   *
   * @param customAdapterName the name of the custom adapter. Different combinations of recyclerViewName/viewHolderName/adapterName must
   *                          have a different customAdapterName so they do not collide. Usually there will be one for androidx and one for
   *                          the old support library.
   * @param adapterNamespace defined the namespaced that must be used for the generated adapter class (either androidx or support).
   * @return the byte array containing the new class.
   */
  private static byte[] getViewHolder(@NotNull final String customAdapterName,
                                      @NotNull RecyclerViewHelper.AdapterNamespace adapterNamespace) {
    return ourClassesCache.computeIfAbsent(customAdapterName + "$ViewHolder", (ignore) -> {
      Function<AndroidxName, String> nameFunction =
        adapterNamespace == AdapterNamespace.ANDROIDX ? AndroidxName::newName : AndroidxName::oldName;
      String recyclerViewName = nameToBinaryRepresentation(nameFunction, CLASS_RECYCLER_VIEW_V7);
      String viewHolderName = nameToBinaryRepresentation(nameFunction, CLASS_RECYCLER_VIEW_VIEW_HOLDER);
      String adapterName = nameToBinaryRepresentation(nameFunction, CLASS_RECYCLER_VIEW_ADAPTER);

      return getViewHolderDump(customAdapterName.replaceAll("\\.", "/"),
                               recyclerViewName,
                               viewHolderName,
                               adapterName);
    });
  }

  // See comment at the end of the file for how this method was generated.
  @SuppressWarnings("unused")  // Generated code
  private static byte[] getAdapterClassDump(@NotNull String customAdapterName,
                                            @NotNull String recyclerViewName,
                                            @NotNull String viewHolderName,
                                            @NotNull String adapterName) {
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    String signature = String.format("L%1$s<L%2$s;>;",
                                     adapterName,
                                     viewHolderName);
    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, customAdapterName, signature, adapterName, null);

    cw.visitInnerClass(customAdapterName + "$ViewHolder", customAdapterName, "ViewHolder", ACC_PRIVATE + ACC_STATIC);

    cw.visitInnerClass(viewHolderName, recyclerViewName, "ViewHolder", ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

    cw.visitInnerClass(adapterName, recyclerViewName, "Adapter", ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

    {
      fv = cw.visitField(ACC_PRIVATE, "mItemCount", "I", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE, "mId", "I", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, adapterName, "<init>", "()V", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitIntInsn(BIPUSH, 10);
      mv.visitFieldInsn(PUTFIELD, customAdapterName, "mItemCount", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }
    {
      String desc = String.format("(Landroid/view/ViewGroup;I)L%1$s;", viewHolderName);
      mv = cw.visitMethod(ACC_PUBLIC, "onCreateViewHolder", desc, null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, customAdapterName, "mId", "I");
      Label l0 = new Label();
      mv.visitJumpInsn(IFLE, l0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/ViewGroup", "getContext", "()Landroid/content/Context;", false);
      mv.visitMethodInsn(INVOKESTATIC, "android/view/LayoutInflater", "from", "(Landroid/content/Context;)Landroid/view/LayoutInflater;", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, customAdapterName, "mId", "I");
      mv.visitVarInsn(ALOAD, 1);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/LayoutInflater", "inflate", "(ILandroid/view/ViewGroup;Z)Landroid/view/View;", false);
      mv.visitVarInsn(ASTORE, 3);
      Label l1 = new Label();
      mv.visitJumpInsn(GOTO, l1);
      mv.visitLabel(l0);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitTypeInsn(NEW, "android/widget/TextView");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/ViewGroup", "getContext", "()Landroid/content/Context;", false);
      mv.visitMethodInsn(INVOKESPECIAL, "android/widget/TextView", "<init>", "(Landroid/content/Context;)V", false);
      mv.visitVarInsn(ASTORE, 3);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {"android/view/View"}, 0, null);
      mv.visitTypeInsn(NEW, customAdapterName + "$ViewHolder");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(INVOKESPECIAL, customAdapterName + "$ViewHolder", "<init>", "(Landroid/view/View;)V", false);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(4, 4);
      mv.visitEnd();
    }
    {
      String desc = String.format("(L%1$s;I)V", viewHolderName);
      mv = cw.visitMethod(ACC_PUBLIC, "onBindViewHolder", desc, null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(GETFIELD, viewHolderName, "itemView", "Landroid/view/View;");
      mv.visitVarInsn(ASTORE, 3);
      mv.visitTypeInsn(NEW, "java/util/ArrayList");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
      mv.visitVarInsn(ASTORE, 4);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitTypeInsn(NEW, "java/util/LinkedList");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false);
      mv.visitMethodInsn(INVOKESPECIAL, customAdapterName, "findTextViews", "(Landroid/view/View;Ljava/util/ArrayList;Ljava/util/LinkedList;)V", false);
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn("Item ");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 6);
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitFrame(Opcodes.F_FULL, 7, new Object[] {customAdapterName, viewHolderName, Opcodes.INTEGER, "android/view/View", "java/util/ArrayList", "java/lang/String", Opcodes.INTEGER}, 0, new Object[] {});
      mv.visitVarInsn(ILOAD, 6);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
      Label l1 = new Label();
      mv.visitJumpInsn(IF_ICMPGE, l1);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitVarInsn(ILOAD, 6);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "android/widget/TextView");
      mv.visitVarInsn(ALOAD, 5);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "setText", "(Ljava/lang/CharSequence;)V", false);
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn("Sub");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
      mv.visitVarInsn(ALOAD, 5);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitIincInsn(6, 1);
      mv.visitJumpInsn(GOTO, l0);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_CHOP,1, null, 0, null);
      mv.visitInsn(RETURN);
      mv.visitMaxs(5, 7);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "getItemCount", "()I", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, customAdapterName, "mItemCount", "I");
      mv.visitInsn(IRETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "setLayoutId", "(I)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitFieldInsn(PUTFIELD, customAdapterName, "mId", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "setItemCount", "(I)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitFieldInsn(PUTFIELD, customAdapterName, "mItemCount", "I");
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PRIVATE, "findTextViews", "(Landroid/view/View;Ljava/util/ArrayList;Ljava/util/LinkedList;)V", "(Landroid/view/View;Ljava/util/ArrayList<Landroid/widget/TextView;>;Ljava/util/LinkedList<Landroid/view/View;>;)V", null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(INSTANCEOF, "android/widget/TextView");
      Label l0 = new Label();
      mv.visitJumpInsn(IFEQ, l0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(CHECKCAST, "android/widget/TextView");
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/widget/TextView", "getText", "()Ljava/lang/CharSequence;", false);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length", "()I", true);
      Label l1 = new Label();
      mv.visitJumpInsn(IFNE, l1);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(CHECKCAST, "android/widget/TextView");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
      mv.visitInsn(POP);
      mv.visitJumpInsn(GOTO, l1);
      mv.visitLabel(l0);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(INSTANCEOF, "android/view/ViewGroup");
      mv.visitJumpInsn(IFEQ, l1);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 4);
      Label l2 = new Label();
      mv.visitLabel(l2);
      mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {Opcodes.INTEGER}, 0, null);
      mv.visitVarInsn(ILOAD, 4);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(CHECKCAST, "android/view/ViewGroup");
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/ViewGroup", "getChildCount", "()I", false);
      mv.visitJumpInsn(IF_ICMPGE, l1);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(CHECKCAST, "android/view/ViewGroup");
      mv.visitVarInsn(ILOAD, 4);
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/view/ViewGroup", "getChildAt", "(I)Landroid/view/View;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedList", "add", "(Ljava/lang/Object;)Z", false);
      mv.visitInsn(POP);
      mv.visitIincInsn(4, 1);
      mv.visitJumpInsn(GOTO, l2);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_CHOP,1, null, 0, null);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedList", "isEmpty", "()Z", false);
      Label l3 = new Label();
      mv.visitJumpInsn(IFNE, l3);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/LinkedList", "remove", "()Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "android/view/View");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(INVOKESPECIAL, customAdapterName, "findTextViews", "(Landroid/view/View;Ljava/util/ArrayList;Ljava/util/LinkedList;)V", false);
      mv.visitLabel(l3);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitInsn(RETURN);
      mv.visitMaxs(4, 5);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }

  // See comment at the end of the file for how this method was generated.
  @SuppressWarnings("unused")  // Generated code.
  private static byte[] getViewHolderDump(@NotNull String customAdapterName,
                                          @NotNull String recyclerViewClass,
                                          @NotNull String viewHolderClass,
                                          @NotNull String viewAdapterClass) {
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_6, ACC_SUPER, customAdapterName + "$ViewHolder", null,
             viewHolderClass, null);

    cw.visitInnerClass(customAdapterName + "$ViewHolder",
                       customAdapterName, "ViewHolder", ACC_PRIVATE + ACC_STATIC);

    cw.visitInnerClass(viewHolderClass, recyclerViewClass, "ViewHolder",
                       ACC_PUBLIC + ACC_STATIC + ACC_ABSTRACT);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Landroid/view/View;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(INVOKESPECIAL, viewHolderClass, "<init>", "(Landroid/view/View;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }


  // The above dump methods were obtained by compiling the following class and running ASMifier on both Adapter and Adapter$ViewHolder.
  // $ echo com.android.layoutlib.bridge.android.support.Adapter com.android.layoutlib.bridge.android.support.Adapter\$ViewHolder \
  //       | xargs -n 1 java -classpath asm-debug-all-5.0.2.jar:. org.objectweb.asm.util.ASMifier
  //
  //package com.android.layoutlib.bridge.android.support;
  //
  //import android.support.v7.widget.RecyclerView;
  //import android.view.LayoutInflater;
  //import android.view.View;
  //import android.view.ViewGroup;
  //import android.widget.TextView;
  //
  //import java.util.ArrayList;
  //import java.util.LinkedList;
  //
  //public class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  //
  //  private int mItemCount = 10;
  //  private int mId;
  //
  //  @Override
  //  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,
  //                                                    int viewType) {
  //    View view;
  //    if (mId > 0) {
  //      view = LayoutInflater.from(parent.getContext()).inflate(mId, parent, false);
  //    } else {
  //      view = new TextView(parent.getContext());
  //    }
  //    return new ViewHolder(view);
  //  }
  //
  //  @Override
  //  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
  //    View view = holder.itemView;
  //    ArrayList<TextView> textViews = new ArrayList<TextView>();
  //    findTextViews(view, textViews, new LinkedList<View>());
  //    String text = "Item " + position;
  //    for (int i = 0; i < textViews.size(); i++) {
  //      textViews.get(i).setText(text);
  //      text = "Sub" + text;
  //    }
  //  }
  //
  //  @Override
  //  public int getItemCount() {
  //    return mItemCount;
  //  }
  //
  //  public void setLayoutId(int id) {
  //    mId = id;
  //  }
  //
  //  public void setItemCount(int itemCount) {
  //    mItemCount = itemCount;
  //  }
  //
  //  private void findTextViews(View view, ArrayList<TextView> out, LinkedList<View> queue) {
  //    if (view instanceof TextView) {
  //      if (((TextView) view).getText().length() == 0) {
  //        out.add((TextView) view);
  //      }
  //    } else if (view instanceof ViewGroup) {
  //      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
  //        queue.add(((ViewGroup) view).getChildAt(i));
  //      }
  //    }
  //    if (!queue.isEmpty()) {
  //      findTextViews(queue.remove(), out, queue);
  //    }
  //  }
  //
  //  private static class ViewHolder extends RecyclerView.ViewHolder {
  //    public ViewHolder(View itemView) {
  //      super(itemView);
  //    }
  //  }
  //}

}
