#!/usr/bin/env bash
# Render's Node runtime build step (`npm ci && npm run db:migrate`): fetch a JDK into .jdk/,
# build the Spring Boot jar, then apply database migrations (also applied again on start).
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -x .jdk/bin/java ]; then
  echo "Downloading Temurin JDK 21..."
  rm -rf .jdk && mkdir -p .jdk
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    | tar xz -C .jdk --strip-components=1
fi
export JAVA_HOME="$PWD/.jdk"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx384m}"

./mvnw -q -B -DskipTests package

if [ -n "${DATABASE_URL:-}" ]; then
  "$JAVA_HOME/bin/java" -Xmx256m -jar target/talkcare-api.jar migrate
fi
