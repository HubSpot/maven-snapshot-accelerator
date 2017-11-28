package com.hubspot.snapshots.core;

public interface SnapshotVersionCore {
  String getGroupId();
  String getArtifactId();
  String getBaseVersion();
  String getResolvedVersion();
}
