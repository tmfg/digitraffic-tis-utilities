FROM maven:3.9.4-eclipse-temurin-17-focal AS build
COPY ./netex-entur-v1.0.1 /
RUN mvn package -DskipTests

FROM debian:bookworm-slim
COPY --from=python:3.11-slim-bookworm / /
COPY --from=openjdk:21-slim-bookworm / /
COPY --from=build /target/validation-netex-entur-1.0.1-jar-with-dependencies.jar /validation-netex-entur.jar

### copy of ENVs from merged images
# python:3.11-slim-bookworm
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION 3.11.5
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
ADD ./netex-entur-v1.0.1/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

ENTRYPOINT ["python", "app.py", "--rule-name", "netex.entur.v1_0_1"]
