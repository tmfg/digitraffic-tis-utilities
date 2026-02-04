FROM alpine:3.23

COPY --from=python:3.14.2-alpine3.23 / /
COPY --from=node:22.22.0-alpine3.23 / /
COPY --from=ghcr.io/tmfg/digitraffic-tis-rules-gtfs2netex:0.106.0 /usr/src/app/ /

### copy of ENVs from merged images
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV PATH=/usr/local/bin:$PATH
ENV PYTHON_VERSION=3.14.2
ENV PYTHON_PIP_VERSION=25.2
ENV NODE_VERSION=22.22.0
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
