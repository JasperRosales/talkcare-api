#!/usr/bin/env bash
# Render's Node runtime start step (`npm start`): run the jar with the JDK fetched at build time.
set -euo pipefail
cd "$(dirname "$0")/.."
exec .jdk/bin/java -XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -jar target/talkcare-api.jar
