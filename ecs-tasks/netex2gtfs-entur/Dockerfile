FROM maven:3.9.11-eclipse-temurin-21-noble AS build
COPY ./netex2gtfs-entur /
RUN mvn package -DskipTests

FROM debian:bookworm-slim
COPY --from=python:3.14-slim-bookworm / /
COPY --from=openjdk:21-slim-bookworm / /
COPY --from=build /target/conversion-netex2gtfs-entur-jar-with-dependencies.jar /conversion-netex2gtfs-entur.jar

### copy of ENVs from merged images
# python:3.14-slim-bookworm
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION 3.14
# openjdk:21-slim-bookworm
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
ADD ./netex2gtfs-entur/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

ENTRYPOINT ["python", "app.py", "--rule-name", "netex2gtfs.entur"]
