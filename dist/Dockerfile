# The container image of the esj command line: one invoice per process, a heap
# ceiling from the environment, and no account with more rights than reading the
# document it is given. Every release publishes it as ghcr.io/bsnsoft/esj, for
# linux/amd64 and linux/arm64 (docs/install.md). Build it from the root of the
# repository with the jar already built (dist/package.sh docker does both):
#
#   mvn -B -DskipTests package -pl esj-cli -am
#   docker build -f dist/Dockerfile -t esj:0.9.0 .
#   docker run --rm -i --read-only --memory 1g -v "$PWD:/work:ro" esj:0.9.0 \
#       validate /work/invoice.xml
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

# The ahead-of-time cache is recorded on the runtime it will be used on, by a
# run that does what the tool is for. A cache from another build of the virtual
# machine is ignored, so it is made here rather than shipped in.
FROM eclipse-temurin:25-jre AS training
COPY esj-cli/target/esj.jar /opt/esj/esj.jar
COPY conformance/pdf/factur-x.pdf /opt/esj/training.pdf
RUN java -XX:+UseCompactObjectHeaders -XX:AOTCacheOutput=/opt/esj/esj.aot \
      -jar /opt/esj/esj.jar validate /opt/esj/training.pdf > /dev/null

FROM eclipse-temurin:25-jre
# The base image labels itself, and its version and build date would be read as
# this image's own, so both are set here from what dist/package.sh passes. The
# source names the repository the image is built from, which is what ghcr.io
# links the package to.
ARG ESJ_VERSION=unknown
ARG ESJ_CREATED=unknown
ARG ESJ_REVISION=unknown
LABEL org.opencontainers.image.title="esj" \
      org.opencontainers.image.description="EN 16931 invoices through the semantic model" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="$ESJ_VERSION" \
      org.opencontainers.image.created="$ESJ_CREATED" \
      org.opencontainers.image.revision="$ESJ_REVISION" \
      org.opencontainers.image.source="https://github.com/bsnsoft/esj" \
      org.opencontainers.image.documentation="https://github.com/bsnsoft/esj/blob/main/docs/install.md#container-image"
COPY --from=training /opt/esj/esj.jar /opt/esj/esj.jar
COPY --from=training /opt/esj/esj.aot /opt/esj/esj.aot
COPY dist/docker-entrypoint.sh /opt/esj/entrypoint.sh
COPY LICENSE NOTICE /opt/esj/
RUN chmod 0555 /opt/esj/entrypoint.sh \
    && useradd --uid 10001 --create-home --shell /usr/sbin/nologin esj
USER 10001
WORKDIR /work
ENV ESJ_MAX_HEAP=512m
ENTRYPOINT ["/opt/esj/entrypoint.sh"]
CMD ["--help"]
