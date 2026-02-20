FROM maven:3.9.12-eclipse-temurin-21-noble AS build
ARG GITHUB_SHA
COPY ./netex-entur /
RUN mvn package -DskipTests -Dsbom.filename=${GITHUB_SHA}

FROM python:3.14.2-slim-bookworm AS app
ENV JAVA_HOME=/opt/java/openjdk
COPY --from=build /target/validation-netex-entur-jar-with-dependencies.jar /validation-netex-entur.jar
COPY --from=eclipse-temurin:21.0.9_10-jre-noble $JAVA_HOME $JAVA_HOME

### copy of ENVs from merged images

ENV PATH=/usr/local/bin:$PATH
ENV PYTHON_VERSION=3.14
# eclipse-temurin:21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# both
ENV LANG=C.UTF-8

USER root
WORKDIR /
# add generic wrapper
ADD ./wrapper /
# add rule specific override, will override parts of wrapper
ADD ./netex-entur/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

RUN mkdir -p /app/tmp

# Trivy rootfs scan
FROM aquasec/trivy:0.69.1 AS sbom-generator
COPY --from=app / /scanroot
RUN trivy rootfs --format cyclonedx --output /trivy-bom.json /scanroot

# Merge Maven SBOM + Trivy SBOM
FROM --platform=linux/amd64 cyclonedx/cyclonedx-cli:0.30.0 AS sbom-merger
ARG GITHUB_SHA
COPY --from=build /target/sbom/${GITHUB_SHA}.json /maven-bom.json
COPY --from=sbom-generator /trivy-bom.json /trivy-bom.json
RUN cyclonedx merge \
    --input-files /maven-bom.json /trivy-bom.json \
    --output-file /merged-bom.json \
    --output-version v1_6

FROM app
ARG GITHUB_SHA
COPY --from=sbom-merger /merged-bom.json /sbom/${GITHUB_SHA}.json

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "netex.entur"]
