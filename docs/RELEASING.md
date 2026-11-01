# Releasing LensPreset

This document covers release **1.1.0**: live-view ISO/EV adjustment, wheel selection of preset
options, and a card-based settings display. The release tag is `v1.1.0`.

Existing compatibility evidence is for the MVP on ILCE-5100 firmware 3.10 with a Sigma 30mm
F1.4 DC DN, tested by [@alfrededison](https://github.com/alfrededison). The trip presets and
preset-owned hints from 1.0.1, plus the new ISO/EV controls for 1.1.0, still need real-camera
testing. Do not describe the release as verified on all PMCA bodies.
The [README](../README.md) and [changelog](../CHANGELOG.md) state the tested scope and current limits.

## Keep one signing identity

Public dev builds and releases should use the same project keystore and key alias. Keep a secure
backup of the keystore and its credentials outside the repository. Configure all four repository
Actions secrets consumed by the build:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_B64` | Base64-encoded project keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

`dev-build` and tagged releases require all four secrets. `RELEASE=1` and
`REQUIRE_PROJECT_SIGNING=1` fail before building if any secret is absent. After signing, the build
verifies that the APK certificate matches `signing/release-cert.sha256`; the corresponding public
certificate is `signing/release-cert.pem`. A wrong key cannot silently become a published build.

Ordinary local builds and the `ci` workflow still use `out/debug.keystore` when no project key is
provided. CI's debug APK is for testing, not an upgrade-compatible replacement for a project-signed
APK. A fresh CI runner can generate a different debug key.

Local maintainer credentials are in the ignored `.signing/` directory: the encrypted PKCS12
keystore and `credentials.json` are owner-readable only. Back up both outside the checkout;
GitHub secrets cannot be downloaded later as a substitute for that backup. To build locally with
these credentials without putting passwords on the command line:

```bash
ANDROID_SDK=~/Library/Android/sdk python3 tools/build_signed.py          # signed dev APK
ANDROID_SDK=~/Library/Android/sdk python3 tools/build_signed.py --release # signed stable APK
```

The official key is different from the local debug key used during initial camera testing.
Those debug installations need one uninstall/reinstall to move to the project-signed builds;
preferences will be lost in that transition. Future project-signed dev and release builds share
the same certificate.
The local APK checksum and Actions artifacts use the APK's basename, so verify them from their
containing directory: `cd out && shasum -a 256 -c LensPreset.apk.sha256` for a local build.

Before publication, inspect the certificate SHA-256 digest of the actual APK using the Android
SDK's `apksigner` and compare it with the established signing identity. With the SDK build-tools
on `PATH`:

```bash
apksigner verify --min-sdk-version 10 --print-certs LensPreset-v1.1.0.apk
```

Different signing certificates prevent an in-place update. A transition from an earlier local
debug key requires an uninstall/reinstall, which removes the app's preferences; tell testers if
such a transition is necessary. Do not commit a keystore, encoded keystore, or credentials.

## Prepare the release commit

1. Make sure `app/AndroidManifest.xml` has the intended `versionName` (currently `1.1.0`). A
   release tag must be exactly `v` plus that version. A version bump starts its dev counter at
   `dev.1`; later first-parent commits increment it independently of tags, counting merges once.
   After publishing a release, bump
   the manifest to the next intended version before resuming dev builds.
   `versionName` is the only app version to edit; leave the source `versionCode` unchanged
   because `build.sh` derives it. Commit the version change before creating the release tag.
2. Commit the intended changes and confirm CI passes for that commit. CI runs core/adapter
   tests, profile schemas, committed firmware-report coverage, firmware-tool tests, and the APK
   build. For local commands, see [Development](DEVELOPMENT.md#tests).
3. Check that README compatibility claims, the camera profile's test provenance, and the
   changelog match the evidence. In the release commit, use the version and release date in the
   changelog heading (`1.1.0 — 2026-11-02`), and update README and this guide to that version.
   Remove stale `Unreleased`, development-target, and previous-latest wording from the release
   notes. Start a new `Unreleased` section when work begins on the following version.
4. Verify the signing secrets and certificate identity. Never treat a green build alone as
   evidence that the expected project key was used.

## Build the draft

Create the tag only after the version and release notes are committed and checks have passed:

```bash
git tag -a v1.1.0 -m "LensPreset 1.1.0"
git push origin main
git push origin v1.1.0
```

Pushing the intended `vX.Y.Z` tag triggers `.github/workflows/release.yml`. The workflow checks
the tag against the manifest, runs unit tests, builds a v1-signed APK with `RELEASE=1`, and creates
a **draft** release with these assets (for `v1.1.0`):

- `LensPreset-v1.1.0.apk`
- `LensPreset-v1.1.0.apk.sha256`

The workflow's draft is intentional; a successful job does not mean users can download it from a
public release. Download the draft assets as a maintainer, verify the checksum from the asset's
directory, and inspect the signing certificate:

```bash
shasum -a 256 -c LensPreset-v1.1.0.apk.sha256
apksigner verify --min-sdk-version 10 --print-certs LensPreset-v1.1.0.apk
```

Use the **actual draft APK** for a short test on the A5100, rather than assuming the local dev
APK is identical. Check live view, one immediate shot, a timed shot with half-press AF, preset
restoration, and exit/relaunch. If reopening is enabled, check both power-on reopening and an
explicit exit without an immediate reopen. Confirm Keep current look is available and report
its state when checking Creative Style / Soft Skin.

## Publish and verify access

Replace the generated commit summary with release notes based on the changelog. Include:

- The tested body, firmware, lens, and tester account.
- What works, the two settings and their defaults, and the known limits.
- Installation/update instructions, including any signing-key transition.
- Links to the camera test report form and profile/preset contribution instructions.

Publish the draft from GitHub Releases after its assets and body test have been checked. Verify
the release page and both download links without maintainer authentication. Confirm that the
version/date in the changelog matches publication; do not imply that an unpublished draft is a
public download.

## Where development APKs appear

- `ci` uploads a debug-signed Actions artifact named `LensPreset-apk` after its APK build succeeds.
- `dev-build` uploads the project-signed `LensPreset-dev-apk` Actions artifact and attaches a
  versioned APK and checksum to a `dev` prerelease. Check the actual release state; a draft cannot
  serve as a public download.
- The tag-triggered release workflow uploads the project-signed `LensPreset-release-apk` Actions
  artifact and creates a draft for a maintainer to publish.

For example, maintainer metadata can be inspected without exposing any signing credentials:

```bash
gh release view dev --json tagName,isDraft,isPrerelease,assets,url
gh release view v1.1.0 --json tagName,isDraft,isPrerelease,assets,url
```
