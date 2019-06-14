# syntax=docker/dockerfile:experimental
FROM gradle:jdk8-alpine as builder

WORKDIR /home/gradle/src
RUN ls -la /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
#mount=type=cache doesn't support chown yet, so use root
USER root
ARG REVISION_ARG
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon :worker:installDist -Prevision=$REVISION_ARG

FROM openjdk:8-jre
EXPOSE 8080

COPY --from=builder /home/gradle/src/worker/build/install/worker /app/
WORKDIR /app
RUN chmod +x bin/worker

ENTRYPOINT "bin/worker"