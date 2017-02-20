FROM registry.neec.xyz/neeco/scala:2.12.0
RUN apk add --no-cache --update mariadb-dev openjdk8
WORKDIR /app
COPY . /app
RUN sbt clean compile stage && apk del openjdk8
CMD ["target/universal/stage/bin/caja -Dpidfile.path=/dev/null"]
ARG REVISION
LABEL revision=$REVISION maintainer="Nee-co"
