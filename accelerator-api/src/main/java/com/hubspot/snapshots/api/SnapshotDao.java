package com.hubspot.snapshots.api;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapperFactory;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import com.hubspot.snapshots.core.SnapshotVersion;
import com.hubspot.snapshots.core.SnapshotVersionEgg;
import com.hubspot.rosetta.jdbi.BindWithRosetta;
import com.hubspot.rosetta.jdbi.RosettaMapperFactory;

@RegisterMapperFactory(RosettaMapperFactory.class)
public interface SnapshotDao extends Transactional<SnapshotDao> {
  int PAGE_SIZE = 1000;

  @SqlQuery("SELECT * FROM latest_snapshots WHERE id > :offset LIMIT " + PAGE_SIZE)
  List<SnapshotVersion> getDelta(@Bind("offset") int offset);

  @SqlUpdate("DELETE FROM latest_snapshots WHERE groupId = :groupId AND artifactId = :artifactId AND baseVersion = :baseVersion")
  void delete(@BindWithRosetta SnapshotVersionEgg snapshot);

  @GetGeneratedKeys
  @SqlUpdate("INSERT INTO latest_snapshots (groupId, artifactId, baseVersion, resolvedVersion) VALUES (:groupId, :artifactId, :baseVersion, :resolvedVersion)")
  int insert(@BindWithRosetta SnapshotVersionEgg snapshot);
}
