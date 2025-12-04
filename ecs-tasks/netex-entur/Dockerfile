FROM maven:3.9.11-eclipse-temurin-21-noble AS build
COPY ./netex-entur /
RUN mvn package -DskipTests

FROM python:3.14-slim-bookworm
ENV JAVA_HOME=/opt/java/openjdk
COPY --from=build /target/validation-netex-entur-jar-with-dependencies.jar /validation-netex-entur.jar
COPY --from=eclipse-temurin:21 $JAVA_HOME $JAVA_HOME

### copy of ENVs from merged images
# python:3.14-slim-bookworm
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

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "netex.entur"]
