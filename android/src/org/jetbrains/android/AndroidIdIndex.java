/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.android.SdkConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Stores a cross reference between declarations and references of Android ID resources and the XML
 * files containing them. The keys are the names of ID resources. The ID resource references
 * containing a package name are represented as "<i>package_name</i>:<i>local_name</i>". References
 * to ID resources of the Android framework are not stored. A '+' prefix in front of a name is used
 * to distinguish declarations ("@+id/"). A ',' prefix in front of a name is used to distinguish
 * a reference inside the value of a constraint_referenced_ids attribute (used in constraint
 * layouts).
 *
 * <p>A special {@link #MARKER} key can be used to retrieve all ID resources declared or
 * referenced in a file.
 *
 * @author Eugene.Kudelevsky
 */
public class AndroidIdIndex extends FileBasedIndexExtension<String, Set<String>> {
  public static final ID<String, Set<String>> INDEX_ID = ID.create("android.id.index");
  public static final String MARKER = "$";

  private static final DataIndexer<String, Set<String>, FileContent> INDEXER = new DataIndexer<String, Set<String>, FileContent>() {
    @Override
    @NotNull
    public Map<String, Set<String>> map(@NotNull FileContent inputData) {
      CharSequence content = inputData.getContentAsText();

      if (CharArrayUtil.indexOf(content, SdkConstants.NS_RESOURCES, 0) < 0) {
        return Collections.emptyMap();
      }
      HashMap<String, Set<String>> map = new HashMap<>();

      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
          super.addAttribute(key, nsPrefix, nsURI, value, type);
          boolean declaration = AndroidResourceUtil.isIdDeclaration(value);

          if (declaration || AndroidResourceUtil.isIdReference(value)) {
            String id = AndroidResourceUtil.getResourceNameByReferenceText(value);

            if (id != null) {
              if (declaration) {
                id = '+' + id;
              }
              map.put(id, Collections.emptySet());
            }
          }
          else if (AndroidResourceUtil.isConstraintReferencedIds(nsURI, nsPrefix, key)) {
            // The value of the app:constraint_referenced_ids attribute is a comma-separated list of names of ID resources.
            if (value != null) {
              for (String id : value.split(",")) {
                if (id != null) {
                  map.put(',' + id, Collections.emptySet());
                }
              }
            }
          }
        }
      });
      if (!map.isEmpty()) {
        map.put(MARKER, new HashSet<>(map.keySet()));
      }
      return map;
    }
  };

  private static final DataExternalizer<Set<String>> DATA_EXTERNALIZER = new DataExternalizer<Set<String>>() {
    @Override
    public void save(@NotNull DataOutput out, Set<String> value) throws IOException {
      out.writeInt(value.size());
      for (String s : value) {
        out.writeUTF(s);
      }
    }

    @Override
    public Set<String> read(@NotNull DataInput in) throws IOException {
      int size = in.readInt();

      if (size < 0 || size > 65535) { // 65K: maximum number of resources for a given type.
        // Something is very wrong (corrupt index); trigger an index rebuild.
        throw new IOException("Corrupt Index: Size " + size);
      }

      Set<String> result = new HashSet<>(size);

      for (int i = 0; i < size; i++) {
        String s = in.readUTF();
        result.add(s);
      }
      return result;
    }
  };

  @Override
  @NotNull
  public ID<String, Set<String>> getName() {
    return INDEX_ID;
  }

  @Override
  @NotNull
  public DataIndexer<String, Set<String>, FileContent> getIndexer() {
    return INDEXER;
  }

  @Override
  @NotNull
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  @NotNull
  public DataExternalizer<Set<String>> getValueExternalizer() {
    return DATA_EXTERNALIZER;
  }

  @Override
  @NotNull
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return file.isInLocalFileSystem();
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 4;
  }
}
