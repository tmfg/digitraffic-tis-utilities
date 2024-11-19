###### Digitraffic / Travel Information Services

# ECS Tasks

Collection of containerized tasks to be run in [AWS ECS][aws-ecs].

## VACO External Rules

External rules are [AWS SQS][aws-sqs] triggered [TIS VACO][ft-tis-vaco] rules  which package together external rule
runners with some control logic - usually in form of a Python script - to handle the input and output of each rule.

More practically, each _rule_ is a standalone tool, module or an executable for a specific task, such as converting data
between formats, running validations, downloading additional files etc.


The packaged rules are

 - [`gbfs-entur`](gbfs-entur) [Entur's GBFS Validator](https://github.com/entur/gbfs-validator-java)
 - [`gtfs-canonical`](gtfs-canonical) [Mobility Data's GTFS Canonical GTFS Schedule Validator](https://github.com/MobilityData/gtfs-validator/)
 - [`gtfs2netex-fintraffic`](gtfs2netex-fintraffic) [Fintraffic's GTFS to NeTEx Converter](https://github.com/tmfg/digitraffic-tis-rules-gtfs2netex)
 - [`netex2gtfs-entur`](netex2gtfs-entur) [Entur's NeTEx to GTFS Converter](https://github.com/entur/netex-gtfs-converter-java)
 - [`netex-entur`](netex-entur) [Entur's NeTEx Validator](https://github.com/entur/netex-validator-java)

## Running a rule

Use the provided Justfile:
```shell
just run <rulename>
```
for example:
```shell
just run gtfs-canonical
```

## Development Pointers

 - When composing Dockerfile from multiple other images, the base images **must** match!
 - ENV variables most likely need to be redefined to re-enable the copied tools

## AWS localstack configuration

The rule runner supports [Localstack](https://www.localstack.cloud/) as runtime environment to make executing the rules
simple. The recommended way is to reuse [vaco's](https://github.com/tmfg/digitraffic-tis-vaco) development environment.

To enable this, you need to do do the following one-time configuration:

```shell
➜  ~ aws configure --profile localstack
AWS Access Key ID [None]: test
AWS Secret Access Key [None]: test
Default region name [None]: eu-north-1
Default output format [None]:
```

---

Copyright Fintraffic 2023-2024. Licensed under the EUPL-1.2 or later.

[aws-ecs]: https://aws.amazon.com/ecs/
[aws-sqs]: https://aws.amazon.com/sqs/
[ft-tis-vaco]: https://www.fintraffic.fi/fi/digitaalisetpalvelut/fintrafficin-datapalvelut/liikkumisen-tietopalvelut/validointi-ja-konvertointipalvelu
