package com.hubspot.snapshots;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hubspot.snapshots.core.SnapshotVersion;

public enum AcceleratorUtils {
  INSTANCE;

  private static final Logger LOG = LoggerFactory.getLogger(AcceleratorUtils.class);

  private static final Set<PosixFilePermission> PERMISSIONS = new HashSet<>(
          Arrays.asList(
                  PosixFilePermission.OWNER_READ,
                  PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.GROUP_READ,
                  PosixFilePermission.GROUP_WRITE,
                  PosixFilePermission.OTHERS_READ,
                  PosixFilePermission.OTHERS_WRITE
          )
  );

  static final String ACCELERATOR_STATUS_FILENAME = "accelerator.status";
  static final String ACCELERATOR_SNAPSHOT_FILENAME = "accelerator.snapshotInfo";
  static final String LAST_UPDATE_SUCCESS = "lastUpdateSuccess";
  static final String LAST_UPDATE_TIMESTAMP = "lastUpdateTimestamp";
  static final String LAST_PROCESSED_ID = "lastProcessedId";
  static final String LATEST_SNAPSHOT_VERSION = "latestSnapshotVersion";
  static final String LATEST_SNAPSHOT_TIMESTAMP = "latestSnapshotTimestamp";

  Path acceleratorStatusPath(LocalRepository localRepository) {
    return localRepo(localRepository).resolve(AcceleratorUtils.ACCELERATOR_STATUS_FILENAME);
  }

  Path snapshotInfoPath(LocalRepository localRepository, SnapshotVersion snapshot) {
    return baseDir(localRepository, snapshot.getGroupId(), snapshot.getArtifactId(), snapshot.getBaseVersion()).resolve(ACCELERATOR_SNAPSHOT_FILENAME);
  }

  Path snapshotInfoPath(LocalRepository localRepository, Metadata metadata) {
    return baseDir(localRepository, metadata.getGroupId(), metadata.getArtifactId(), metadata.getVersion()).resolve(ACCELERATOR_SNAPSHOT_FILENAME);
  }

  Path mavenMetadataPath(LocalRepository localRepository, Metadata metadata, RemoteRepository repository) {
    String fileName = "maven-metadata-" + repository.getId() +".xml";
    return baseDir(localRepository, metadata.getGroupId(), metadata.getArtifactId(), metadata.getVersion()).resolve(fileName);
  }

  private Path baseDir(LocalRepository localRepository, String groupId, String artifactId, String version) {
    String[] groupParts = groupId.split("\\.");

    Path snapshotInfoPath = localRepo(localRepository);
    for (String groupPart : groupParts) {
      snapshotInfoPath = snapshotInfoPath.resolve(groupPart);
    }
    return snapshotInfoPath
            .resolve(artifactId)
            .resolve(version);
  }

  Properties readProperties(Path path) {
    try (InputStream inputStream = Files.newInputStream(path, StandardOpenOption.READ)) {
      Properties properties = new Properties();
      properties.load(inputStream);
      return properties;
    } catch (IOException e) {
      LOG.debug("Error trying to read properties from " + path, e);
      return null;
    }
  }

  void writeToPath(Iterable<? extends CharSequence> lines, Path path) {
    Path temp = null;
    try {
      temp = Files.createTempFile(path.getParent(), "accelerator-", ".tmp");
      Files.setPosixFilePermissions(temp, PERMISSIONS);
      Files.write(temp, lines, StandardCharsets.UTF_8);
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new RuntimeException("Error writing accelerator data to path " + path, e);
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {}
      }
    }
  }

  private static Path localRepo(LocalRepository localRepository) {
    return localRepository.getBasedir().toPath();
  }
}
