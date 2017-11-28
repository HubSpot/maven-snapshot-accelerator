package com.hubspot.snapshots.api;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.hubspot.snapshots.core.SnapshotVersion;
import com.hubspot.snapshots.core.SnapshotVersionEgg;
import com.hubspot.snapshots.core.Snapshots;

@Path("/snapshots")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SnapshotResource {
  private final SnapshotDao snapshotDao;

  @Inject
  public SnapshotResource(SnapshotDao snapshotDao) {
    this.snapshotDao = snapshotDao;
  }

  @GET
  @Path("/delta")
  public Snapshots getDelta(@QueryParam("offset") int offset) {
    List<SnapshotVersion> snapshots = snapshotDao.getDelta(offset);
    int nextOffset = nextOffset(snapshots, offset);
    return new Snapshots(snapshots, snapshots.size() == SnapshotDao.PAGE_SIZE, nextOffset);
  }

  @POST
  public void report(SnapshotVersionEgg snapshot) {
    snapshotDao.save(snapshot);
  }

  private static int nextOffset(List<SnapshotVersion> snapshots, int previous) {
    int offset = previous;

    for (SnapshotVersion snapshot : snapshots) {
      if (snapshot.getId() > offset) {
        offset = snapshot.getId();
      }
    }

    return offset;
  }
}
