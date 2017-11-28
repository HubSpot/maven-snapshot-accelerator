package com.hubspot.snapshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.repository.LocalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hubspot.snapshots.client.AcceleratorClient;
import com.hubspot.snapshots.core.SnapshotVersion;

public enum AcceleratorUpdater {
  INSTANCE;

  private static final Logger LOG = LoggerFactory.getLogger(AcceleratorUpdater.class);

  private static final AtomicReference<Boolean> HEALTHY = new AtomicReference<>();

  private static boolean initialize(LocalRepository localRepository) {
    int offset = loadAcceleratorOffset(localRepository);
    LOG.debug("Loaded accelerator offset " + offset);

    int maxId = offset;
    int updated = 0;
    try {
      Iterator<SnapshotVersion> iter = AcceleratorClient.newInstance().getDelta(offset);
      while (iter.hasNext()) {
        SnapshotVersion snapshot = iter.next();
        updateSnapshotInfo(localRepository, snapshot);
        maxId = Math.max(maxId, snapshot.getId());
        updated++;
      }

      LOG.debug("Processed " + updated + " new snapshots");
      writeAcceleratorInfo(localRepository, maxId);
      LOG.debug("Wrote new accelerator offset " + maxId  + " to disk");

      LOG.info("Accelerator is healthy, will skip snapshot checks based on accelerator metadata");
      return true;
    } catch (Exception e) {
      LOG.warn("Unable to connect to the accelerator API at {}", AcceleratorClient.snapshotUrl());
      LOG.warn("Will need to check for all snapshot updates");
      recordAcceleratorFailure(localRepository, offset, e);
      return false;
    }
  }

  public boolean isHealthy(LocalRepository localRepository) {
    if (HEALTHY.get() == null) {
      synchronized (this) {
        if (HEALTHY.get() == null) {
          HEALTHY.set(initialize(localRepository));
        }
      }
    }

    return HEALTHY.get();
  }

  private static int loadAcceleratorOffset(LocalRepository localRepository) {
    Path acceleratorStatusPath = AcceleratorUtils.INSTANCE.acceleratorStatusPath(localRepository);

    Properties acceleratorProperties = AcceleratorUtils.INSTANCE.readProperties(acceleratorStatusPath);
    if (acceleratorProperties == null) {
      return 0;
    }

    String s = acceleratorProperties.getProperty(AcceleratorUtils.LAST_PROCESSED_ID);
    if (s == null) {
      LOG.debug("Accelerator file is missing " + AcceleratorUtils.LAST_PROCESSED_ID + " at path " + acceleratorStatusPath);
      return 0;
    }

    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      LOG.debug("Accelerator file has an invalid " + AcceleratorUtils.LAST_PROCESSED_ID + " at path " + acceleratorStatusPath, e);
      return 0;
    }
  }

  private static void updateSnapshotInfo(LocalRepository localRepository, SnapshotVersion snapshot) {
    Path snapshotInfoPath = AcceleratorUtils.INSTANCE.snapshotInfoPath(localRepository, snapshot);

    if (Files.isDirectory(snapshotInfoPath.getParent())) {
      List<String> lines = Arrays.asList(
              AcceleratorUtils.LATEST_SNAPSHOT_VERSION + "=" + snapshot.getResolvedVersion(),
              AcceleratorUtils.LATEST_SNAPSHOT_TIMESTAMP + "=" + snapshot.getTimestamp()
      );
      AcceleratorUtils.INSTANCE.writeToPath(lines, snapshotInfoPath);
    } else {
      LOG.debug("Skipping update because artifact is not in local repo for path " + snapshotInfoPath);
    }
  }

  private static void recordAcceleratorFailure(LocalRepository localRepository, int offset, Exception e) {
    LOG.debug("Error updating accelerator data", e);

    List<String> lines = Arrays.asList(
            AcceleratorUtils.LAST_UPDATE_SUCCESS + "=false",
            AcceleratorUtils.LAST_UPDATE_TIMESTAMP + "=" + System.currentTimeMillis(),
            AcceleratorUtils.LAST_PROCESSED_ID + "=" + offset
    );
    try {
      AcceleratorUtils.INSTANCE.writeToPath(lines, AcceleratorUtils.INSTANCE.acceleratorStatusPath(localRepository));
    } catch (Exception f) {
      LOG.debug("Error recording accelerator failure on disk", f);
    }
  }

  private static void writeAcceleratorInfo(LocalRepository localRepository, int offset) {
    List<String> lines = Arrays.asList(
            AcceleratorUtils.LAST_UPDATE_SUCCESS + "=true",
            AcceleratorUtils.LAST_UPDATE_TIMESTAMP + "=" + System.currentTimeMillis(),
            AcceleratorUtils.LAST_PROCESSED_ID + "=" + offset
    );
    AcceleratorUtils.INSTANCE.writeToPath(lines, AcceleratorUtils.INSTANCE.acceleratorStatusPath(localRepository));
  }
}
