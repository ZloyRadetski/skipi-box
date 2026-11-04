#!/bin/bash
# Behavioural tests of packaging/verify-listing.sh: a package passes only when its file listing
# holds everything the installed application needs at run time.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
verify="$here/../verify-listing.sh"
failures=0

expect() { # expect <name> <expected-exit> <platform> <listing>
  local name="$1" want="$2" platform="$3" listing="$4" got
  printf '%s\n' "$listing" | "$verify" "$platform" >/dev/null 2>&1
  got=$?
  if [ "$got" -ne "$want" ]; then
    echo "FAIL: $name (exit $got, wanted $want)"; failures=$((failures + 1))
  else
    echo "ok:   $name"
  fi
}

linux_full='./opt/skipi/bin/SKIPI
./opt/skipi/lib/app/resources/libskipicore.so
./opt/skipi/lib/app/resources/libhev-socks5-tunnel.so
./opt/skipi/lib/app/resources/geoip.dat
./opt/skipi/lib/app/resources/geosite.dat'
windows_full='SKIPI/SKIPI.exe
SKIPI/app/resources/skipicore.dll
SKIPI/app/resources/geoip.dat
SKIPI/app/resources/geosite.dat
SKIPI/app/resources/hev/hev-socks5-tunnel.exe
SKIPI/app/resources/hev/wintun.dll
SKIPI/app/resources/hev/msys-2.0.dll
SKIPI/app/resources/hev/msys-gcc_s-seh-1.dll'

expect "linux package with everything passes" 0 linux "$linux_full"
expect "linux package without the Core library fails" 1 linux "$(echo "$linux_full" | grep -v libskipicore)"
expect "linux package without the launcher fails" 1 linux "$(echo "$linux_full" | grep -v bin/SKIPI)"
expect "linux package without geo data fails" 1 linux "$(echo "$linux_full" | grep -v geosite)"
expect "windows package with everything passes" 0 windows "$windows_full"
expect "windows package without wintun fails" 1 windows "$(echo "$windows_full" | grep -v wintun)"
expect "windows package without the gcc runtime fails" 1 windows "$(echo "$windows_full" | grep -v msys-gcc)"
expect "windows package without the executable fails" 1 windows "$(echo "$windows_full" | grep -v 'SKIPI.exe')"
expect "empty listing fails" 1 linux ""
expect "unknown platform is a usage error" 2 beos "$linux_full"
# A file that merely ends the same way is not the required file.
expect "similar name does not satisfy a requirement" 1 linux "$(echo "$linux_full" | sed 's#libskipicore.so#libskipicore.so.bak#')"

exit $((failures > 0))
