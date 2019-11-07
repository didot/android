package org.jetbrains.android.dom.inspections

import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.VIEW_TAG
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.imports.AndroidMavenImportFix
import com.android.tools.idea.imports.MavenClassRegistry
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls
import java.util.ArrayList

/**
 * Looks for unknown tags, such as unresolved custom layouts
 *
 * TODO: Check other resource types that allows this syntax (check the inflaters)
 * TODO: Enforce built-ins (non custom views) as well. E.g. if you have <NoSuchView/> it should light up red.
 */
class AndroidUnresolvableTagInspection : LocalInspectionTool() {

  @Nls
  override fun getGroupDisplayName(): String = AndroidBundle.message("android.inspections.group.name")

  @Nls
  override fun getDisplayName(): String = AndroidBundle.message("android.inspections.unresolvable.tag")

  override fun getShortName(): String = "AndroidUnresolvableTag"

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is XmlFile) {
      return ProblemDescriptor.EMPTY_ARRAY
    }
    val facet = AndroidFacet.getInstance(file) ?: return ProblemDescriptor.EMPTY_ARRAY
    if (isRelevantFile(facet, file)) {
      val visitor = MyVisitor(manager, isOnTheFly)
      file.accept(visitor)
      return visitor.myResult.toTypedArray()
    }
    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun isRelevantFile(facet: AndroidFacet, file: XmlFile): Boolean {
    val resourceType = ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(file)
    return if (resourceType != null) {
      resourceType == ResourceFolderType.LAYOUT || resourceType == ResourceFolderType.MENU
    }
    else false
  }

  private class MyVisitor(private val myInspectionManager: InspectionManager,
                          private val myOnTheFly: Boolean) : XmlRecursiveElementVisitor() {
    internal val myResult: MutableList<ProblemDescriptor> = ArrayList()

    override fun visitXmlTag(tag: XmlTag) {
      super.visitXmlTag(tag)

      if (tag.namespace.isNotEmpty()) {
        return
      }
      var className = tag.name
      if (className.indexOf('.') == -1) {
        if (className == VIEW_TAG) {
          className = tag.getAttributeValue(ATTR_CLASS) ?: return
        } else {
          // Not a custom view and not <view class="fqn"/>
          return
        }
      }

      // Make sure the class exists; check only the last reference; that's the class name tag (the rest are for the package segments)
      val reference = tag.references.lastOrNull() ?: return
      if (reference.resolve() == null) {
        var fixes = LocalQuickFix.EMPTY_ARRAY
        // normal position of the tag name, but unusual spaces can mess with this...
        var range: PsiElement = tag.firstChild.nextSibling
        // ...so search properly:
        var curr = tag.firstChild
        while (curr != null) {
          if (curr is XmlToken && curr.tokenType == XmlTokenType.XML_NAME) {
            range = curr
            break
          }
          else {
            curr = curr.nextSibling
          }
        }
        val name = className.substring(className.lastIndexOf('.') + 1)
        val imported = MavenClassRegistry.findImport(name)
        if (imported != null && (className == imported || className == AndroidxNameUtils.getNewName(className))) {
          // Have fix
          var artifact = MavenClassRegistry.findArtifact(name) ?: "library"
          if (tag.project.isAndroidx()) {
            artifact = AndroidxNameUtils.getCoordinateMapping(artifact)
          }
          fixes = arrayOf<LocalQuickFix>(AndroidMavenImportFix(className, artifact))
        }
        myResult.add(myInspectionManager.createProblemDescriptor(range, AndroidBundle.message(
          "element.cannot.resolve", className), myOnTheFly, fixes,
                                                                 ProblemHighlightType.LIKE_UNKNOWN_SYMBOL))
      }
    }
  }
}
