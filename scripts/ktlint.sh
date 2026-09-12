#!/usr/bin/env bash
# Kotlin のスタイル検査 (Android SDK なしで動く)。
#
# Gradle プラグインではなく CLI を使う理由: :app の設定には Android SDK が要るが、
# 検査自体は SDK なしでできる。CI でも手元でも同じものを回せるようにしておく。
#
#   scripts/ktlint.sh        検査だけ
#   scripts/ktlint.sh --fix  自動修正
set -euo pipefail

KTLINT_VERSION="1.5.0"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/lunch-log"
KTLINT_JAR="$CACHE_DIR/ktlint-$KTLINT_VERSION.jar"

if [ ! -f "$KTLINT_JAR" ]; then
  mkdir -p "$CACHE_DIR"
  echo "ktlint $KTLINT_VERSION を取得します..."
  curl -sS -L -o "$KTLINT_JAR" \
    "https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/$KTLINT_VERSION/ktlint-cli-$KTLINT_VERSION-all.jar"
fi

ARGS=()
[ "${1:-}" = "--fix" ] && ARGS+=("-F")

java -jar "$KTLINT_JAR" "${ARGS[@]}" "app/src/**/*.kt" "core/src/**/*.kt"
