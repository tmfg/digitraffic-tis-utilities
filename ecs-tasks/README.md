###### Digitraffic / Travel Information Services

# ECS Tasks

Collection of containerized tasks to be run in ECS.

## VACO External Rules

External rules are SQS triggered VACO rules of any type (_validation, conversion, syntax, logic etc._) which package
together external rule runners with some control logic - usually in form of a Python script - to handle the input
and output of each rule.

The packaged rules are

 - [`gtfs-canonical-4.1.0`](gtfs-canonical-4.1.0) [Mobility Data's GTFS Canonical GTFS Schedule Validator](https://github.com/MobilityData/gtfs-validator/)
   ```shell
   docker build -t gtfs-canonical .
   docker run -v ./data:/data -it gtfs-canonical -i '/data' -o '/data/output'
   ```

### Development Pointers

 - When composing Dockerfile from multiple other images, the base images **must** match!
 - ENV variables most likely need to be redefined to re-enable the copied tools

## Building and Running the Image
```shell
```

---

Copyright Fintraffic 2023. Licensed under the EUPL-1.2 or later.

