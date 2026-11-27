#!/bin/bash
# Behavioural tests of packaging/linux/build-appimage.sh: it packs an application directory that holds
# what jpackage produces today and refuses one without the launcher or the Core library.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
build="$here/../linux/build-appimage.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

# Stands in for appimagetool: writes the output file it is asked for.
printf '#!/bin/sh\n: > "$2"\n' > "$work/appimagetool"
chmod +x "$work/appimagetool"

app_dir() { # app_dir <name>: an application directory; hev is not in it, SKIPI Core carries it
  mkdir -p "$work/$1/bin" "$work/$1/lib/app/resources"
  printf '#!/bin/sh\n' > "$work/$1/bin/SKIPI"
  chmod +x "$work/$1/bin/SKIPI"
  : > "$work/$1/lib/app/resources/libskipicore.so"
  echo "$work/$1"
}

expect() { # expect <name> <expected-exit> <app-dir>
  local name="$1" want="$2" got
  APPIMAGETOOL="$work/appimagetool" bash "$build" "$3" "$work/out.AppImage" >"$work/log" 2>&1
  got=$?
  if [ "$got" -ne "$want" ]; then
    echo "FAIL: $name (exit $got, wanted $want)"; cat "$work/log"; failures=$((failures + 1))
  else
    echo "ok:   $name"
  fi
}

expect "a complete application directory is packed" 0 "$(app_dir full)"
no_core="$(app_dir no-core)"; rm "$no_core/lib/app/resources/libskipicore.so"
expect "a directory without the Core library is refused" 1 "$no_core"
no_launcher="$(app_dir no-launcher)"; rm "$no_launcher/bin/SKIPI"
expect "a directory without the launcher is refused" 1 "$no_launcher"

exit $((failures > 0))
