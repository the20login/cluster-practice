#FROM golang:1.9.1-alpine3.6 as confd
#
#ARG CONFD_VERSION=0.16.0
#
#ADD https://github.com/kelseyhightower/confd/archive/v${CONFD_VERSION}.tar.gz /tmp/
#
#RUN apk add --no-cache \
#    bzip2 \
#    make && \
#  mkdir -p /go/src/github.com/kelseyhightower/confd && \
#  cd /go/src/github.com/kelseyhightower/confd && \
#  tar --strip-components=1 -zxf /tmp/v${CONFD_VERSION}.tar.gz && \
#  GOOS=linux GOARCH=amd64 go install github.com/kelseyhightower/confd && \
#  rm -rf /tmp/v${CONFD_VERSION}.tar.gz

FROM nginx:1.17.0-alpine

ENV ETCD_HOSTNAME etcd
ENV ETCD_PORT 2379

RUN rm /etc/nginx/conf.d/default.conf

#COPY --from=confd /go/bin/confd /usr/local/bin/confd
ADD http://github.com/kelseyhightower/confd/releases/download/v0.16.0/confd-0.16.0-linux-amd64 /usr/local/bin/confd
RUN chmod +x /usr/local/bin/confd

ADD nginx.toml /etc/confd/conf.d/nginx.toml

ADD nginx.tmpl /etc/confd/templates/nginx.tmpl
ADD default.conf /etc/nginx/conf.d/cluster.conf

ADD confd-watch /usr/local/bin/confd-watch
ADD entrypoint /usr/local/bin/entrypoint
RUN chmod +x /usr/local/bin/confd-watch
RUN chmod +x /usr/local/bin/entrypoint

EXPOSE 80
EXPOSE 8080

CMD /usr/local/bin/entrypoint