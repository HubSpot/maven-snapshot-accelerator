package com.hubspot.snapshots.api;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;

import io.dropwizard.Application;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Environment;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

public class AcceleratorService extends Application<AcceleratorConfiguration> {

  public static void main(String... args) throws Exception {
    new AcceleratorService().run(args);
  }

  @Override
  public void run(AcceleratorConfiguration configuration, Environment environment) {
    final DBIFactory factory = new DBIFactory();
    final DBI jdbi = factory.build(environment, configuration.getDataSourceFactory(), "mysql");

    if (configuration.getDataSourceFactory().getInitializeSchema()) {
      jdbi.withHandle(new HandleCallback<Void>() {

        @Override
        public Void withHandle(Handle handle) throws Exception {
          DatabaseConnection connection = new JdbcConnection(handle.getConnection());
          Liquibase liquibase = new Liquibase("schema.sql", new ClassLoaderResourceAccessor(), connection);
          liquibase.update(new Contexts());
          return null;
        }
      });
    }

    final SnapshotDao dao = jdbi.onDemand(SnapshotDao.class);
    environment.jersey().register(new SnapshotResource(dao));
  }
}
