package com.hubspot.snapshots.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.db.DataSourceFactory;

public class AcceleratorDataSourceFactory extends DataSourceFactory {
  private boolean initializeSchema = false;

  @JsonProperty
  public boolean getInitializeSchema() {
    return initializeSchema;
  }

  @JsonProperty
  public void setInitializeSchema(boolean initializeSchema) {
    this.initializeSchema = initializeSchema;
  }
}
