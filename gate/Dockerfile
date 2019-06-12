# syntax=docker/dockerfile:experimental
FROM gradle:jdk8-alpine as builder

WORKDIR /home/gradle/src
RUN ls -la /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
#mount=type=cache doesn't support chown yet, so use root
USER root
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon :gate:installDist

FROM openjdk:8-jre-alpine
EXPOSE 8080
COPY --from=builder /home/gradle/src/gate/build/install/gate /app/
WORKDIR /app
RUN chmod +x bin/gate

ENTRYPOINT "bin/gate"