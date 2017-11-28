package com.hubspot.snapshots.api;

import org.skife.jdbi.v2.DBI;

import io.dropwizard.Application;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Environment;

public class AcceleratorService extends Application<AcceleratorConfiguration> {

  public static void main(String... args) throws Exception {
    new AcceleratorService().run(args);
  }

  @Override
  public void run(AcceleratorConfiguration configuration, Environment environment) {
    final DBIFactory factory = new DBIFactory();
    final DBI jdbi = factory.build(environment, configuration.getDataSourceFactory(), "mysql");
    final SnapshotDao dao = jdbi.onDemand(SnapshotDao.class);
    environment.jersey().register(new SnapshotResource(dao));
  }
}
