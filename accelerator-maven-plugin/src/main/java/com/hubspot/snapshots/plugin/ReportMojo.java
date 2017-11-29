package com.hubspot.snapshots.plugin;

import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.hubspot.snapshots.client.AcceleratorClient;
import com.hubspot.snapshots.core.SnapshotVersionEgg;

@Mojo(name = "report", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class ReportMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(property = "accelerator.plugin.skip", defaultValue = "false")
  private boolean skip;

  @Parameter(property = "accelerator.failOnError", defaultValue = "false")
  private boolean failOnError;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping plugin execution");
      return;
    }

    Artifact artifact = project.getArtifact();

    // this check is also important because calling isSnapshot changes the object's internal state :facepalm:
    if (!artifact.isSnapshot()) {
      getLog().info("Skipping non-snapshot artifact");
      return;
    } else if (artifact.getVersion().endsWith("SNAPSHOT")) {
      getLog().warn("Skipping non-resolved snapshot version " + artifact.getVersion());
      return;
    }

    SnapshotVersionEgg snapshot = new SnapshotVersionEgg(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            artifact.getBaseVersion(),
            artifact.getVersion()
    );

    try {
      AcceleratorClient.detectingBaseUrl().report(snapshot);
      getLog().info("Successfully reported to accelerator API");
    } catch (IOException e) {
      if (failOnError) {
        getLog().error("Error reporting to accelerator API", e);
        throw new MojoExecutionException("Error reporting to accelerator API", e);
      } else {
        getLog().warn("Error reporting to accelerator API", e);
      }
    }
  }
}
