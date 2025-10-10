FROM python:3.14-slim-bookworm
ENV JAVA_HOME=/opt/java/openjdk

COPY --from=eclipse-temurin:21 $JAVA_HOME $JAVA_HOME
COPY --from=ghcr.io/mobilitydata/gtfs-validator:5.0.1 /gtfs-validator-cli.jar /gtfs-validator-cli.jar

### copy of ENVs from merged images
# python:3.11-slim-bullseye
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION 3.14
# eclipse-temurin:21
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

ENTRYPOINT ["python", "app.py", "--rule-name", "gtfs.canonical"]
