#!/usr/bin/env bash
# Sourced by build.sh. Sets VERSION_NAME and VERSION_CODE from app/AndroidManifest.xml, the only
# place a version is written:
#
#   release (RELEASE=1)  X.Y.Z          code = X*10^7 + Y*10^5 + Z*10^3 + 999
#   dev build            X.Y.Z-dev.N    code = X*10^7 + Y*10^5 + Z*10^3 + N
#
# N counts commits since the last v* tag (all commits before the first tag), so a later dev build
# always installs over an earlier one, and the release outranks every dev build before it.

_manifest_version=$(sed -n 's/.*android:versionName="\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/p' app/AndroidManifest.xml | head -n1)
if [ -z "$_manifest_version" ]; then
  echo "app/AndroidManifest.xml: versionName must be X.Y.Z" >&2
  exit 1
fi
IFS=. read -r _major _minor _patch <<< "$_manifest_version"
_base=$((_major * 10000000 + _minor * 100000 + _patch * 1000))

if [ "${RELEASE:-0}" = "1" ]; then
  VERSION_NAME="$_manifest_version"
  VERSION_CODE=$((_base + 999))
else
  _n=0
  if git rev-parse --git-dir >/dev/null 2>&1 && git rev-parse HEAD >/dev/null 2>&1; then
    if _tag=$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null); then
      _n=$(git rev-list --count "$_tag"..HEAD)
    else
      _n=$(git rev-list --count HEAD)
    fi
  fi
  if [ "$_n" -gt 998 ]; then _n=998; fi
  VERSION_NAME="$_manifest_version-dev.$_n"
  VERSION_CODE=$((_base + _n))
fi
export VERSION_NAME VERSION_CODE
