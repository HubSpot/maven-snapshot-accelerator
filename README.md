# maven-snapshot-accelerator

## Background

Normally, when building with Maven, it requires at least two round-trips to the remote repository to resolve each snapshot dependency, one to fetch the `maven-metadata.xml` and one to fetch the `maven-metadata.xml.sha1` (this is assuming an update policy of `always`). For apps with hundreds of snapshot dependencies, this latency starts to become noticeable. And if the there is high latency to the remote repository (if these round-trips are transatlantic, for example), then the latency becomes downright unusable.

In the CI use-case, the latency between our repository manager and CI server has been low enough that we've just ignored this problem. And for local development, we would either build in offline mode or set a really long interval in our update policy. But eventually you'll need to pick up a new build of a dependency or your update policy interval will lapse and then you'll have no choice but to go watch an episode of Curb Your Enthusiasm while your build chugs along. So the goal of this project is to speed up this dependency resolution process when using Maven snapshots.

## Design

Ultimately, the idea is to have a service that can tell you in a single round-trip all of the snapshot dependencies that have changed since your previous build, and then use this information to short-circuit requests to the remote repository (if a dependency hasn't published a new snapshot, there's no need to fetch the `maven-metadata.xml` or `maven-metadata.xml.sha1` for it). To make this work, there are three parts:

- The API which supports two basic operations: Notify of a new snapshot, and give me all changed snapshots by offset. Right now this expects a SQL DB for persistence, but could be made more pluggable.
- The Maven plugin which notifies the API after a snapshot has been published to the remote repository. You can add this to your CI script to make sure it happens for all builds, something like: 

`mvn -B deploy com.hubspot.snapshots:accelerator-maven-plugin:0.1:report`
- The Maven extension which hits the API at the start of a build to find all new snapshots and then short-circuits metadata requests for dependencies that haven't changed.

## Getting Started
