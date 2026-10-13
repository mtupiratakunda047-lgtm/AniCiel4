#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="${GRADLE_VERSION:-8.9}"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/aniciel-bootstrap/$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME_DIR/gradle-$GRADLE_VERSION/bin/gradle"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
DIST_ZIP="$GRADLE_HOME_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [ -x "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

mkdir -p "$GRADLE_HOME_DIR"
if [ ! -f "$DIST_ZIP" ]; then
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$DIST_URL" -o "$DIST_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$DIST_ZIP" "$DIST_URL"
  else
    echo "Gradle is not installed and curl/wget is unavailable." >&2
    echo "In Termux run: pkg install gradle" >&2
    exit 127
  fi
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "unzip is required to bootstrap Gradle. In Termux run: pkg install unzip" >&2
  exit 127
fi

rm -rf "$GRADLE_HOME_DIR/gradle-$GRADLE_VERSION"
unzip -q "$DIST_ZIP" -d "$GRADLE_HOME_DIR"
exec "$GRADLE_BIN" "$@"
