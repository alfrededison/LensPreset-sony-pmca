#!/usr/bin/env bash
# Shared signing checks. The caller supplies the certificate pin file and uses set -e.

require_project_signing() {
  local name expected
  for name in ANDROID_KEYSTORE_B64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
    if [ -z "${!name:-}" ]; then
      echo "Missing project signing secret: $name" >&2
      return 1
    fi
  done
  if [ ! -f "$1" ]; then
    echo "Missing project signing certificate pin: $1" >&2
    return 1
  fi
  expected=$(tr -d '\r\n' < "$1")
  if [[ ! "$expected" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Invalid project signing certificate pin: $1" >&2
    return 1
  fi
}

verify_project_certificate() {
  local expected actual certificates
  expected=$(tr -d '\r\n' < "$3")
  certificates=$("$2" verify --min-sdk-version 10 --print-certs "$1") || return 1
  actual=$(printf '%s\n' "$certificates" | awk '/^Signer #1 certificate SHA-256 digest: / {print $NF}')
  if [ "$actual" != "$expected" ]; then
    echo "APK signing certificate does not match the project certificate pin" >&2
    return 1
  fi
}
