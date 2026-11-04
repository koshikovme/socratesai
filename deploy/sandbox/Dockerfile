# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM ruby:3.4-slim-bookworm@sha256:a0636a941ef86063f17b343f6c96f271f8eeedd76febf035bb6d57bc5024682b AS base
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates g++ libcap2 libpq5 sudo libbz2-1.0 libexpat1 libffi8 libgdbm6 \
    libgdbm-compat4 liblzma5 libncursesw6 libreadline8 libsqlite3-0 libssl3 libuuid1 \
    zlib1g libfontconfig1 libfreetype6 && rm -rf /var/lib/apt/lists/*

FROM base AS build
RUN apt-get update && apt-get install -y --no-install-recommends make libcap-dev libpq-dev \
    && rm -rf /var/lib/apt/lists/*
ADD --checksum=sha256:d9cb06071b38abd3cca0e17d4dd218cbb3cada23200a1bb369fe6b01a6c0d38d \
    https://codeload.github.com/judge0/isolate/tar.gz/ad39cc4d0fbb577fb545910095c9da5ef8fc9a1a /tmp/isolate.tar.gz
RUN mkdir /tmp/isolate && tar -xzf /tmp/isolate.tar.gz -C /tmp/isolate --strip-components=1 \
    && sed -i '/^CFLAGS=/ s/$/ -O2 -D_FORTIFY_SOURCE=3 -fstack-protector-strong -fPIE/' /tmp/isolate/Makefile \
    && make -C /tmp/isolate isolate \
       LDFLAGS='-pie -Wl,-z,relro,-z,now' BUILD_DATE=2026-11-05 BUILD_COMMIT=ad39cc4d \
    && make -C /tmp/isolate install DESTDIR=/opt/isolate-install
WORKDIR /api
COPY Gemfile.runtime Gemfile
COPY Gemfile.runtime.lock Gemfile.lock
ENV BUNDLE_FROZEN=true BUNDLE_WITHOUT=development:test
RUN bundle install --jobs=2 --retry=2 && rm -rf /usr/local/bundle/cache

FROM eclipse-temurin:21-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69 AS java
FROM python:3.12-slim-bookworm@sha256:e697c81897302a4c4333bade000d98038fbb21eb064f8ee1fd42d02ebcd9d337 AS python

FROM base AS runtime
WORKDIR /api
COPY --from=build /usr/local/bundle /usr/local/bundle
COPY --from=build /opt/isolate-install/usr/local/ /usr/local/
COPY --from=java /opt/java/openjdk /usr/local/java21
COPY --from=python /usr/local/bin/python3.12 /usr/local/bin/python3.12
COPY --from=python /usr/local/lib/libpython3.12.so.1.0 /usr/local/lib/
COPY --from=python /usr/local/lib/python3.12 /usr/local/lib/python3.12
ADD --checksum=sha256:1ac8b20e1dacd244db53a879e201c887a90f8b718d7e28600f9d7bc58d7772bf \
    https://codeload.github.com/judge0/judge0/tar.gz/d0632cffaaf4fcdbf83a83e7a55ed9baf8a86db6 /tmp/judge0.tar.gz
RUN tar -xzf /tmp/judge0.tar.gz --strip-components=1 -C /api && rm /tmp/judge0.tar.gz
COPY Gemfile.runtime Gemfile
COPY Gemfile.runtime.lock Gemfile.lock
COPY modernize.rb runtime-seeds.rb entrypoint-modern.sh server-modern.sh worker-modern.sh ./
RUN ldconfig && ruby modernize.rb \
    && groupadd --gid 999 judge0 && useradd --uid 1000 --gid 999 --create-home --shell /bin/bash judge0 \
    && printf 'judge0 ALL=(ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/judge0 && chmod 0440 /etc/sudoers.d/judge0 \
    && install -d -m 0755 -o root -g root /var/local/lib/isolate \
    && install -d -m 0755 -o judge0 -g judge0 /api/tmp /api/log \
    && chmod 4755 /usr/local/bin/isolate && chmod 0755 /api/*-modern.sh \
    && sed -i 's/^num_boxes *=.*/num_boxes = 2147483647/' /usr/local/etc/isolate \
    && ruby -rjson -e 'File.write("runtime-versions.json", JSON.generate({python: IO.popen(["/usr/local/bin/python3.12", "--version"]).read.split.last, cpp: IO.popen(["g++", "-dumpfullversion"]).read.strip, java: IO.popen(["/usr/local/java21/bin/java", "-version"], err: [:child,:out]).read[/version "([^"]+)"/,1]}))'
ENV RAILS_ENV=production BUNDLE_FROZEN=true BUNDLE_WITHOUT=development:test \
    JUDGE0_VERSION=1.13.1-socrates-20261105 JUDGE0_HOMEPAGE=https://judge0.com \
    JUDGE0_SOURCE_CODE=https://github.com/judge0/judge0 JUDGE0_MAINTAINER=Judge0 \
    LANG=C.UTF-8
USER 1000:999
EXPOSE 2358
ENTRYPOINT ["/api/entrypoint-modern.sh"]
CMD ["/api/server-modern.sh"]
