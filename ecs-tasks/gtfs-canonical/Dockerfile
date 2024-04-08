FROM debian:bookworm-slim
COPY --from=python:3.11-slim-bookworm / /
COPY --from=openjdk:21-slim-bookworm / /
COPY --from=ghcr.io/mobilitydata/gtfs-validator:4.2.0 /gtfs-validator-cli.jar /gtfs-validator-cli.jar

### copy of ENVs from merged images
# python:3.11-slim-bullseye
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION 3.11.5
# openjdk:21-slim-bullseye
ENV PATH=/usr/local/openjdk-21/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV JAVA_VERSION=21
ENV JAVA_HOME=/usr/local/openjdk-21
# both
ENV LANG=C.UTF-8

USER root
WORKDIR /
# add generic wrapper
ADD ./wrapper /
# add rule specific override, will override parts of wrapper
ADD ./gtfs-canonical/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs.canonical"]
