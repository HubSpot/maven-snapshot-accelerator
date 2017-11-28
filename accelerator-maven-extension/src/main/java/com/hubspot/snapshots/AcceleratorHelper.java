package com.hubspot.snapshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public enum AcceleratorHelper {
  INSTANCE;

  private static final Logger LOG = LoggerFactory.getLogger(AcceleratorHelper.class);
  private static final DocumentBuilderFactory DOCUMENT_FACTORY = DocumentBuilderFactory.newInstance();

  public boolean shouldSkipUpdate(LocalRepository localRepository, Metadata metadata, RemoteRepository repository) {
    if (!AcceleratorUpdater.INSTANCE.isHealthy(localRepository)) {
      return false;
    } else if (metadata.getGroupId().isEmpty() || metadata.getArtifactId().isEmpty()) {
      return false;
    } else if (metadata.getVersion().isEmpty() || !metadata.getVersion().endsWith("SNAPSHOT")) {
      return false;
    } else if (repository == null) {
      return false;
    }

    Path acceleratorMetadata = AcceleratorUtils.INSTANCE.snapshotInfoPath(localRepository, metadata);
    Path mavenMetadata = AcceleratorUtils.INSTANCE.mavenMetadataPath(localRepository, metadata, repository);

    if (!Files.isDirectory(mavenMetadata.getParent())) {
      return false;
    }

    String mavenTimestamp = loadMavenMetadataTimestamp(mavenMetadata);
    if (mavenTimestamp == null) {
      return false;
    }

    String acceleratorTimestamp = loadAcceleratorMetadataTimestamp(acceleratorMetadata);
    if (acceleratorTimestamp == null) {
      return true;
    }

    return acceleratorTimestamp.compareTo(mavenTimestamp) <= 0;
  }

  private static String loadAcceleratorMetadataTimestamp(Path path) {
    Properties acceleratorMetadata = AcceleratorUtils.INSTANCE.readProperties(path);
    if (acceleratorMetadata == null) {
      return null;
    }

    return acceleratorMetadata.getProperty(AcceleratorUtils.LATEST_SNAPSHOT_TIMESTAMP);
  }

  private static String loadMavenMetadataTimestamp(Path path) {
    try {
      DocumentBuilder builder = DOCUMENT_FACTORY.newDocumentBuilder();
      Element element = builder.parse(path.toFile()).getDocumentElement();
      element.normalize();

      Node versioning = getChild(element, "versioning");
      if (versioning == null) {
        return null;
      }

      Node snapshot = getChild(versioning, "snapshot");
      if (snapshot == null) {
        return null;
      }

      Node timestamp = getChild(snapshot, "timestamp");
      if (timestamp == null) {
        return null;
      }

      return timestamp.getTextContent();
    } catch (Exception e) {
      LOG.debug("Error parsing maven metadata at path " + path, e);
      return null;
    }
  }

  private static Node getChild(Node node, String name) {
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (name.equals(child.getNodeName())) {
        return child;
      }
    }

    return null;
  }
}
