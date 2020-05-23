package org.jetbrains.android.maven;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Tag("resolved-info")
public class MavenArtifactResolvedInfo {
  private String myApiLevel;
  private List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo>
    myDependencies = new ArrayList<>();

  public MavenArtifactResolvedInfo(String apiLevel,
                                   Collection<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> dependencyInfos) {
    myApiLevel = apiLevel;
    myDependencies = new ArrayList<>(dependencyInfos);
  }

  public MavenArtifactResolvedInfo() {
  }

  public String getApiLevel() {
    return myApiLevel;
  }

  @XCollection(propertyElementName = "dependencies")
  public List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> getDependencies() {
    return myDependencies;
  }

  public void setDependencies(List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> dependencies) {
    myDependencies = dependencies;
  }

  public void setApiLevel(String apiLevel) {
    myApiLevel = apiLevel;
  }
}
