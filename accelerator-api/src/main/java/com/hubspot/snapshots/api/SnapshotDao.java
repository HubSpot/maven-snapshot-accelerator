package com.hubspot.snapshots.api;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapperFactory;

import com.hubspot.snapshots.core.SnapshotVersion;
import com.hubspot.snapshots.core.SnapshotVersionEgg;
import com.hubspot.rosetta.jdbi.BindWithRosetta;
import com.hubspot.rosetta.jdbi.RosettaMapperFactory;

@RegisterMapperFactory(RosettaMapperFactory.class)
public interface SnapshotDao {
  int PAGE_SIZE = 1000;

  @SqlQuery("SELECT * FROM latest_snapshots WHERE id > :offset LIMIT " + PAGE_SIZE)
  List<SnapshotVersion> getDelta(@Bind("offset") int offset);

  @SqlUpdate("REPLACE INTO latest_snapshots (groupId, artifactId, baseVersion, resolvedVersion) VALUES (:groupId, :artifactId, :baseVersion, :resolvedVersion)")
  void save(@BindWithRosetta SnapshotVersionEgg snapshot);
}
