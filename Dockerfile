FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-26

ENV TZ="Europe/Oslo"
ENV JAVA_OPTS='-XX:MaxRAMPercentage=90'

WORKDIR /app

COPY build/libs/*.jar /app/

RUN echo "=== Inside container ===" \
 && ls -l /app/ \
 && find /app/ -type f -exec stat -c "%n %a %A" {} \;

CMD ["-jar", "app.jar"]
ARG BYGD_PA_NY='2026-06-29T08:36:00'
