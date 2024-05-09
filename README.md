# ONGuard

The ONGuard (OSV - NVD Guard) service integrates OSV and NVD in order to retrieve CVE vulnerabilities from
the given set of package urls (purls).

Upon receiving a collection of `purls`:

```json
{
    "purls": [
        "pkg:maven/io.quarkus/quarkus-vertx-http@2.13.5.Final?type=jar",
        "pkg:maven/io.quarkus/quarkus-core@2.13.5.Final?type=jar"
    ]
}
```

The service will query the OSV REST API to retrieve the vulnerabilities associated to each of these `purls` and
the OSV Service will return with a collection of vulnerabilities.

```json
{
    "results": [
        {
            "vulns": [
                {
                    "id":  "GHSA-4f4r-wgv2-jjvg",
                    "modified": "2023-12-06T03:15:58.438241Z"
                },
                {
                    "id": "GHSA-c57v-hc7m-8px2",
                    "modified": "2023-04-11T01:26:48.949735Z"
                }
            ]
        },
        {
            "vulns": [

            ]
        }
    ]
}
```

We need to expand each of these vulnerability IDs and for that the service will call retrieve the OSV data
that contains useful remediation information, aliases (including the CVE), summary and title but we also need to
retrieve the CVSS metrics and this data is retrieved from the NVD service.

That means the service has to perform 2 requests:
- OSV /vulns/{vulnId}
- NVD /rest/json/cves/2.0?cveId={cveId}

As you can imagine. That implies (2 * number of vulnerabilities) + 1 requests for each request. Where in a normal-sized
Java project with 150 dependencies (direct and transitive) will be 201 HTTP requests.

For that we have added a Redis cache in order to save the aggregated data and also to cache the requests to OSV.

The final result of the aggregated data will look like this:

```json
{
    "pkg:maven/io.quarkus/quarkus-vertx-http@2.13.5.Final?type=jar": [
        {
            "cveId": "CVE-2023-4853",
            "created": "2024-02-02T06:56:20.584+00:00",
            "summary": "Quarkus HTTP vulnerable to incorrect evaluation of permissions",
            "description": "A flaw was found in Quarkus where HTTP security ...",
            "affected": [
                {
                    "package": {},
                    "ranges": [],
                    "versions": []
                }
            ],
            "severity": [
                {
                    "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N",
                    "type": "CVSS_V3"
                }
            ]
        }
    ]
}
```

## Endpoints

The ONGuard service provides the following service endpoints:

### POST /purls

This endpoint resolves the package urls into public CVEs.

### GET /vulnerabilities/{vulnerabilityID}

Retrieves the public CVE data for the given Vulnerability ID. If this ID is not a CVE (e.g. GHSA) the associated CVE will be returned, if exists.

### POST /vulnerabilities

Retrieves the public CVE data for the given Vulnerability IDs in JSON format.

```json
[
    "vulnId-1",
    "vulnId-2"
]
```

## OpenAPI Schema

The OpenAPI Schema can be retrieved in the management endpoint at http://localhost:9000/q/openapi

## Running the application

### Required parameters

The NVD database has a rate limit that is more permissive when using an api Key. You can get one by filling in [this form](https://nvd.nist.gov/developers/request-an-api-key)

Once you apply and receive it you can configure it with the following configuration parameter: `api.nvd.key=<your_api_key>`


### Running the application locally

The application depends on Redis and uses the JSON capability. You can either connect to an existing instance or use the `TestContainers` framework to spin up one for you.

#### Using Redis with TestsContainers

In this case, as it is the default configuration, you only need to provide the apiKey.

```shell script
./mvnw compile quarkus:dev -Dapi.nvd.key=<your_key>
```

* Note: If you're having issues with Podman and TestContainers you can check the [Quarkus Blog](https://quarkus.io/blog/quarkus-devservices-testcontainers-podman/) and the [Quarkus Podman guide](https://quarkus.io/guides/podman)

#### Connecting to an existing Redis database

In this case I will use podman to start an instance. Note that I use the `redis-stack` instance because it contains the JSON capability. I also expose the port 8001 for connecting to the Redis Insights instance.

```bash
podman run -d --rm -p 6379:6379 -p 8001:8001 --name redis-stack redis/redis-stack:latest
```

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev -Dapi.nvd.key=<your_key> -Dquarkus.redis.hosts=redis://localhost:6379/
```

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/onguard-<version>-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.
