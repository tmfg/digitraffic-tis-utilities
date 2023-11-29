FROM alpine:3.18

COPY --from=python:3.11.6-alpine3.18 / /
COPY --from=node:19.9.0-alpine / /
COPY --from=ghcr.io/tmfg/digitraffic-tis-rules-gtfs2netex:latest /usr/src/app/ /

### copy of ENVs from merged images
# python:3.11.6-alpine3.18
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION=3.11.6
ENV PYTHON_PIP_VERSION=23.2.1
# node:19.9.0-alpine
ENV NODE_VERSION=19.9.0
ENV YARN_VERSION=1.22.19
# both
ENV LANG=C.UTF-8

USER root
WORKDIR /
# add generic wrapper
ADD ./wrapper /
# add rule specific override, will override parts of wrapper
ADD ./gtfs2netex-fintraffic-v1.0.0/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

RUN apk add bash

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs2netex.fintraffic.v1_0_0"]
