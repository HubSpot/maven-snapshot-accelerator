package com.hubspot.snapshots.api;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.Configuration;

public class AcceleratorConfiguration extends Configuration {

  @Valid
  @NotNull
  private AcceleratorDataSourceFactory database = new AcceleratorDataSourceFactory();

  @JsonProperty("database")
  public AcceleratorDataSourceFactory getDataSourceFactory() {
    return database;
  }

  @JsonProperty("database")
  public void setDataSourceFactory(AcceleratorDataSourceFactory factory) {
    this.database = factory;
  }
}
