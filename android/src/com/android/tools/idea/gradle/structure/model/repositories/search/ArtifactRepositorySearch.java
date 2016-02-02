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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

@NotThreadSafe
public class ArtifactRepositorySearch {
  @NotNull private final List<ArtifactRepository> myRepositories;

  public ArtifactRepositorySearch(@NotNull List<ArtifactRepository> repositories) {
    myRepositories = repositories;
  }

  @NotNull
  public Callback start(@NotNull final SearchRequest request) {
    final Callback callback = new Callback();

    final List<Future<SearchResult>> jobs = Lists.newArrayListWithExpectedSize(myRepositories.size());
    final List<SearchResult> results = Lists.newArrayList();
    final List<Exception> errors = Lists.newArrayList();

    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        for (final ArtifactRepository repository : myRepositories) {
          jobs.add(application.executeOnPooledThread(new Callable<SearchResult>() {
            @Override
            public SearchResult call() throws Exception {
              return repository.search(request);
            }
          }));
        }

        for (Future<SearchResult> job : jobs) {
          try {
            results.add(Futures.get(job, Exception.class));
          }
          catch (Exception e) {
            errors.add(e);
          }
        }
        callback.setDone(results, errors);
      }
    });
    return callback;
  }

  public static class Callback extends ActionCallback {
    private List<SearchResult> mySearchResults;
    private List<Exception> myErrors;

    void setDone(@NotNull List<SearchResult> searchResults, @NotNull List<Exception> myErrors) {
      mySearchResults = ImmutableList.copyOf(searchResults);
      myErrors = ImmutableList.copyOf(myErrors);
      setDone();
    }

    @NotNull
    public List<SearchResult> getSearchResults() {
      checkIsDone();
      assert mySearchResults != null;
      return mySearchResults;
    }

    @Nullable
    public List<Exception> getErrors() {
      checkIsDone();
      assert myErrors != null;
      return myErrors;
    }

    private void checkIsDone() {
      if (!isDone()) {
        throw new IllegalStateException("Repository search has not finished yet");
      }
    }
  }
}
