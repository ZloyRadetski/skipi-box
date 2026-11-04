#!/bin/bash
# Checks the file listing of an installer (one path per line on stdin) for everything the installed
# application needs at run time.
#
#   verify-listing.sh <linux|windows> < listing
#
# Exit 0: complete; 1: something is missing (named on stderr); 2: usage error.
set -u

case "${1:-}" in
  linux)   required=(bin/SKIPI libskipicore.so libhev-socks5-tunnel.so geoip.dat geosite.dat) ;;
  windows) required=(SKIPI.exe skipicore.dll geoip.dat geosite.dat hev-socks5-tunnel.exe wintun.dll msys-2.0.dll msys-gcc_s-seh-1.dll) ;;
  *) echo "usage: verify-listing.sh <linux|windows> < listing" >&2; exit 2 ;;
esac

# Normalise separators and give every path a leading slash, so a requirement matches whole
# trailing path segments only (libskipicore.so must not be satisfied by libskipicore.so.bak).
listing="$(sed -e 's|[\]|/|g' -e 's#^\./#/#' -e 's#^\([^/]\)#/\1#')"

missing=0
for entry in "${required[@]}"; do
  pattern="/$(printf '%s' "$entry" | sed 's/[.]/\./g')\$"
  if ! printf '%s\n' "$listing" | grep -q -e "$pattern"; then
    echo "the package is missing $entry" >&2
    missing=1
  fi
done
exit "$missing"
