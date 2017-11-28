package com.hubspot.snapshots.core;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Snapshots {
  private final Collection<SnapshotVersion> versions;
  private final boolean hasMore;
  private final int nextOffset;

  @JsonCreator
  public Snapshots(@JsonProperty("versions") Collection<SnapshotVersion> versions,
                   @JsonProperty("hasMore") boolean hasMore,
                   @JsonProperty("nextOffset") int nextOffset) {
    this.versions = versions;
    this.hasMore = hasMore;
    this.nextOffset = nextOffset;
  }

  public Collection<SnapshotVersion> getVersions() {
    return versions;
  }

  @JsonProperty("hasMore")
  public boolean hasMore() {
    return hasMore;
  }

  public int getNextOffset() {
    return nextOffset;
  }
}
