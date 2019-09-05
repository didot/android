/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.index

import com.android.SdkConstants
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.TAG_LAYOUT
import com.android.ide.common.resources.stripPrefixFromId
import com.android.resources.ResourceFolderType
import com.android.tools.idea.databinding.index.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.databinding.index.BindingLayoutType.VIEW_BINDING_LAYOUT
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.xml.NanoXmlBuilder
import com.intellij.util.xml.NanoXmlUtil
import org.jetbrains.kotlin.idea.search.projectScope
import java.io.DataInput
import java.io.DataOutput
import java.io.Reader

/**
 * File based index for data binding layout xml files.
 */
class BindingXmlIndex : FileBasedIndexExtension<String, BindingXmlData>() {
  /**
   * An entry into this index, containing information associated with a target layout file.
   */
  data class Entry(val file: VirtualFile, val data: BindingXmlData)

  companion object {
    @JvmField
    val NAME = ID.create<String, BindingXmlData>("BindingXmlIndex")

    private fun getKeyForFile(file: VirtualFile) = FileBasedIndex.getFileId(file).toString()

    private fun getDataForFile(file: VirtualFile, scope: GlobalSearchScope): BindingXmlData? {
      val index = FileBasedIndex.getInstance()
      return index.getValues(NAME, getKeyForFile(file), scope).firstOrNull()
    }

    fun getDataForFile(project: Project, file: VirtualFile) = getDataForFile(file, GlobalSearchScope.fileScope(project, file))
    fun getDataForFile(psiFile: PsiFile) = getDataForFile(psiFile.virtualFile, GlobalSearchScope.fileScope(psiFile))

    /**
     * Returns all entries that match a given [layoutName].
     *
     * This may return multiple entries as a layout may have multiple configurations.
     */
    private fun getEntriesForLayout(project: Project, layoutName: String, scope: GlobalSearchScope): Collection<Entry> {
      val entries = mutableListOf<Entry>()
      FilenameIndex.getVirtualFilesByName(project, "$layoutName.xml", scope).forEach { file ->
        getDataForFile(file, scope)?.let { data -> entries.add(Entry(file, data)) }
      }
      return entries
    }

    @JvmStatic
    fun getEntriesForLayout(project: Project, layoutName: String) = getEntriesForLayout(project, layoutName, project.projectScope())
    fun getEntriesForLayout(module: Module, layoutName: String) = getEntriesForLayout(module.project, layoutName,
                                                                                      module.moduleContentWithDependenciesScope)
  }

  override fun getKeyDescriptor(): KeyDescriptor<String> {
    return EnumeratorStringDescriptor.INSTANCE
  }

  override fun dependsOnFileContent() = true

  /**
   * Defines the data externalizer handling the serialization/de-serialization of indexed information.
   */
  override fun getValueExternalizer(): DataExternalizer<BindingXmlData> {
    return object : DataExternalizer<BindingXmlData> {
      override fun save(out: DataOutput, value: BindingXmlData?) {
        value ?: return
        writeINT(out, value.layoutType.ordinal)
        writeINT(out, if (value.viewBindingIgnore) 1 else 0)
        IOUtil.writeUTF(out, value.customBindingName ?: "")

        writeINT(out, value.imports.size)
        for (import in value.imports) {
          IOUtil.writeUTF(out, import.type)
          IOUtil.writeUTF(out, import.alias ?: "")
        }

        writeINT(out, value.variables.size)
        for (variable in value.variables) {
          IOUtil.writeUTF(out, variable.name)
          IOUtil.writeUTF(out, variable.type)
        }

        writeINT(out, value.viewIds.size)
        for (viewId in value.viewIds) {
          IOUtil.writeUTF(out, viewId.id)
          IOUtil.writeUTF(out, viewId.viewName)
          IOUtil.writeUTF(out, viewId.layoutName ?: "")
        }
      }

      override fun read(`in`: DataInput): BindingXmlData {
        val layoutType = BindingLayoutType.values()[readINT(`in`)]
        val viewBindingIgnore = readINT(`in`) == 1
        val customBindingName = IOUtil.readUTF(`in`).ifEmpty { null }

        val imports = mutableListOf<ImportData>()
        for (i in 0 until readINT(`in`)) {
          imports.add(ImportData(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        val variables = mutableListOf<VariableData>()
        for (i in 0 until readINT(`in`)) {
          variables.add(VariableData(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`)))
        }

        val viewIds = mutableListOf<ViewIdData>()
        for (i in 1..readINT(`in`)) {
          viewIds.add(ViewIdData(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`),
                                 IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        return BindingXmlData(layoutType, viewBindingIgnore, customBindingName, imports, variables, viewIds)
      }
    }
  }

  override fun getName(): ID<String, BindingXmlData> {
    return NAME
  }

  override fun getIndexer(): DataIndexer<String, BindingXmlData, FileContent> {
    return DataIndexer { inputData ->
      var isDataBindingLayout = false
      var customBindingName: String? = null
      var viewBindingIgnore = false
      val variables = mutableListOf<VariableData>()
      val imports = mutableListOf<ImportData>()
      val viewIds = mutableListOf<ViewIdData>()

      class TagData(val name: String) {
        var importType: String? = null
        var importAlias: String? = null

        var variableName: String? = null
        var variableType: String? = null

        var viewClass: String? = null
        var viewId: String? = null
        var viewLayout: String? = null
      }

      NanoXmlUtil.parse(EscapingXmlReader(inputData.contentAsText), object : NanoXmlBuilder {
        var currTag: TagData? = null
        var isRootElement = true

        override fun startElement(name: String, nsPrefix: String?, nsURI: String?, systemID: String, lineNr: Int) {
          currTag = TagData(name)
          if (name == TAG_LAYOUT) {
            isDataBindingLayout = true
          }
        }

        override fun addAttribute(key: String, nsPrefix: String?, nsURI: String?, value: String, type: String) {
          val currTag = currTag!! // Always valid inside tags
          when (currTag.name) {
            SdkConstants.TAG_DATA ->
              when (key) {
                SdkConstants.ATTR_CLASS -> customBindingName = value
              }

            SdkConstants.TAG_IMPORT ->
              when (key) {
                SdkConstants.ATTR_TYPE -> currTag.importType = value
                SdkConstants.ATTR_ALIAS -> currTag.importAlias = value
              }

            SdkConstants.TAG_VARIABLE ->
              when (key) {
                SdkConstants.ATTR_NAME -> currTag.variableName = value
                SdkConstants.ATTR_TYPE -> currTag.variableType = value
              }

            else -> {
              // If here, we are a View tag, e.g. <Button>, <EditText>, <include>, <merge>, etc.

              if (nsURI == SdkConstants.ANDROID_URI) {
                when (key) {
                  // Used to determine view type of <View>.
                  SdkConstants.ATTR_CLASS -> currTag.viewClass = value
                  SdkConstants.ATTR_ID -> currTag.viewId = stripPrefixFromId(value)
                }
              }
              if (currTag.name == SdkConstants.VIEW_INCLUDE || currTag.name == SdkConstants.VIEW_MERGE) {
                when (key) {
                  // Used to determine view type of <Merge> and <Include>.
                  SdkConstants.ATTR_LAYOUT -> currTag.viewLayout = value
                }
              }
            }
          }
          if (isRootElement) {
            if (nsURI == SdkConstants.TOOLS_URI && key == SdkConstants.ATTR_VIEW_BINDING_IGNORE) {
              viewBindingIgnore = value.toBoolean()
            }
          }
        }

        override fun elementAttributesProcessed(name: String, nsPrefix: String?, nsURI: String?) {
          val currTag = currTag!! // Always valid inside tags
          when (currTag.name) {
            SdkConstants.TAG_DATA -> {
              // Nothing to do here, but case needed to avoid ending up in default branch
            }

            SdkConstants.TAG_IMPORT ->
              if (currTag.importType != null) {
                imports.add(ImportData(currTag.importType!!, currTag.importAlias))
              }

            SdkConstants.TAG_VARIABLE ->
              if (currTag.variableName != null && currTag.variableType != null) {
                variables.add(VariableData(currTag.variableName!!, currTag.variableType!!))
              }

            else ->
              if (currTag.viewId != null) {
                // Tag should either be something like <TextView>, <Button>, etc.
                // OR the special-case <view class="path.to.CustomView"/>
                val viewName = if (currTag.name != SdkConstants.VIEW_TAG) currTag.name else currTag.viewClass
                if (viewName != null) {
                  viewIds.add(ViewIdData(currTag.viewId!!, viewName, currTag.viewLayout))
                }
              }
            }

          this.currTag = null
        }

        override fun endElement(name: String, nsPrefix: String?, nsURI: String?) {
          isRootElement = false
        }
      })

      val layoutType = if (isDataBindingLayout) DATA_BINDING_LAYOUT else VIEW_BINDING_LAYOUT
      mapOf(getKeyForFile(inputData.file) to BindingXmlData(layoutType, viewBindingIgnore, customBindingName, imports, variables, viewIds))
    }
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      override fun acceptInput(file: VirtualFile): Boolean {
        return "xml" == file.extension
               && ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.LAYOUT
               && file.parent?.parent?.name == FD_RES
               && XmlFileType.INSTANCE == file.fileType
      }
    }
  }

  override fun getVersion() = 5
}

/**
 * Reader that attempts to escape known codes (e.g. "&lt;") on the fly as it reads.
 *
 * It seems that NanoXml does not itself translate escape characters, instead skipping over them.
 * So, we have to intercept them ourselves. For the attributes we parse, we only care about a
 * subset of all potentially escaped characters -- specifically '<' and '>', which can be used
 * in generic types. The rest, we can skip over, which NanoXml would have done anyway.
 */
private class EscapingXmlReader(text: CharSequence): Reader() {
  private val delegate = CharArrayUtil.readerFromCharSequence(text)
  private val buffer = StringBuilder()

  override fun read(cbuf: CharArray, off: Int, len: Int): Int {
    var numRead = 0
    while (numRead < len) {
      var nextChar: Char = delegate.read().takeIf { it >= 0 }?.toChar() ?: break
      var skipChar = false
      if (nextChar == '&') {
        assert(buffer.isEmpty())

        buffer.append(nextChar)
        while (true) {
          nextChar = delegate.read().takeIf { it >= 0 }?.toChar() ?: break
          buffer.append(nextChar)
          if (nextChar == ';')
            break
        }

        when (buffer.toString()) {
          "&lt;" -> nextChar = '<'
          "&gt;" -> nextChar = '>'
          else -> skipChar = true
        }
        buffer.clear()
      }

      if (!skipChar) {
        cbuf[off + numRead] = nextChar
        ++numRead
      }
    }

    return if (numRead > 0) numRead else -1
  }

  override fun close() {
    delegate.close()
  }
}
