#!/usr/bin/env bash
# Sourced by build.sh. Sets VERSION_NAME and VERSION_CODE from app/AndroidManifest.xml, the only
# place a version is written:
#
#   release (RELEASE=1)  X.Y.Z          code = X*10^7 + Y*10^5 + Z*10^3 + 999
#   dev build            X.Y.Z-dev.N    code = X*10^7 + Y*10^5 + Z*10^3 + N
#
# N starts at 1 for the commit that changes versionName on first-parent history, then counts
# commits on that history from that point (each merge counts once).
# A pending version change also starts at 1. Tags do not affect the counter. A release outranks
# all dev builds with the same X.Y.Z; bump versionName before starting the next release's work.

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
  _n=1
  if git rev-parse --git-dir >/dev/null 2>&1 && git rev-parse HEAD >/dev/null 2>&1; then
    _head_version=$(git show HEAD:app/AndroidManifest.xml 2>/dev/null |
      sed -n 's/.*android:versionName="\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/p' | head -n1) || _head_version=
    if [ "$_head_version" = "$_manifest_version" ]; then
      for _version_commit in $(git log --first-parent -m --format=%H -G 'android:versionName=' -- app/AndroidManifest.xml); do
        _previous_version=$(git show "${_version_commit}^:app/AndroidManifest.xml" 2>/dev/null |
          sed -n 's/.*android:versionName="\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/p' | head -n1) || _previous_version=
        # Formatting or another attribute on this line must not reset the build number.
        if [ "$_previous_version" != "$_manifest_version" ]; then
          _n=$(git rev-list --first-parent --count "$_version_commit"..HEAD)
          _n=$((_n + 1))
          break
        fi
      done
    fi
  fi
  if [ "$_n" -gt 998 ]; then _n=998; fi
  VERSION_NAME="$_manifest_version-dev.$_n"
  VERSION_CODE=$((_base + _n))
fi
export VERSION_NAME VERSION_CODE
