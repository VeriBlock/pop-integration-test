# pop-integration-int

This repository contains e2e test framework for POP protocol.

To run all tests: `./gradlew build test`

Each test uses testcontainers to start docker images of NodeCore, APM, BTCSQ (and other stuff) to test everything end-to-end.

## Environment variables

Tests will use either default versions (see [BaseIntegrationTest.kt](./src/main/kotlin/testframework/BaseIntegrationTest.kt)) or specified in following environment variables:

- INT_NODECORE_VERSION - veriblock version (https://hub.docker.com/r/veriblock/nodecore)
- INT_APM_VERSION - APM version (https://hub.docker.com/r/veriblock/altchain-pop-miner)
- INT_BTCSQ_VERSION - BTCSQ version (https://hub.docker.com/r/veriblock/btcsq)
- INT_LOG_LEVEL: "DEBUG"

Example: [ci.yml](./.github/workflows/main.yml)

## Development guide

Each service (APM/NC/BTCSQ) is organized into "wrapper" - [./src/main/kotlin/testframework/wrapper](./src/main/kotlin/testframework/wrapper). 
Each wrapper is expected to provide all necessary tools to start / stop and control wrapped tool via API (HTTP/GRPC...).

Every test defines a "topology" - a set of "wrappers" to run, their relations (for example, APM should be added after NC and BTCSQ).

[ExampleTest.kt](./src/test/kotlin/functional/ExampleTest.kt)