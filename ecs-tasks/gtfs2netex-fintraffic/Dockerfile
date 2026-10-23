# Dependabot updates only images named in FROM lines, so each merged image has its own stage
FROM python:3.14.2-alpine3.23 AS python
FROM node:24.22.1-alpine3.23 AS node

FROM alpine:3.23 AS app

COPY --from=python / /
COPY --from=node / /
COPY --from=ghcr.io/tmfg/digitraffic-tis-rules-gtfs2netex:0.136.0 /usr/src/app/ /

### copy of ENVs from merged images
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV PATH=/usr/local/bin:$PATH
# both
ENV LANG=C.UTF-8

USER root
WORKDIR /
# add generic wrapper
ADD ./wrapper /
# add rule specific override, will override parts of wrapper
ADD ./gtfs2netex-fintraffic/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

RUN apk add bash \
    && apk cache clean

RUN mkdir -p /app/tmp

# Generate SBOM
FROM aquasec/trivy:0.75.0 AS sbom-generator
COPY --from=app / /scanroot
RUN trivy rootfs --format cyclonedx --output /bom.json /scanroot

# Embed SBOM
FROM app
ARG GITHUB_SHA
COPY --from=sbom-generator /bom.json /sbom/${GITHUB_SHA}.json

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs2netex.fintraffic"]
