#!/bin/bash
# Builds a single-file AppImage from the jpackage application directory.
#
#   build-appimage.sh <app-image-dir> <output.AppImage>
#
# <app-image-dir> is desktopApp/build/compose/binaries/main/app/SKIPI (./gradlew :desktopApp:createDistributable).
# APPIMAGETOOL must point at an appimagetool executable that you obtained and trust; it is never
# downloaded here. The result needs glibc at least as new as the libraries inside (2.38 today), so
# build releases on the oldest system you want to support.
set -euo pipefail

app_dir="${1:?usage: build-appimage.sh <app-image-dir> <output.AppImage>}"
output="${2:?usage: build-appimage.sh <app-image-dir> <output.AppImage>}"
tool="${APPIMAGETOOL:?set APPIMAGETOOL to the appimagetool executable}"
here="$(cd "$(dirname "$0")" && pwd)"
icon="${SKIPI_ICON:-$here/../../../design/skipi_logo_default.svg}"

[ -x "$app_dir/bin/SKIPI" ] || { echo "not a SKIPI application directory: $app_dir" >&2; exit 1; }
[ -f "$app_dir/lib/app/resources/libskipicore.so" ] || { echo "Core library is missing from $app_dir" >&2; exit 1; }
[ -f "$app_dir/lib/app/resources/libhev-socks5-tunnel.so" ] || { echo "hev library is missing from $app_dir" >&2; exit 1; }
[ -x "$tool" ] || { echo "APPIMAGETOOL is not executable: $tool" >&2; exit 1; }

staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT
appdir="$staging/SKIPI.AppDir"
mkdir -p "$appdir"
cp -a "$app_dir" "$appdir/SKIPI"
install -m 0755 "$here/AppRun" "$appdir/AppRun"
install -m 0644 "$here/skipi.desktop" "$appdir/skipi.desktop"
install -m 0644 "$icon" "$appdir/skipi.svg"
ln -s skipi.svg "$appdir/.DirIcon"

ARCH="${ARCH:-x86_64}" "$tool" "$appdir" "$output"
echo "built $output"
