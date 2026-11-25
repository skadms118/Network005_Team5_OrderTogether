#!/bin/sh
# Order Together build (macOS / Linux / Git Bash)
# Compiles the entry files below and every class they use into out/
# Usage: sh build.sh   (works from any folder; it moves to the repository folder)

cd "$(dirname "$0")" || exit 1

# ---- Settings: keep in sync with build.cmd ----
RELEASE=21
ENTRIES="src/server/ServerMain.java src/client/ClientMain.java"
# -----------------------------------------------

SOURCES=""
for f in $ENTRIES; do
    if [ -f "$f" ]; then
        SOURCES="$SOURCES $f"
    else
        echo "skip: $f (not found)"
    fi
done
if [ -z "$SOURCES" ]; then
    echo "No entry files to build yet."
    exit 1
fi

rm -rf out
# $SOURCES is intentionally unquoted: one argument per file
if ! javac -encoding UTF-8 --release "$RELEASE" -d out -sourcepath src $SOURCES; then
    echo "Build failed."
    exit 1
fi
echo "Build OK: out/"
