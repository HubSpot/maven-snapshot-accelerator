package com.hubspot.snapshots.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SnapshotVersion implements SnapshotVersionCore {
  private final int id;
  private final String groupId;
  private final String artifactId;
  private final String baseVersion;
  private final String resolvedVersion;

  @JsonCreator
  public SnapshotVersion(@JsonProperty("id") int id,
                         @JsonProperty("groupId") String groupId,
                         @JsonProperty("artifactId") String artifactId,
                         @JsonProperty("baseVersion") String baseVersion,
                         @JsonProperty("resolvedVersion") String resolvedVersion) {
    this.id = id;
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.baseVersion = baseVersion;
    this.resolvedVersion = resolvedVersion;
  }

  public int getId() {
    return id;
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

  @JsonIgnore
  public String getTimestamp() {
    String[] parts = resolvedVersion.split("-");
    if (parts.length < 3) {
      throw new IllegalStateException();
    }

    return parts[parts.length - 2];
  }
}
