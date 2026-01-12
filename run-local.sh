#!/usr/bin/env bash
set -e

set -a
. .env
set +a

./gradlew clean bootRun
