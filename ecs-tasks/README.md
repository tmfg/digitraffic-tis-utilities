###### Digitraffic / Travel Information Services

# ECS Tasks

Collection of containerized tasks to be run in ECS.

## VACO External Rules

External rules are SQS triggered VACO rules of any type (_validation, conversion, syntax, logic etc._) which package
together external rule runners with some control logic - usually in form of a Python script - to handle the input
and output of each rule.

The packaged rules are

 - [`gtfs-canonical-4.1.0`](gtfs-canonical-v4.1.0) [Mobility Data's GTFS Canonical GTFS Schedule Validator](https://github.com/MobilityData/gtfs-validator/)

## Running a rule

Use provided Justfile:
```shell
just run <rulename>
```
for example:
```shell
just run gtfs-canonical-v4.1.0
```

## Development Pointers

 - When composing Dockerfile from multiple other images, the base images **must** match!
 - ENV variables most likely need to be redefined to re-enable the copied tools

## AWS localstack configuration

```shell
LOCALSTACK_ENDPOINT_URL="http://localhost:4566"

➜  ~ aws configure --profile localstack
AWS Access Key ID [None]: test
AWS Secret Access Key [None]: test
Default region name [None]: eu-north-1
Default output format [None]:
```

---

Copyright Fintraffic 2023-2024. Licensed under the EUPL-1.2 or later.

