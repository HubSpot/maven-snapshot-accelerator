# maven-snapshot-accelerator

## Background

Normally, when building with Maven, it requires at least two round-trips to the remote repository to resolve each snapshot dependency, one to fetch the `maven-metadata.xml` and one to fetch the `maven-metadata.xml.sha1` (this is assuming an update policy of `always`). For apps with hundreds of snapshot dependencies, this latency starts to become noticeable. And if the there is high latency to the remote repository (if these round-trips are transatlantic, for example), then the latency becomes downright unusable.

In the CI use-case, the latency between our repository manager and CI server has been low enough that we've just ignored this problem. And for local development, we would either build in offline mode or set a really long interval in our update policy. But eventually you'll need to pick up a new build of a dependency or your update policy interval will lapse and then you'll have no choice but to go watch an episode of Curb Your Enthusiasm while your build chugs along. So the goal of this project is to speed up this dependency resolution process when using Maven snapshots.

## Design

Ultimately, the idea is to have a service that can tell you in a single round-trip all of the snapshot dependencies that have changed since your previous build, and then use this information to short-circuit requests to the remote repository (if a dependency hasn't published a new snapshot, there's no need to fetch the `maven-metadata.xml` or `maven-metadata.xml.sha1` for it). If a snapshot has changed, Maven still needs to make multiple round-trips to the remote repository to fetch the new version, so the assumption underlying this design is that only a small percentage of dependencies change between builds. The system is comprised of three parts:

- The API which supports two basic operations: Notify of a new snapshot, and give me all changed snapshots by offset. Right now this expects a SQL DB for persistence, but could be made more pluggable.
- The Maven plugin which notifies the API after a snapshot has been published to the remote repository. You can add this to your CI script to make sure it happens for all builds, something like: 

`mvn -B deploy com.hubspot.snapshots:accelerator-maven-plugin:0.3:report`
- The Maven extension which hits the API at the start of a build to find all new snapshots and then short-circuits metadata requests for dependencies that haven't changed.

## Getting Started

### Running the API

The first step is to get the API up and running. We publish a JAR to Maven central with all of the dependencies bundled. The part you need to provide is your JDBC driver and a Dropwizard configuration pointing at the database. For testing, you can use an in-memory database like H2 along with [this](https://github.com/HubSpot/maven-snapshot-accelerator/blob/master/accelerator-api/src/test/resources/test.yaml) Dropwizard configuration (which we use for acceptance testing at build-time). To get the API up and running in this configuration, you need to download the API JAR, the H2 JAR, and the dropwizard configuration:
```bash
curl -L -O https://repo1.maven.org/maven2/com/hubspot/snapshots/accelerator-api/0.3/accelerator-api-0.3-shaded.jar
curl -L -O https://repo1.maven.org/maven2/com/h2database/h2/1.4.196/h2-1.4.196.jar
curl -L -O https://raw.githubusercontent.com/HubSpot/maven-snapshot-accelerator/master/accelerator-api/src/test/resources/test.yaml
```

Then you can run the API (Java 7+ required):
```bash
java -cp accelerator-api-0.3-shaded.jar:h2-1.4.196.jar com.hubspot.snapshots.api.AcceleratorService server test.yaml
```

Once the server has started up you can access the Dropwizard admin page at `http://localhost:8080/admin/`. From there you can click on the Healthcheck link to make sure that all healthchecks are passing, or click on the Metrics link to get a JSON dump of all metrics. You can also test the snapshot endpoints (examples are written with [HTTPie](https://github.com/jakubroztocil/httpie)):

```bash
# delta should not return any snapshots
➜  ~ http localhost:8080/accelerator/snapshots/delta offset==0
HTTP/1.1 200 OK
Content-Length: 46
Content-Type: application/json
Date: Thu, 30 Nov 2017 20:50:17 GMT
Vary: Accept-Encoding

{
    "hasMore": false, 
    "nextOffset": 0, 
    "versions": []
}

# report a new snapshot version to the API
➜  ~ http post localhost:8080/accelerator/snapshots groupId=com.test artifactId=test baseVersion=0.1-SNAPSHOT resolvedVersion=0.1-20171129.222952-1
HTTP/1.1 200 OK
Content-Length: 120
Content-Type: application/json
Date: Thu, 30 Nov 2017 20:50:42 GMT

{
    "artifactId": "test", 
    "baseVersion": "0.1-SNAPSHOT", 
    "groupId": "com.test", 
    "id": 1, 
    "resolvedVersion": "0.1-20171129.222952-1"
}

# delta should return the snapshot we reported 
➜  ~ http localhost:8080/accelerator/snapshots/delta offset==0                                                                                     
HTTP/1.1 200 OK
Content-Length: 166
Content-Type: application/json
Date: Thu, 30 Nov 2017 20:50:56 GMT
Vary: Accept-Encoding

{
    "hasMore": false, 
    "nextOffset": 1, 
    "versions": [
        {
            "artifactId": "test", 
            "baseVersion": "0.1-SNAPSHOT", 
            "groupId": "com.test", 
            "id": 1, 
            "resolvedVersion": "0.1-20171129.222952-1"
        }
    ]
}

# delta with an offset of 1 should not return any snapshots
➜  ~ http localhost:8080/accelerator/snapshots/delta offset==1                                                                                     
HTTP/1.1 200 OK
Content-Length: 46
Content-Type: application/json
Date: Thu, 30 Nov 2017 20:51:09 GMT
Vary: Accept-Encoding

{
    "hasMore": false, 
    "nextOffset": 1, 
    "versions": []
}
```

#### Setting up the schema

For convenience, the Dropwizard testing configuration tells the app to initialize the schema itself ([here](https://github.com/HubSpot/maven-snapshot-accelerator/blob/fa6decbf7dcca3dfeef00727580a7e9b51bfb790/accelerator-api/src/test/resources/test.yaml#L12)). You can use this same flag for a real deployment, but to do so the API would need to connect to the database as a user with DDL permissions. Instead, it may be preferable to set up the database schema before running the API. The expected schema (found [here](https://github.com/HubSpot/maven-snapshot-accelerator/blob/master/accelerator-api/src/main/resources/schema.sql)) is pretty simple, just a single table with 5 columns. You can initialize this with Liquibase or just create the table manually.

## Running the Maven plugin

Now that the API is running, you'll want to add the accelerator Maven plugin to your CI builds so that the API gets notified of new snapshot versions. You can add a call to the plugin after the deploy step, to make sure that the publish has succeeded and that the resolved snapshot version is available. You'll also need to pass the base URL of the API as an environment variable or system property so that the plugin knows where to report to. You can do this by creating a `~/.mavenrc` file (which Maven sources before running the build) with the following contents:
```bash
#!/bin/bash

export ACCELERATOR_URL='https://myapidomain.com/accelerator'
```

And then you can add the plugin to your CI script, for example:
```bash
mvn -B deploy com.hubspot.snapshots:accelerator-maven-plugin:0.3:report
```

By default, failure to notify the API will not fail the build. If you want to change this behavior, you can add `-Daccelerator.failOnError=true` to the Maven arguments.

## Using the Maven extension

Now that the API is running and getting notified of new snapshot versions, the last step is to use the accelerator Maven extension. The extension will hit the API at the start of a Maven build to find out about any new snapshot versions. It keeps track of the API offset (so it only needs to fetch a delta) and the latest version of each snapshot via metadata files stored in your local Maven repository. To install the extension, you just need to download it and copy it to your Maven extensions folder:

```bash
curl -L -O https://repo1.maven.org/maven2/com/hubspot/snapshots/accelerator-maven-extension/0.3/accelerator-maven-extension-0.3-shaded.jar
mv accelerator-maven-extension-0.3-shaded.jar $M2_HOME/lib/ext
```

Similar to the plugin install, you'll need to set an environment variable or system property that points at the API. You can achieve this using the same `~/.mavenrc` approach. If everything is set up properly, you should see a message like this printed at the start of your next Maven build:
```
[INFO] Accelerator is healthy, will skip snapshot checks based on accelerator metadata
```

### IDE Compatibility

For this to work in your IDE, make sure the IDE is set to use the same Maven install where you copied the extension JAR. Unfortunately, if you're using IntelliJ, there's a bit more work to do because it doesn't load extension JARs and there's no way we've found to make it do so (feel free to leave feedback on [this](https://youtrack.jetbrains.com/issue/IDEA-135229#comment=27-2481665) issue if you want to see this changed). To get around this, you need to copy the extension JAR to the lib folder. But now the problem is that there's no way to make our JAR come first on the classpath, and if it doesn't then the extension won't work. To get around this problem, we also need to replace the maven-resolver-impl JAR with a modified one that doesn't contain `org.eclipse.aether.internal.impl.DefaultUpdateCheckManager` (the class we override in the extension). We wrote a hacky script (available [here](https://gist.github.com/jhaber/55c0dbcb5d9aa59d53debc70123a2a1e)) to take care of this (quit IntelliJ before running that script, and re-open after it's done).
