package org.jetbrains.android.dom;

public class AndroidRawResourcesDomTest extends AndroidDomTest {
  public AndroidRawResourcesDomTest() {
    super(true, "dom/raw");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/raw/" + testFileName;
  }

  public void testRootTagCompletion() throws Throwable {
    doTestCompletionVariants("rawRootTagCompletion.xml", "resources");
  }

  public void testRawTagCompletion() throws Throwable {
    toTestCompletion("rawCompletion.xml", "rawCompletionAfter.xml");
  }

  public void testRawFileHighlighting() throws Throwable {
    doTestHighlighting(copyFileToProject("myRawResource.xml"));
  }
}
