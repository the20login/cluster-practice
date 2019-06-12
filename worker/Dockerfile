# syntax=docker/dockerfile:experimental
FROM gradle:jdk8-alpine as builder

WORKDIR /home/gradle/src
RUN ls -la /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
#mount=type=cache doesn't support chown yet, so use root
USER root
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon :worker:installDist

FROM openjdk:8-jre
EXPOSE 8080
ENV JAVA_OPTS "-Dsun.net.inetaddr.ttl=0 -Dnetworkaddress.cache.ttl=10 -Dnetworkaddress.cache.negative.ttl=0"
COPY --from=builder /home/gradle/src/worker/build/install/worker /app/
WORKDIR /app
RUN chmod +x bin/worker

ENTRYPOINT "bin/worker"