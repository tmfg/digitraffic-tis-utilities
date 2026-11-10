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

## Wrapper runtime settings

The shared `wrapper/app.py` reads these optional environment variables. The infrastructure sets both per environment.

 - `IDLE_TIMEOUT_SECONDS`: unset or `0` exits on the first empty receive. A value above `0` long-polls the queue
   (up to 20 s per call) and exits after that many seconds without messages. An invalid value is treated as `0` with a
   warning.
 - `TASK_PROTECTION_MINUTES`: when set and `ECS_AGENT_URI` is set (Fargate), the task enables ECS scale-in protection
   with this expiry while it processes a job. A failed call is logged and the job still runs. Unset means no
   protection calls, as in LocalStack and `just run`.

Regardless of the variables:

 - `/app/tmp` is emptied after each job (`app-tmp` on the host with `just run`).
 - SIGTERM stops receiving. A received but unstarted message is returned to the queue.
 - Each job logs `queue_wait rule=<rule> entryId=<id> wait_ms=<ms>`.

## Wrapper tests

```shell
just test
```

Runs pytest in `wrapper-tests/` inside a virtualenv (`.venv-wrapper-tests`) and needs Python 3.12 or later. The tests
live outside `wrapper/`, so they are not copied into the images.

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
