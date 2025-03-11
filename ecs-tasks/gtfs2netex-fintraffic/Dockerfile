FROM alpine:3.19

COPY --from=python:3.11.9-alpine3.19 / /
COPY --from=node:20-alpine3.19 / /
COPY --from=ghcr.io/tmfg/digitraffic-tis-rules-gtfs2netex:0.64.0 /usr/src/app/ /

### copy of ENVs from merged images
# python:3.11.9-alpine3.19
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION=3.11.9
ENV PYTHON_PIP_VERSION=24.0
# node:20-alpine3.19
ENV NODE_VERSION=20.12.2
ENV YARN_VERSION=1.22.19
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

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs2netex.fintraffic"]
