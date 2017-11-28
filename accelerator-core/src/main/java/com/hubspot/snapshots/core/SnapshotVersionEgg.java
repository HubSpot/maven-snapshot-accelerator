package com.hubspot.snapshots.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SnapshotVersionEgg implements SnapshotVersionCore {
  private final String groupId;
  private final String artifactId;
  private final String baseVersion;
  private final String resolvedVersion;

  @JsonCreator
  public SnapshotVersionEgg(@JsonProperty("groupId") String groupId,
                            @JsonProperty("artifactId") String artifactId,
                            @JsonProperty("baseVersion") String baseVersion,
                            @JsonProperty("resolvedVersion") String resolvedVersion) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.baseVersion = baseVersion;
    this.resolvedVersion = resolvedVersion;
  }

  @Override
  public String getGroupId() {
    return groupId;
  }

  @Override
  public String getArtifactId() {
    return artifactId;
  }

  @Override
  public String getBaseVersion() {
    return baseVersion;
  }

  @Override
  public String getResolvedVersion() {
    return resolvedVersion;
  }
}
