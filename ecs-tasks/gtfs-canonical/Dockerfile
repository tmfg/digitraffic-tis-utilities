FROM python:3.14.2-slim-bookworm AS app
ENV JAVA_HOME=/opt/java/openjdk

COPY --from=eclipse-temurin:21.0.9_10-jre-noble $JAVA_HOME $JAVA_HOME
COPY --from=ghcr.io/mobilitydata/gtfs-validator:5.0.1 /gtfs-validator-cli.jar /gtfs-validator-cli.jar

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
FROM aquasec/trivy:latest AS sbom-generator
COPY --from=app / /scanroot
RUN trivy rootfs --format cyclonedx --output /bom.json /scanroot

# Embed SBOM
FROM app
ARG GITHUB_SHA
COPY --from=sbom-generator /bom.json /sbom/${GITHUB_SHA}.json

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs.canonical"]
