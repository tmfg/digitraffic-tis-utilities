FROM python:3.14.2-slim-bookworm AS app
ENV JAVA_HOME=/opt/java/openjdk

COPY --from=eclipse-temurin:26-jre-noble $JAVA_HOME $JAVA_HOME
COPY --from=ghcr.io/tmfg/digitraffic-tis-rules-ssim2netex /app/digitraffic-tis-rules-ssim2netex.jar /digitraffic-tis-rules-ssim2netex.jar
# For locally built image of digitraffic-tis-rules-ssim2netex, use this instead:
#COPY --from=digitraffic-tis-rules-ssim2netex /app/digitraffic-tis-rules-ssim2netex.jar /digitraffic-tis-rules-ssim2netex.jar

### copy of ENVs from merged images
ENV PATH /usr/local/bin:$PATH
ENV PYTHON_VERSION 3.14
ENV PATH="${JAVA_HOME}/bin:${PATH}"
# both
ENV LANG=C.UTF-8

USER root
WORKDIR /
# add generic wrapper
ADD ./wrapper /
# add rule specific override, will override parts of wrapper
ADD ./ssim2netex-fintraffic/rule.py /
RUN pip install --trusted-host pypi.python.org -r requirements.txt

RUN mkdir -p /app/tmp

VOLUME ["/app/tmp"]

ENTRYPOINT ["python", "app.py", "--rule-name", "ssim2netex.fintraffic"]
