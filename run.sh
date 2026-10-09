#!/bin/sh
# usage: ./run.sh [gui|demo|test]
cd "$(dirname "$0")" && mkdir -p out && javac -d out $(find src -name '*.java') || exit 1
case "${1:-gui}" in
  gui)  java -cp out weather.Main ;;
  demo) java -cp out weather.Main --demo "${2:-5}" ;;
  test) java -cp out weather.Tests ;;
esac
