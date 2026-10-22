# Dependabot updates only images named in FROM lines, so each merged image has its own stage
FROM eclipse-temurin:21.0.9_10-jre-noble AS java
FROM ghcr.io/mobilitydata/gtfs-validator:8.0.1 AS gtfs-validator

FROM python:3.14.6-slim-bookworm AS app
ENV JAVA_HOME=/opt/java/openjdk

COPY --from=java $JAVA_HOME $JAVA_HOME
COPY --from=gtfs-validator /gtfs-validator-cli.jar /gtfs-validator-cli.jar

### copy of ENVs from merged images
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION 3.14
ENV PATH="${JAVA_HOME}/bin:${PATH}"
# both
ENV LANG=C.UTF-8

USER root
WORKDIR /
# add generic wrapper
ADD ./wrapper /
# add rule specific override, will override parts of wrapper
ADD ./gtfs-canonical/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

RUN mkdir -p /app/tmp

# Generate SBOM
FROM aquasec/trivy:0.71.0 AS sbom-generator
COPY --from=app / /scanroot
RUN trivy rootfs --format cyclonedx --output /bom.json /scanroot

# Embed SBOM
FROM app
ARG GITHUB_SHA
COPY --from=sbom-generator /bom.json /sbom/${GITHUB_SHA}.json

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs.canonical"]
