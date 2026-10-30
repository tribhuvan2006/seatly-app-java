#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven 3.8+ is required. Install Maven, then run this script again." >&2
  exit 1
fi
exec mvn spring-boot:run
