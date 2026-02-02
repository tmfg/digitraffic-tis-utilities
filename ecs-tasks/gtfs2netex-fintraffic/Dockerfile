FROM alpine:3.22

COPY --from=python:3.13.8-alpine3.22 / /
COPY --from=node:22-alpine3.22 / /
COPY --from=ghcr.io/tmfg/digitraffic-tis-rules-gtfs2netex:0.106.0 /usr/src/app/ /

### copy of ENVs from merged images
# python:3.13.8-alpine3.22
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV PATH=/usr/local/bin:$PATH
ENV PYTHON_VERSION=3.13.8
ENV PYTHON_PIP_VERSION=25.2
# node:22-alpine3.22
ENV NODE_VERSION=20.20.0
ENV YARN_VERSION=1.22.22
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

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs2netex.fintraffic"]
