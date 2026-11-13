# Nexus CRM backend

Spring Boot 4 application on Java 25, built with Maven. The design is in
[../docs/backend-technical-design.md](../docs/backend-technical-design.md).

This is the bare skeleton: it starts, serves health checks, and has no
database or business modules yet.

## Requirements

- JDK 25

Maven is not required; the wrapper (`./mvnw`) downloads the right version.

## Commands

| Task | Command |
| --- | --- |
| Run locally (port 8080) | `./mvnw spring-boot:run` |
| Run the tests | `./mvnw verify` |
| Build the jar | `./mvnw package` |

## Health checks

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

No other actuator endpoint is exposed over HTTP.

## Layout

Code lives under `com.nexus.crm`. Each business module will be a top-level
package beneath it (`com.nexus.crm.leads`, and so on), as the design document
describes.
