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
