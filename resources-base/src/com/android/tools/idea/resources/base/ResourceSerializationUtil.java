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
package com.android.tools.idea.resources.base;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static methods for serialization and deserialization of resources implementing {@link BasicResourceItem} interface.
 */
public class ResourceSerializationUtil {
  private static final Logger LOG = Logger.getInstance(ResourceSerializationUtil.class);

  /**
   * Writes contents of a resource repository to a cache file on disk.
   *
   * The data is stored as follows:
   * <ol>
   *   <li>The header provided by the caller (sequence of bytes)</li>
   *   <li>Number of folder configurations (int)</li>
   *   <li>Qualifier strings of folder configurations (strings)</li>
   *   <li>Number of value resource files (int)</li>
   *   <li>Value resource files (see {@link ResourceSourceFile#serialize})</li>
   *   <li>Number of namespace resolvers (int)</li>
   *   <li>Serialized namespace resolvers (see {@link NamespaceResolver#serialize})</li>
   *   <li>Number of resource items (int)</li>
   *   <li>Serialized resource items (see {@link BasicResourceItemBase#serialize})</li>
   * </ol>
   */
  public static void createPersistentCache(@NotNull Path cacheFile, @NotNull byte[] fileHeader,
                                           @NotNull Base128StreamWriter contentWriter) {
    // Write to a temporary file first, then rename it to the final name.
    Path tempFile;
    try {
      Files.deleteIfExists(cacheFile);
      tempFile = FileUtilRt.createTempFile(cacheFile.getParent().toFile(), cacheFile.getFileName().toString(), ".tmp").toPath();
    }
    catch (IOException e) {
      LOG.error("Unable to create a temporary file in " + cacheFile.getParent().toString(), e);
      return;
    }

    try (Base128OutputStream stream = new Base128OutputStream(tempFile)) {
      stream.write(fileHeader);
      contentWriter.write(stream);
    }
    catch (Throwable e) {
      LOG.error("Unable to create cache file " + tempFile.toString(), e);
      deleteIgnoringErrors(tempFile);
      return;
    }

    try {
      Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (NoSuchFileException e) {
      // Ignore. This may happen in tests if the "caches" directory was cleaned up by a test tear down.
    } catch (IOException e) {
      LOG.error("Unable to create cache file " + cacheFile.toString(), e);
      deleteIgnoringErrors(tempFile);
    }
  }

  /**
   * Writes resources to the given output stream.
   *
   * @param resources the resources to write
   * @param stream the stream to write to
   * @param configFilter only resources belonging to configurations satisfying this filter are written to the stream
   */
  public static void writeResourcesToStream(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> resources,
                                            @NotNull Base128OutputStream stream,
                                            @NotNull Predicate<FolderConfiguration> configFilter) throws IOException {
    ObjectIntHashMap<String> qualifierStringIndexes = new ObjectIntHashMap<>();
    ObjectIntHashMap<ResourceSourceFile> sourceFileIndexes = new ObjectIntHashMap<>();
    ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes = new ObjectIntHashMap<>();
    int itemCount = 0;
    Collection<ListMultimap<String, ResourceItem>> resourceMaps = resources.values();

    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      for (ResourceItem item : resourceMap.values()) {
        FolderConfiguration configuration = item.getConfiguration();
        if (configFilter.test(configuration)) {
          String qualifier = configuration.getQualifierString();
          if (!qualifierStringIndexes.containsKey(qualifier)) {
            qualifierStringIndexes.put(qualifier, qualifierStringIndexes.size());
          }
          if (item instanceof BasicValueResourceItemBase) {
            ResourceSourceFile sourceFile = ((BasicValueResourceItemBase)item).getSourceFile();
            if (!sourceFileIndexes.containsKey(sourceFile)) {
              sourceFileIndexes.put(sourceFile, sourceFileIndexes.size());
            }
          }
          if (item instanceof ResourceValue) {
            addToNamespaceResolverIndexes(((ResourceValue)item).getNamespaceResolver(), namespaceResolverIndexes);
          }
          if (item instanceof BasicStyleResourceItem) {
            for (StyleItemResourceValue styleItem : ((BasicStyleResourceItem)item).getDefinedItems()) {
              addToNamespaceResolverIndexes(styleItem.getNamespaceResolver(), namespaceResolverIndexes);
            }
          }
          else if (item instanceof BasicStyleableResourceItem) {
            for (AttrResourceValue attr : ((BasicStyleableResourceItem)item).getAllAttributes()) {
              addToNamespaceResolverIndexes(attr.getNamespaceResolver(), namespaceResolverIndexes);
            }
          }
          itemCount++;
        }
      }
    }

    writeStrings(qualifierStringIndexes, stream);
    writeSourceFiles(sourceFileIndexes, stream, qualifierStringIndexes);
    writeNamespaceResolvers(namespaceResolverIndexes, stream);

    stream.writeInt(itemCount);

    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      for (ResourceItem item : resourceMap.values()) {
        FolderConfiguration configuration = item.getConfiguration();
        if (configFilter.test(configuration)) {
          ((BasicResourceItemBase)item).serialize(stream, qualifierStringIndexes, sourceFileIndexes, namespaceResolverIndexes);
        }
      }
    }
  }

  private static void addToNamespaceResolverIndexes(@NotNull ResourceNamespace.Resolver resolver,
                                                    @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) {
    if (!namespaceResolverIndexes.containsKey(resolver)) {
      namespaceResolverIndexes.put(resolver, namespaceResolverIndexes.size());
    }
  }

  /**
   * Loads resources from the given input stream and passes then to the given consumer.
   * @see #writeResourcesToStream
   */
  public static void readResourcesFromStream(@NotNull Base128InputStream stream,
                                             @NotNull Map<String, String> stringCache,
                                             @Nullable Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache,
                                             @NotNull LoadableResourceRepository repository,
                                             @NotNull Consumer<BasicResourceItem> resourceConsumer) throws IOException {
    stream.setStringCache(stringCache); // Enable string instance sharing to minimize memory consumption.

    int n = stream.readInt();
    List<RepositoryConfiguration> configurations = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      String configQualifier = stream.readString();
      if (configQualifier == null) {
        throw Base128InputStream.StreamFormatException.invalidFormat();
      }
      FolderConfiguration folderConfig = FolderConfiguration.getConfigForQualifierString(configQualifier);
      if (folderConfig == null) {
        throw Base128InputStream.StreamFormatException.invalidFormat();
      }
      configurations.add(new RepositoryConfiguration(repository, folderConfig));
    }

    n = stream.readInt();
    List<ResourceSourceFile> newSourceFiles = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      ResourceSourceFile sourceFile = repository.deserializeResourceSourceFile(stream, configurations);
      newSourceFiles.add(sourceFile);
    }

    n = stream.readInt();
    List<ResourceNamespace.Resolver> newNamespaceResolvers = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      NamespaceResolver namespaceResolver = NamespaceResolver.deserialize(stream);
      if (namespaceResolverCache != null) {
        namespaceResolver = namespaceResolverCache.computeIfAbsent(namespaceResolver, Function.identity());
      }
      newNamespaceResolvers.add(namespaceResolver);
    }

    n = stream.readInt();
    for (int i = 0; i < n; i++) {
      BasicResourceItemBase item = BasicResourceItemBase.deserialize(stream, configurations, newSourceFiles, newNamespaceResolvers);
      resourceConsumer.accept(item);
    }
  }

  /**
   * Checks that contents of the given stream starting from the current position match the expected sequence of bytes.
   *
   * @param expectedContents the sequence of bytes expected to be present in the stream
   * @param stream the stream to read the data from
   * @return true if the stream contents match, false otherwise
   */
  public static boolean validateContents(@NotNull byte[] expectedContents, @NotNull Base128InputStream stream) throws IOException {
    for (byte expected : expectedContents) {
      byte b = stream.readByte();
      if (b != expected) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns contents of a cache file header produced by the given writer code.
   *
   * @param headerWriter the writer object
   * @return the cache file header contents in a byte array
   */
  @NotNull
  public static byte[] getCacheFileHeader(@NotNull Base128StreamWriter headerWriter) {
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    try (Base128OutputStream stream = new Base128OutputStream(header)) {
      headerWriter.write(stream);
    }
    catch (IOException e) {
      throw new Error("Internal error", e); // An IOException in the try block above indicates a bug.
    }
    return header.toByteArray();
  }
  private static void deleteIgnoringErrors(@NotNull Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
    }
  }

  private static void writeStrings(@NotNull ObjectIntHashMap<String> qualifierStringIndexes, @NotNull Base128OutputStream stream)
      throws IOException {
    String[] strings = new String[qualifierStringIndexes.size()];
    qualifierStringIndexes.forEachEntry((str, index2) -> { strings[index2] = str; return true; });
    stream.writeInt(strings.length);
    for (String str : strings) {
      stream.writeString(str);
    }
  }

  private static void writeSourceFiles(@NotNull ObjectIntHashMap<ResourceSourceFile> sourceFileIndexes,
                                       @NotNull Base128OutputStream stream,
                                       @NotNull ObjectIntHashMap<String> qualifierStringIndexes) throws IOException {
    ResourceSourceFile[] sourceFiles = new ResourceSourceFile[sourceFileIndexes.size()];
    sourceFileIndexes.forEachEntry((sourceFile, index1) -> { sourceFiles[index1] = sourceFile; return true; });
    stream.writeInt(sourceFiles.length);
    for (ResourceSourceFile sourceFile : sourceFiles) {
      sourceFile.serialize(stream, qualifierStringIndexes);
    }
  }

  private static void writeNamespaceResolvers(@NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes,
                                              @NotNull Base128OutputStream stream) throws IOException {
    ResourceNamespace.Resolver[] resolvers = new ResourceNamespace.Resolver[namespaceResolverIndexes.size()];
    namespaceResolverIndexes.forEachEntry((resolver, index) -> { resolvers[index] = resolver; return true; });
    stream.writeInt(resolvers.length);
    for (ResourceNamespace.Resolver resolver : resolvers) {
      NamespaceResolver serializableResolver =
          resolver == ResourceNamespace.Resolver.EMPTY_RESOLVER ? NamespaceResolver.EMPTY : (NamespaceResolver)resolver;
      serializableResolver.serialize(stream);
    }
  }

  public interface Base128StreamWriter {
    void write(@NotNull Base128OutputStream stream) throws IOException;
  }
}
