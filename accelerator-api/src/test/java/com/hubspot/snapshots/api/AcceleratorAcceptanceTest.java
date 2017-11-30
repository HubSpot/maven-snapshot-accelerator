package com.hubspot.snapshots.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.fasterxml.jackson.databind.MapperFeature;
import com.hubspot.rosetta.Rosetta;
import com.hubspot.snapshots.client.AcceleratorClient;
import com.hubspot.snapshots.core.SnapshotVersion;
import com.hubspot.snapshots.core.SnapshotVersionEgg;

import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;

public class AcceleratorAcceptanceTest {
  private static final AtomicInteger SNAPSHOT_COUNTER = new AtomicInteger(0);

  @ClassRule
  public static final DropwizardAppRule<AcceleratorConfiguration> RULE = new DropwizardAppRule<>(
          AcceleratorService.class,
          ResourceHelpers.resourceFilePath("test.yaml"),
          ConfigOverride.config("server.connector.port", "0")
  );

  private static ManagedDataSource dataSource;
  private static AcceleratorClient client;

  @BeforeClass
  public static void setup() throws Exception {
    Rosetta.getMapper().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);

    dataSource = RULE.getConfiguration().getDataSourceFactory().build(RULE.getEnvironment().metrics(), "test");
    dataSource.start();

    client = AcceleratorClient.withBaseUrl(String.format("http://localhost:%d/accelerator", RULE.getLocalPort()));
  }

  @After
  public void cleanup() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.prepareStatement("TRUNCATE TABLE latest_snapshots").execute();
    }
  }

  @AfterClass
  public static void teardown() throws Exception {
    dataSource.stop();
  }

  @Test
  public void itReturnsEmptyDeltaWhenNoSnapshotsPresent() throws IOException {
    List<SnapshotVersion> snapshots = toList(client.getDelta(0));
    assertThat(snapshots).isEmpty();
  }

  @Test
  public void itReturnsNewSnapshotFromDelta() throws IOException {
    SnapshotVersionEgg snapshot = nextSnapshot();
    client.report(snapshot);

    List<SnapshotVersion> snapshots = toList(client.getDelta(0));
    assertThat(snapshots).hasSize(1);

    SnapshotVersion actual = snapshots.get(0);
    assertThat(actual.getGroupId()).isEqualTo(snapshot.getGroupId());
    assertThat(actual.getArtifactId()).isEqualTo(snapshot.getArtifactId());
    assertThat(actual.getBaseVersion()).isEqualTo(snapshot.getBaseVersion());
    assertThat(actual.getResolvedVersion()).isEqualTo(snapshot.getResolvedVersion());

    List<SnapshotVersion> nextPage = toList(client.getDelta(actual.getId()));
    assertThat(nextPage).isEmpty();
  }

  @Test
  public void itOverwritesSnapshotWithSameCoordinates() throws IOException {
    SnapshotVersionEgg first = nextSnapshot();
    client.report(first);

    SnapshotVersionEgg second = nextSnapshot();
    client.report(second);

    List<SnapshotVersion> snapshots = toList(client.getDelta(0));
    assertThat(snapshots).hasSize(1);

    SnapshotVersion actual = snapshots.get(0);
    assertThat(actual.getGroupId()).isEqualTo(second.getGroupId());
    assertThat(actual.getArtifactId()).isEqualTo(second.getArtifactId());
    assertThat(actual.getBaseVersion()).isEqualTo(second.getBaseVersion());
    assertThat(actual.getResolvedVersion()).isEqualTo(second.getResolvedVersion());

    SnapshotVersionEgg third = nextSnapshot();
    client.report(third);

    List<SnapshotVersion> nextPage = toList(client.getDelta(actual.getId()));
    assertThat(nextPage).hasSize(1);

    actual = nextPage.get(0);
    assertThat(actual.getGroupId()).isEqualTo(third.getGroupId());
    assertThat(actual.getArtifactId()).isEqualTo(third.getArtifactId());
    assertThat(actual.getBaseVersion()).isEqualTo(third.getBaseVersion());
    assertThat(actual.getResolvedVersion()).isEqualTo(third.getResolvedVersion());
  }

  private static SnapshotVersionEgg nextSnapshot() {
    return new SnapshotVersionEgg(
            "com.test",
            "test",
            "0.1-SNAPSHOT",
            "0.1-20171129.222952-" + SNAPSHOT_COUNTER.incrementAndGet()
    );
  }

  private static <T> List<T> toList(Iterator<T> iterator) {
    List<T> list = new ArrayList<>();
    while (iterator.hasNext()) {
      list.add(iterator.next());
    }

    return list;
  }
}
