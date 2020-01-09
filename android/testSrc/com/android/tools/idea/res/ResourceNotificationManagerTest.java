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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.ResourceNotificationManager.Reason;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceVersion;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class ResourceNotificationManagerTest extends AndroidTestCase {
  public void test() {
    @Language("XML") String xml;

    // Setup sample project: a strings file, and a couple of layout file

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\">\n" +
          "    <!-- My comment -->\n" +
          "    <TextView\n" +
          "        android:layout_width=\"match_parent\"\n" +
          "        android:layout_height=\"match_parent\"\n" +
          "        android:text=\"@string/hello\" />\n" +
          "</FrameLayout>";
    final XmlFile layout1 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout1.xml", xml);
    @SuppressWarnings("ConstantConditions")
    VirtualFile resourceDir = layout1.getParent().getParent().getVirtualFile();
    assertNotNull(resourceDir);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\" />\n";
    final XmlFile layout2 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout2.xml", xml);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<resources>\n" +
          "    <string name=\"hello\">Hello</string>\n" +
          "\n" +
          "    <!-- Base application theme. -->\n" +
          "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.Light.DarkActionBar\">\n" +
          "        <!-- Customize your theme here. -->\n" +
          "        <item name=\"android:colorBackground\">#ff0000</item>\n" +
          "    </style>" +
          "</resources>";
    final XmlFile values1 = (XmlFile)myFixture.addFileToProject("res/values/my_values1.xml", xml);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<resources>\n" +
          "    \n" +
          "</resources>";
    myFixture.addFileToProject("res/values/colors.xml", xml);

    final Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout1.getVirtualFile());
    final ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getProject());

    // Listener 1: Listens for changes in layout 1
    final Ref<Boolean> called1 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = reason -> {
      called1.set(true);
      calledValue1.set(reason);
    };

    // Listener 2: Only listens for general changes in the module
    final Ref<Boolean> called2 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = reason -> {
      called2.set(true);
      calledValue2.set(reason);
    };

    manager.addListener(listener1, myFacet, layout1.getVirtualFile(), configuration1);
    manager.addListener(listener2, myFacet, null, null);

    // Make sure that when we're modifying multiple files, with complicated
    // edits (that trigger full file rescans), we handle that scenario correctly.
    clear(called1, calledValue1, called2, calledValue2);
    // There's actually some special optimizations done via PsiResourceItem#recomputeValue
    // to only mark the resource repository changed if the value has actually been looked
    // up. This allows us to not recompute layout if you're editing some string that
    // hasn't actually been looked up and rendered in a layout. In order to make sure
    // that that optimization doesn't kick in here, we need to look up the value of
    // the resource item first:
    //noinspection ConstantConditions
    assertEquals("#ff0000",
                 configuration1.getResourceResolver()
                   .getStyle("AppTheme", false)
                   .getItem(ResourceNamespace.ANDROID, "colorBackground").getValue());
    AndroidResourceUtil.createValueResource(getProject(), resourceDir, "color2", ResourceType.COLOR, "colors.xml",
                                            Collections.singletonList("values"), "#fa2395");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
    clear(called1, calledValue1, called2, calledValue2);
    @SuppressWarnings("ConstantConditions")
    final XmlTag tag = values1.getDocument().getRootTag().getSubTags()[1].getSubTags()[0];
    assertEquals("item", tag.getName());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> tag.getValue().setEscapedText("@color/color2"));
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // First check: Modify the layout by changing @string/hello to @string/hello_world
    // and verify that our listeners are called.
    ResourceVersion version1 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    addText(layout1, "@string/hello^", "_world");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version2 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertFalse(version1.toString(), version1.equals(version2));

    // Next check: Modify a <string> value definition in a values file
    // and check that those changes are flagged too
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version3 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    addText(values1, "name=\"hello^\"", "_world");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
    ResourceVersion version4 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertFalse(version4.toString(), version3.equals(version4));

    // Next check: Modify content in a comment and verify that no changes are fired
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, "My ^comment", "new ");
    ensureNotCalled(called1, called2);

    // Check that editing text in a layout file has no effect
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, " ^ <TextView", "abc");
    ensureNotCalled(called1, called2);

    // Make sure that's true for replacements too
    replaceText(layout1, "^abc", "abc".length(), "def");
    ensureNotCalled(called1, called2);

    // ...and for deletions
    removeText(layout1, "^def", "def".length());
    ensureNotCalled(called1, called2);

    // Check that editing text in a *values file* -does- have an effect
    // Read the value first to ensure that we trigger it as a read (see comment above for previous
    // resource resolver lookup)
    //noinspection ConstantConditions
    assertEquals("Hello", configuration1.getResourceResolver().findResValue("@string/hello_world", false).getValue());
    addText(values1, "Hello^</string>", " World");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // Check that recreating AppResourceRepository object doesn't affect the ResourceNotificationManager
    clear(called1, calledValue1, called2, calledValue2);
    ResourceRepositoryManager.getInstance(myFacet).resetAllCaches();
    AndroidResourceUtil.createValueResource(getProject(), resourceDir, "color4", ResourceType.COLOR, "colors.xml",
                                            Collections.singletonList("values"), "#ff2300");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // Next check: Mark the lines between <TextView .... /> as comments
    // and verify that our listeners are called.
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version5 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    replaceText(layout1, "^<TextView",  9, "<!--<TextView-->");
    replaceText(layout1, "        ^android:layout_width", 35, "<!--android:layout_width=\"match_parent\"-->");
    replaceText(layout1, "        ^android:layout_height", 36, "<!--android:layout_height=\"match_parent\"-->");
    replaceText(layout1, "^android:text=", 37,"<!--android:text=\"@string/hello_world\" />-->");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version6 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertFalse(version6.toString(), version5.equals(version6));

    // Next check: Un-mark the comments of the lines between <!--<TextView ... />--> (which we just commented in previous check)
    // and verify that our listeners are called.
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version7 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    replaceText(layout1, "^<!--<TextView-->",  15, "<TextView");
    replaceText(layout1, "^<!--android:layout_width=\"match_parent\"-->", 42, "android:layout_width=\"match_parent\"");
    replaceText(layout1, "^<!--android:layout_height=\"match_parent\"-->", 43, "android:layout_height=\"match_parent\"");
    replaceText(layout1, "^<!--android:text=\"@string/hello_world\" />-->", 44, "android:text=\"@string/hello_world\" />");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version8 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertFalse(version8.toString(), version7.equals(version8));

    // Finally check that once we remove the listeners there are no more notifications
    manager.removeListener(listener1, myFacet, layout1.getVirtualFile(), configuration1);
    manager.removeListener(listener2, myFacet, layout2.getVirtualFile(), configuration1);
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, "@string/hello_world^", "2");
    ensureNotCalled(called1, called2);

    // TODO: Check that editing a partial URL doesn't re-render
    // Check module dependency triggers!
    // TODO: Test that remove and replace editing also works as expected
  }

  public void testNotifyOnRename() {

    // Setup sample project: a strings file, and a couple of layout file
    @Language("XML") String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                  "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                  "    android:layout_width=\"match_parent\"\n" +
                                  "    android:layout_height=\"match_parent\">\n" +
                                  "    <!-- My comment -->\n" +
                                  "    <TextView\n" +
                                  "        android:layout_width=\"match_parent\"\n" +
                                  "        android:layout_height=\"match_parent\"\n" +
                                  "        android:text=\"@string/hello\" />\n" +
                                  "</FrameLayout>";
    final XmlFile layout1 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout1.xml", xml);
    VirtualFile resourceDir = layout1.getParent().getParent().getVirtualFile();
    assertNotNull(resourceDir);


    final Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout1.getVirtualFile());
    final ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getProject());

    // Listener 1: Listens for changes in layout 1
    final Ref<Boolean> called1 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = reason -> {
      called1.set(true);
      calledValue1.set(reason);
    };

    // Listener 2: Only listens for general changes in the module
    final Ref<Boolean> called2 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = reason -> {
      called2.set(true);
      calledValue2.set(reason);
    };
    manager.addListener(listener1, myFacet, layout1.getVirtualFile(), configuration1);
    manager.addListener(listener2, myFacet, null, null);
    // New resource renaming does not require the extension anymore.
    String newResourceName = StudioFlags.RESOLVE_USING_REPOS.get() ? "newLayout" : "newLayout.xml";
    ApplicationManager.getApplication()
      .invokeAndWait(() -> new RenameDialog(getProject(), layout1, null, null).performRename(newResourceName));
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
  }

  public void testNotNotifiedOnRenameNonResourceFile() {

    // Setup sample project: a strings file, and a couple of layout file
    @Language("JAVA") String java = "class Hello {}";
    final PsiFile javeFile = myFixture.addFileToProject("src/hello.java", java);
    VirtualFile resourceDir = javeFile.getParent().getParent().getVirtualFile();
    assertNotNull(resourceDir);


    final Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(javeFile.getVirtualFile());
    final ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getProject());

    // Listener 1: Listens for changes in layout 1
    final Ref<Boolean> called1 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = reason -> {
      called1.set(true);
      calledValue1.set(reason);
    };

    // Listener 2: Only listens for general changes in the module
    final Ref<Boolean> called2 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = reason -> {
      called2.set(true);
      calledValue2.set(reason);
    };
    manager.addListener(listener1, myFacet, javeFile.getVirtualFile(), configuration1);
    manager.addListener(listener2, myFacet, null, null);
    ApplicationManager.getApplication()
      .invokeAndWait(() -> new RenameDialog(getProject(), javeFile, null, null).performRename("newFile.java"));
    ensureNotCalled(called1, called2);
  }

  private static void ensureCalled(final Ref<Boolean> called1,
                                   final Ref<Set<Reason>> calledValue1,
                                   final Ref<Boolean> called2,
                                   final Ref<Set<Reason>> calledValue2,
                                   final Reason reason) {
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        assertTrue(called1.get());
        assertEquals(EnumSet.of(reason), calledValue1.get());

        assertTrue(called2.get());
        assertEquals(EnumSet.of(reason), calledValue2.get());
      }
    });
  }

  private static void clear(Ref<Boolean> called1, Ref<Set<Reason>> calledValue1, Ref<Boolean> called2, Ref<Set<Reason>> calledValue2) {
    called1.set(false);
    called2.set(false);
    calledValue1.set(null);
    calledValue2.set(null);
  }

  private static void ensureNotCalled(final Ref<Boolean> called1, final Ref<Boolean> called2) {
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        assertFalse(called1.get());
        assertFalse(called2.get());
      }
    });
  }

  private void addText(@NotNull PsiFile file, final String location, final String insertedText) {
    editText(file, location, 0, insertedText);
  }

  private void removeText(@NotNull PsiFile file, final String location, final int length) {
    editText(file, location, length, null);
  }

  private void replaceText(@NotNull PsiFile file, final String location, final int length, final String replaceText) {
    editText(file, location, length, replaceText);
  }

  private void editText(@NotNull PsiFile file, final String location, final int length, @Nullable final String text) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(file);
    assertNotNull(document);

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null, () -> {
      final String documentText = document.getText();

      int delta = location.indexOf('^');
      assertTrue("Missing ^ describing caret offset in text window " + location, delta != -1);
      String target = location.substring(0, delta) + location.substring(delta + 1);
      int offset = documentText.indexOf(target);
      assertTrue("Could not find " + target + " in " + documentText, offset != -1);

      if (text != null) {
        if (length == 0) {
          document.insertString(offset + delta, text);
        } else {
          document.replaceString(offset + delta, offset + delta + length, text);
        }
      } else {
        document.deleteString(offset + delta, offset + delta + length);
      }
      documentManager.commitDocument(document);
    });
  }
}
